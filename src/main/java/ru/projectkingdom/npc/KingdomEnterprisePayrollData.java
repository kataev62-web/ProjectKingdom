package ru.projectkingdom.npc;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Wage liabilities per business, for players or NPC identities. No daily auto-payout. */
public final class KingdomEnterprisePayrollData extends SavedData {
    private static final String KEY="kingdom_enterprise_payroll_v1";
    /** After three unpaid game-days the worker refuses further unpaid shifts. */
    public static final int MAX_ARREARS_DAYS=3;
    public static long arrearsLimit(long dailyCopper){
        if(dailyCopper<1||dailyCopper>10000)throw new IllegalArgumentException("invalid wage");
        return dailyCopper*MAX_ARREARS_DAYS;
    }
    public record Employment(UUID enterprise,UUID worker,long dailyCopper,long lastDay,long owed,boolean active) {}
    private final Map<String,Employment> entries=new LinkedHashMap<>();
    private static String key(UUID firm,UUID worker){return firm+"|"+worker;}
    public static KingdomEnterprisePayrollData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(KingdomEnterprisePayrollData::load,KingdomEnterprisePayrollData::new,KEY);
    }
    public Collection<Employment> all(){return Collections.unmodifiableCollection(entries.values());}
    public Employment contract(UUID firm,UUID worker){return entries.get(key(firm,worker));}
    /** Owner may hire a real player UUID or a registered NPC UUID (NPC identity must be validated upstream). */
    public boolean hire(MinecraftServer server,UUID owner,UUID enterprise,UUID worker,long rate){
        var firm=KingdomEnterpriseData.get(server).find(enterprise);
        if(firm==null || !firm.owner().equals(owner) || worker==null || worker.equals(owner) || rate<1 || rate>10000)return false;
        long day=Math.max(0,server.overworld().getDayTime()/24000L);
        Employment old=entries.get(key(enterprise,worker));
        if(old!=null && old.active()){
            accrue(server,enterprise,worker);old=entries.get(key(enterprise,worker));
        }
        long owed=old==null?0:old.owed();
        // Never reset a suspended company's unpaid debt by clicking Hire again.
        if(old!=null&&!old.active()&&owed>0 || owed>=arrearsLimit(rate))return false;
        entries.put(key(enterprise,worker),new Employment(enterprise,worker,rate,day,owed,true));
        // The career of the resident keeps every step, also after a change of
        // the profession.
        var job=KingdomNpcData.get(server).workers.values().stream()
            .filter(w->worker.equals(w.entityId)).map(w->w.job).findFirst().orElse("free");
        KingdomResidentDevelopment.get(server).recordCareer(worker,day,job,enterprise);
        setDirty();return true;
    }
    public boolean dismiss(MinecraftServer server,UUID owner,UUID enterprise,UUID worker){
        var firm=KingdomEnterpriseData.get(server).find(enterprise);
        Employment c=entries.get(key(enterprise,worker));
        if(firm==null || !firm.owner().equals(owner) || c==null || !c.active())return false;
        accrue(server,enterprise,worker);c=entries.get(key(enterprise,worker));
        entries.put(key(enterprise,worker),new Employment(enterprise,worker,0,c.lastDay(),c.owed(),false));setDirty();return true;
    }
    public long accrue(MinecraftServer server,UUID enterprise,UUID worker){
        String key=key(enterprise,worker);Employment c=entries.get(key);
        if(c==null)return 0;
        if(!c.active())return c.owed();
        long day=Math.max(0,server.overworld().getDayTime()/24000L);
        long elapsed=Math.max(0,day-c.lastDay());
        if(elapsed==0)return c.owed();
        // Do not mint more debt while the employee is forbidden to work.
        // Backpay remains a legal obligation in SavedData after suspension.
        long cap=arrearsLimit(c.dailyCopper());
        long capacity=Math.max(0,cap-c.owed());
        long add=Math.min(capacity,elapsed>Long.MAX_VALUE/c.dailyCopper()
                ?Long.MAX_VALUE:elapsed*c.dailyCopper());
        long total=c.owed()+add;
        entries.put(key,new Employment(enterprise,worker,c.dailyCopper(),day,total,total<cap));
        setDirty();return total;
    }
    /** Stop wages after a registered NPC dies. Preserve accrued debts, never mint or erase cash. */
    public void retireDeceasedNpc(MinecraftServer server,UUID worker){
        if(server==null||worker==null)return;
        for(Employment e:new ArrayList<>(entries.values())){
            if(!e.worker().equals(worker)||!e.active())continue;
            accrue(server,e.enterprise(),worker);
            Employment latest=entries.get(key(e.enterprise(),worker));
            entries.put(key(e.enterprise(),worker),
                    new Employment(e.enterprise(),worker,0,latest.lastDay(),latest.owed(),false));
            setDirty();
        }
    }
    public long totalDue(MinecraftServer server,UUID worker){
        long sum=0;
        for(Employment c:new ArrayList<>(entries.values()))if(c.worker().equals(worker)){
            long due=accrue(server,c.enterprise(),worker);
            sum=sum>Long.MAX_VALUE-due?Long.MAX_VALUE:sum+due;
        }
        return sum;
    }
    /** Lightweight, deterministic NPC contract synchronization; no payout or complex AI.
     * Called periodically by the main server tick, not once per NPC per game tick.
     */
    public void syncNpc(MinecraftServer server,KingdomNpcData registry){
        var enterprises=KingdomEnterpriseData.get(server);
        for(Employment c:new ArrayList<>(entries.values())){
            if(!c.active())continue;
            KingdomNpcData.Worker known=null;
            for(var w:registry.all())if(w.entityId.equals(c.worker())){known=w;break;}
            if(known==null)continue; // offline identity may still owe money; never delete it
            var firm=enterprises.find(c.enterprise());
            if(firm==null || known.employer==null || !firm.owner().equals(known.employer)){
                if(firm!=null)dismiss(server,firm.owner(),firm.id(),known.entityId);
                continue;
            }
            if(known.dailyWage!=c.dailyCopper())hire(server,firm.owner(),firm.id(),known.entityId,known.dailyWage);
            else accrue(server,firm.id(),known.entityId);
            var latest=contract(firm.id(),known.entityId);
            if(latest!=null&&!latest.active()&&latest.owed()>0){
                known.contractPaused=true;
                known.unpaidDays=MAX_ARREARS_DAYS;
                // The existing job assignment is retained for eventual resumption;
                // workday executor checks contractPaused before every operation.
                KingdomNpcData.get(server).setDirty();
            }
        }
        for(var w:registry.all()){
            if(w.employer==null || w.ageYears<18 || w.contractPaused)continue;
            boolean hasContract=false;
            for(Employment c:entries.values())if(c.worker().equals(w.entityId) && c.active()){
                hasContract=true;break;
            }
            if(hasContract)continue;
            // Resume at the same workplace only after the full old debt was
            // paid; never silently move unpaid liabilities to another business.
            Employment last=null;
            for(Employment old:entries.values())if(old.worker().equals(w.entityId)
                    &&!old.active())last=old;
            if(last!=null && last.owed()>0)continue;
            var firm=last!=null?enterprises.find(last.enterprise()):enterprises.owned(w.employer);
            if(firm!=null && firm.owner().equals(w.employer))
                hire(server,w.employer,firm.id(),w.entityId,w.dailyWage);
        }
        // Worker must physically reach the cash desk. No teleport or money creation.
        for(var w:registry.all())claimNpc(server,w);
    }
    /** NPC obtains actual copper/silver/gold ItemStacks, not a virtual salary balance.
     * This fires only if the real NPC has physically reached the employer's bound chest.
     * No pathfinding or remote payout is performed by this accounting method.
     */
    public boolean claimNpc(MinecraftServer server,KingdomNpcData.Worker worker) {
        if(worker==null || worker.entityId==null)return false;
        var enterprise=KingdomEnterpriseData.get(server);
        for(Employment e:new ArrayList<>(entries.values())){
            if(!e.worker().equals(worker.entityId))continue;
            long owed=accrue(server,e.enterprise(),e.worker());
            if(owed<1)continue;
            var firm=enterprise.find(e.enterprise());
            if(firm==null || firm.cash()<=0)continue;
            long amount=Math.min(Math.min(10000L,owed),firm.cash());
            java.util.List<net.minecraft.world.item.ItemStack> proposed=coinsWithExisting(worker.wagePurse,amount);
            if(proposed==null)continue;
            for(var level:server.getAllLevels()) {
                if(!worker.dimension.equals(level.dimension().location().toString()))continue;
                if(!enterprise.debitVerifiedNpcWage(level,e.enterprise(),worker.entityId,amount))continue;
                worker.wagePurse.clear();worker.wagePurse.addAll(proposed);
                Employment updated=new Employment(e.enterprise(),e.worker(),e.dailyCopper(),e.lastDay(),e.owed()-amount,e.active());
                entries.put(key(e.enterprise(),e.worker()),updated);
                if(updated.owed()==0&&worker.contractPaused){
                    // Only real full repayment lifts the debt pause. The next sync
                    // may renew work if the employer still has a legitimate firm.
                    worker.contractPaused=false;
                    worker.unpaidDays=0;
                }
                KingdomNpcData.get(server).setDirty();setDirty();return true;
            }
        }
        return false;
    }
    private static java.util.List<net.minecraft.world.item.ItemStack> coinsWithExisting(
            java.util.List<net.minecraft.world.item.ItemStack> purse,long amount){
        java.util.List<net.minecraft.world.item.ItemStack> proposed=new java.util.ArrayList<>();
        for(var item:purse)if(!item.isEmpty()){
            if(KingdomCurrency.value(item)==0 || proposed.size()>=36)return null;
            proposed.add(item.copy());
        }
        long[] denominations={10000,100,1};
        net.minecraft.world.item.Item[] kinds={KingdomCurrency.GOLD.get(),KingdomCurrency.SILVER.get(),KingdomCurrency.COPPER.get()};
        for(int kind=0;kind<3;kind++){
            long quantity=amount/denominations[kind];amount%=denominations[kind];
            for(var stack:proposed)if(quantity>0 && stack.is(kinds[kind])){
                int space=Math.max(0,stack.getMaxStackSize()-stack.getCount());
                int add=(int)Math.min(space,quantity);if(add>0){stack.grow(add);quantity-=add;}
            }
            while(quantity>0){
                if(proposed.size()>=36)return null;
                int batch=(int)Math.min(quantity,64);
                proposed.add(new net.minecraft.world.item.ItemStack(kinds[kind],batch));quantity-=batch;
            }
        }
        return proposed;
    }
    /** Result of an explicit manual payment; never a simulated or virtual wage. */
    public record ManualNpcPayment(long paid,String message) {}

    /**
     * The employer hands up to 64 real loose copper coins to the employee.
     *
     * <p>The coins are the real items of the inventory of the employer and the
     * real items of the bag of the resident: the payment lowers the debt that
     * was already accrued. Without a debt there is no transfer, no wallet
     * conversion and no remint of any kind.
     *
     * <p>The whole transfer is checked before the first write and rolled back
     * when a debit does not land.
     */
    public ManualNpcPayment payNpcLooseCopper(ServerPlayer payer,KingdomNpcData.Worker worker){
        if(payer==null||worker==null||worker.entityId==null||worker.employer==null
                ||!payer.getUUID().equals(worker.employer))
            return new ManualNpcPayment(0,"Нет права выплачивать зарплату этому NPC.");
        var businesses=KingdomEnterpriseData.get(payer.getServer());
        Employment selected=null;
        for(Employment e:new ArrayList<>(entries.values())){
            if(!e.worker().equals(worker.entityId))continue;
            var firm=businesses.find(e.enterprise());
            if(firm==null||!payer.getUUID().equals(firm.owner()))continue;
            long due=accrue(payer.getServer(),e.enterprise(),e.worker());
            var current=contract(e.enterprise(),e.worker());
            if(current!=null&&due>0){selected=current;break;}
        }
        if(selected==null)
            return new ManualNpcPayment(0,"Начисленного долга пока нет. Зарплату начисляют за прошедшие игровые дни.");
        var inv=payer.getInventory();
        long remaining=Math.min(64L,selected.owed());
        java.util.List<net.minecraft.world.item.ItemStack> proposed=new ArrayList<>();
        for(var held:worker.wagePurse){
            if(held==null||held.isEmpty())continue;
            if(KingdomCurrency.value(held)<=0||proposed.size()>=36)
                return new ManualNpcPayment(0,"Кошелёк NPC повреждён или переполнен. Выплата отменена.");
            proposed.add(held.copy());
        }
        java.util.List<int[]> debits=new ArrayList<>();
        java.util.List<net.minecraft.world.item.ItemStack> before=new ArrayList<>();
        java.util.List<Integer> counts=new ArrayList<>();
        for(int slot=0;slot<inv.getContainerSize()&&remaining>0;slot++){
            var stack=inv.getItem(slot);
            if(stack.isEmpty()||!stack.is(KingdomCurrency.COPPER.get()))continue;
            int n=(int)Math.min(remaining,stack.getCount());
            var coins=stack.copy();coins.setCount(n);
            if(!appendPhysicalCopper(proposed,coins))
                return new ManualNpcPayment(0,"Не хватает свободных мест в кошельке NPC. Выплата не проведена.");
            debits.add(new int[]{slot});before.add(stack.copy());counts.add(n);
            remaining-=n;
        }
        long paid=Math.min(64L,selected.owed())-remaining;
        if(paid==0)
            return new ManualNpcPayment(0,"Нужны настоящие медные монеты Kingdom в инвентаре игрока (не внутри кошелька).");
        // Preflight all selected sources before the first destructive write.
        for(int i=0;i<debits.size();i++){
            var actual=inv.getItem(debits.get(i)[0]);
            if(!net.minecraft.world.item.ItemStack.matches(actual,before.get(i)))
                return new ManualNpcPayment(0,"Инвентарь изменился. Выплата отменена.");
        }
        int committed=0;
        for(int i=0;i<debits.size();i++){
            int slot=debits.get(i)[0];
            var next=before.get(i).copy();next.shrink(counts.get(i));
            inv.setItem(slot,next);
            if(!net.minecraft.world.item.ItemStack.matches(inv.getItem(slot),next)){
                for(int j=0;j<=committed;j++)
                    inv.setItem(debits.get(j)[0],before.get(j).copy());
                inv.setChanged();
                return new ManualNpcPayment(0,"Монеты не удалось списать. Выплата отменена.");
            }
            committed++;
        }
        worker.wagePurse.clear();worker.wagePurse.addAll(proposed);
        // The real bag of an MCA resident receives the same physical coins.
        for(net.minecraft.server.level.ServerLevel level:payer.server.getAllLevels()){
            if(!worker.dimension.equals(level.dimension().location().toString()))continue;
            var entity=level.getEntity(worker.entityId);
            if(!(entity instanceof net.minecraft.world.entity.npc.Villager resident))continue;
            if(!KingdomMcaAdapter.isResident(resident))break;
            var personal=KingdomResidentInventory.personal(resident);
            if(personal==null)break;
            for(var stack:proposed){
                var copy=stack.copy();
                if(!KingdomResidentInventory.insert(personal,copy))break;
            }
            personal.setChanged();
            break;
        }
        var updated=new Employment(selected.enterprise(),selected.worker(),selected.dailyCopper(),
                selected.lastDay(),selected.owed()-paid,selected.active());
        entries.put(key(selected.enterprise(),selected.worker()),updated);
        if(updated.owed()==0){worker.contractPaused=false;worker.unpaidDays=0;}
        inv.setChanged();
        KingdomNpcData.get(payer.getServer()).setDirty();
        setDirty();
        return new ManualNpcPayment(paid,"Выплачено "+paid+" медных настоящими монетами. Остаток долга: "
                +updated.owed()+".");
    }

    /** Appends the same physical coins with the same NBT, with a limit of 36 stacks. */
    private static boolean appendPhysicalCopper(
            java.util.List<net.minecraft.world.item.ItemStack> planned,
            net.minecraft.world.item.ItemStack incoming){
        var remaining=incoming.copy();
        for(var slot:planned){
            if(remaining.isEmpty())break;
            if(!net.minecraft.world.item.ItemStack.isSameItemSameTags(slot,remaining))continue;
            int space=Math.max(0,slot.getMaxStackSize()-slot.getCount());
            int add=Math.min(space,remaining.getCount());
            if(add>0){slot.grow(add);remaining.shrink(add);}
        }
        while(!remaining.isEmpty()){
            if(planned.size()>=36)return false;
            int taken=Math.min(remaining.getCount(),remaining.getMaxStackSize());
            var part=remaining.copy();part.setCount(taken);
            planned.add(part);
            remaining.shrink(taken);
        }
        return true;
    }

    /** A player physically visits the employer's registered cash desk and claims an earned wage. */
    public boolean claim(MinecraftServer server,ServerPlayer worker,UUID enterprise){
        long owed=accrue(server,enterprise,worker.getUUID());if(owed<=0)return false;
        long amount=Math.min(10000L,owed);
        if(!KingdomEnterpriseData.get(server).payVerifiedWage(worker,enterprise,amount))return false;
        String k=key(enterprise,worker.getUUID());Employment c=entries.get(k);
        entries.put(k,new Employment(enterprise,c.worker(),c.dailyCopper(),c.lastDay(),c.owed()-amount,c.active()));
        setDirty();return true;
    }
    public boolean claimAny(MinecraftServer server,ServerPlayer worker){
        for(Employment c:new ArrayList<>(entries.values()))if(c.worker().equals(worker.getUUID())
                && claim(server,worker,c.enterprise()))return true;
        return false;
    }
    private static KingdomEnterprisePayrollData load(CompoundTag root){
        KingdomEnterprisePayrollData data=new KingdomEnterprisePayrollData();
        for(Tag row:root.getList("Jobs",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)row;
            if(!t.hasUUID("Firm") || !t.hasUUID("Worker"))continue;
            long rate=Math.max(0,t.getLong("Rate"));boolean active=t.getBoolean("Active");
            if(rate>10000 || (active && rate==0))continue;
            Employment e=new Employment(t.getUUID("Firm"),t.getUUID("Worker"),rate,Math.max(0,t.getLong("Day")),Math.max(0,t.getLong("Owed")),active);
            data.entries.put(key(e.enterprise(),e.worker()),e);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root){
        ListTag a=new ListTag();for(Employment e:entries.values()){
            CompoundTag t=new CompoundTag();t.putUUID("Firm",e.enterprise());t.putUUID("Worker",e.worker());
            t.putLong("Rate",e.dailyCopper());t.putLong("Day",e.lastDay());t.putLong("Owed",e.owed());t.putBoolean("Active",e.active());a.add(t);
        }root.put("Jobs",a);return root;
    }
}
