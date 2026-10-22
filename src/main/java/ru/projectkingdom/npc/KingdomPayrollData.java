package ru.projectkingdom.npc;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Payroll is a persistent liability, not an automatic transfer or currency mint. */
public final class KingdomPayrollData extends SavedData {
    private static final String KEY="kingdom_payroll_v1";
    // Preserve the public accessors relied on by the existing game menu.
    public record Contract(UUID town,UUID worker,long dailyCopper,long lastPaidDay,long owedCopper) {}
    private final Map<UUID,Contract> active=new LinkedHashMap<>();
    /** Closed contracts preserve earned salary after dismissal or change of town. */
    private final Map<String,Contract> arrears=new LinkedHashMap<>();
    private static String key(UUID town,UUID worker){return town+"|"+worker;}
    public static KingdomPayrollData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(KingdomPayrollData::load,KingdomPayrollData::new,KEY);
    }
    public Contract contract(UUID worker){return active.get(worker);}
    public Collection<Contract> all(){return Collections.unmodifiableCollection(active.values());}
    public Collection<Contract> unpaid(UUID worker){
        List<Contract> due=new ArrayList<>();
        for(Contract c:arrears.values())if(c.worker().equals(worker) && c.owedCopper()>0)due.add(c);
        Contract contract=active.get(worker);if(contract!=null && contract.owedCopper()>0)due.add(contract);
        return Collections.unmodifiableList(due);
    }
    public boolean hire(MinecraftServer server,UUID mayor,UUID worker,long rate){
        if(worker==null || mayor==null || worker.equals(mayor) || rate<1 || rate>10000)return false;
        var db=KingdomCivicsData.get(server);var town=db.townOf(mayor);
        if(town==null || !town.mayor().equals(mayor))return false;
        var employmentTown=db.townOf(worker);
        if(employmentTown==null || !employmentTown.id().equals(town.id()))return false;
        long day=server.overworld().getDayTime()/24000L;
        Contract previous=active.get(worker);
        if(previous!=null && !previous.town().equals(town.id()))return false;
        if(previous!=null)accrue(server,worker); // settle old rate before changing it
        previous=active.get(worker);
        // Reactivating a former contract merges older debt exactly once.
        Contract oldDebt=arrears.remove(key(town.id(),worker));
        long carry=previous==null?0:previous.owedCopper();
        if(oldDebt!=null){
            if(carry>Long.MAX_VALUE-oldDebt.owedCopper())carry=Long.MAX_VALUE;
            else carry+=oldDebt.owedCopper();
        }
        active.put(worker,new Contract(town.id(),worker,rate,day,carry));setDirty();return true;
    }
    public boolean dismiss(MinecraftServer server,UUID mayor,UUID worker){
        var town=KingdomCivicsData.get(server).townOf(mayor);
        Contract contract=active.get(worker);
        if(town==null || !town.mayor().equals(mayor) || contract==null || !contract.town().equals(town.id()))return false;
        accrue(server,worker);
        contract=active.remove(worker);
        if(contract.owedCopper()>0)mergeArrears(contract);
        setDirty();return true;
    }
    private void mergeArrears(Contract contract){
        String k=key(contract.town(),contract.worker());
        Contract old=arrears.get(k);
        long sum=contract.owedCopper();
        if(old!=null)sum=old.owedCopper()>Long.MAX_VALUE-sum?Long.MAX_VALUE:old.owedCopper()+sum;
        arrears.put(k,new Contract(contract.town(),contract.worker(),0,contract.lastPaidDay(),sum));
    }
    public long accrue(MinecraftServer server,UUID worker){
        Contract c=active.get(worker);
        if(c==null)return sumOwed(worker);
        long day=server.overworld().getDayTime()/24000L;
        long elapsed=Math.max(0,day-c.lastPaidDay());
        if(elapsed>0){
            long addition=elapsed>Long.MAX_VALUE/c.dailyCopper()?Long.MAX_VALUE:elapsed*c.dailyCopper();
            long owed=c.owedCopper()>Long.MAX_VALUE-addition?Long.MAX_VALUE:c.owedCopper()+addition;
            active.put(worker,new Contract(c.town(),worker,c.dailyCopper(),day,owed));setDirty();
        }
        return sumOwed(worker);
    }
    private long sumOwed(UUID worker){
        long sum=0;
        Contract current=active.get(worker);
        if(current!=null)sum=current.owedCopper();
        for(Contract c:arrears.values())if(c.worker().equals(worker))
            sum=sum>Long.MAX_VALUE-c.owedCopper()?Long.MAX_VALUE:sum+c.owedCopper();
        return sum;
    }
    /** Manual payment at a real bank counter, including old-city wages. */
    public boolean claim(MinecraftServer server,ServerPlayer worker){
        UUID id=worker.getUUID();accrue(server,id);
        if(!KingdomBankData.get(server).atBranch(worker))return false;
        ItemStack wallet=KingdomCurrency.wallet(worker);
        if(wallet.isEmpty())return false;
        List<Contract> queue=new ArrayList<>(unpaid(id));
        for(Contract c:queue){
            long amount=Math.min(10000L,c.owedCopper());
            if(amount<1 || KingdomCurrency.balance(wallet)>Long.MAX_VALUE-amount)continue;
            KingdomCivicsData civics=KingdomCivicsData.get(server);
            if(!civics.payTownObligation(c.town(),amount))continue;
            if(!KingdomCurrency.credit(wallet,amount)){
                if(!civics.refundTown(c.town(),amount))throw new IllegalStateException("City wage rollback failed");
                continue;
            }
            Contract updated=new Contract(c.town(),id,c.dailyCopper(),c.lastPaidDay(),c.owedCopper()-amount);
            Contract live=active.get(id);
            if(live!=null && live.town().equals(c.town()))active.put(id,updated);
            else if(updated.owedCopper()==0)arrears.remove(key(c.town(),id));
            else arrears.put(key(c.town(),id),updated);
            worker.getInventory().setChanged();setDirty();return true;
        }
        return false;
    }
    private static KingdomPayrollData load(CompoundTag root){
        KingdomPayrollData data=new KingdomPayrollData();
        for(Tag row:root.getList("Contracts",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)row;
            if(!t.hasUUID("Town") || !t.hasUUID("Worker"))continue;
            long rate=t.getLong("Rate");if(rate<1 || rate>10000)continue;
            Contract c=new Contract(t.getUUID("Town"),t.getUUID("Worker"),rate,Math.max(0,t.getLong("LastDay")),Math.max(0,t.getLong("Owed")));
            data.active.put(c.worker(),c);
        }
        for(Tag row:root.getList("Arrears",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)row;
            if(!t.hasUUID("Town") || !t.hasUUID("Worker"))continue;
            Contract c=new Contract(t.getUUID("Town"),t.getUUID("Worker"),0,Math.max(0,t.getLong("LastDay")),Math.max(0,t.getLong("Owed")));
            if(c.owedCopper()>0)data.arrears.put(key(c.town(),c.worker()),c);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root){
        ListTag a=new ListTag();for(Contract c:active.values())a.add(store(c));root.put("Contracts",a);
        a=new ListTag();for(Contract c:arrears.values())a.add(store(c));root.put("Arrears",a);return root;
    }
    private static CompoundTag store(Contract c){
        CompoundTag t=new CompoundTag();t.putUUID("Town",c.town());t.putUUID("Worker",c.worker());
        t.putLong("Rate",c.dailyCopper());t.putLong("LastDay",c.lastPaidDay());t.putLong("Owed",c.owedCopper());return t;
    }
}
