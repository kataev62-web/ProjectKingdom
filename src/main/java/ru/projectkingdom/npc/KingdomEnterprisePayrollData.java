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
        if(worker==null || worker.entityId==null||worker.inventoryRecoveryRequired)return false;
        var enterprise=KingdomEnterpriseData.get(server);
        for(Employment e:new ArrayList<>(entries.values())){
            if(!e.worker().equals(worker.entityId))continue;
            long owed=accrue(server,e.enterprise(),e.worker());
            Employment accrued=contract(e.enterprise(),e.worker());
            if(owed<1||accrued==null)continue;
            var firm=enterprise.find(e.enterprise());
            if(firm==null || firm.cash()<=0)continue;
            long amount=Math.min(Math.min(10000L,owed),firm.cash());
            for(var level:server.getAllLevels()) {
                if(!worker.dimension.equals(level.dimension().location().toString()))continue;
                if(!enterprise.payVerifiedNpcWage(level,e.enterprise(),worker,amount))continue;
                Employment updated=new Employment(accrued.enterprise(),accrued.worker(),accrued.dailyCopper(),
                        accrued.lastDay(),accrued.owed()-amount,accrued.active());
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
    /** Result of an explicit manual payment; never a simulated or virtual wage. */
    public record ManualNpcPayment(long paid,String message) {}

    /**
     * The employer hands up to 64 real loose copper coins to the employee.
     *
     * <p>The coins are the real items of the inventory of the employer and the
     * real items of the saved wage purse of the resident: payment lowers the debt that
     * was already accrued. Without a debt there is no transfer, no wallet
     * conversion and no remint of any kind.
     *
     * <p>The whole transfer is checked before the first write and rolled back
     * when a debit does not land.
     */
    public ManualNpcPayment payNpcLooseCopper(ServerPlayer payer,KingdomNpcData.Worker worker){
        if(payer==null||worker==null||worker.entityId==null||worker.inventoryRecoveryRequired||worker.employer==null
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
        // The saved wage purse owns these physical stacks. The MCA bag is a separate
        // inventory: copying the proposed historical purse into it minted a second copy.
        var payment=ResidentPhysicalPurchase.wage(worker.wagePurse,inv,selected.owed(),
                stack -> stack.is(KingdomCurrency.COPPER.get()),KingdomCurrency::value);
        if(payment==null)
            return new ManualNpcPayment(0,"Нужны медные монеты и свободное место в кошельке NPC.");
        try {
            if(!payment.plan().commit())
                return new ManualNpcPayment(0,"Монеты не удалось списать. Выплата отменена.");
        } catch(RuntimeException uncertain) {
            worker.inventoryRecoveryRequired=true;
            KingdomNpcData.get(payer.getServer()).setDirty();
            return new ManualNpcPayment(0,"Выплата требует сверки физических монет. Повтор заблокирован.");
        }
        long paid=payment.paid();
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
