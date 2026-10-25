package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Write-ahead receipt for municipal purchases crossing the real bank vault,
 * civic treasury ledger and the existing durable delivery order.
 * If the server stops between these steps, freeze the request; NEVER replay.
 */
public final class KingdomMunicipalProcurementData extends SavedData {
    private static final String KEY="kingdom_municipal_procurement_v1";
    public enum Phase { PREPARED, CASH_REMOVED, TREASURY_DEBITED, ORDERED, NEEDS_REVIEW }
    public record Receipt(UUID id,UUID town,UUID actor,String item,int amount,long copper,
                          Phase phase,UUID deliveryId) {
        public Receipt {
            Objects.requireNonNull(id);Objects.requireNonNull(town);
            Objects.requireNonNull(actor);Objects.requireNonNull(phase);
            if(item==null||item.isBlank()||amount<1||amount>16||copper<1||copper>1_000_000)
                throw new IllegalArgumentException("receipt");
        }
        Receipt with(Phase next,UUID order){return new Receipt(id,town,actor,item,amount,copper,next,order);}
    }
    private final Map<UUID,Receipt> receipts=new LinkedHashMap<>();
    public static KingdomMunicipalProcurementData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(
                KingdomMunicipalProcurementData::load,KingdomMunicipalProcurementData::new,KEY);
    }
    public Collection<Receipt> all(){return Collections.unmodifiableCollection(receipts.values());}
    public Receipt get(UUID id){return receipts.get(id);}
    /** A courier must never release municipal escrow without the finished journal. */
    public boolean authorizedDelivery(KingdomSupplyOrders.Order order) {
        if(order==null || order.mode!=KingdomSupplyOrders.Mode.MUNICIPAL)return false;
        for(var r:receipts.values())
            if(r.phase()==Phase.ORDERED && order.id.equals(r.deliveryId())
                    && order.buyer.equals(r.town()) && order.itemId.equals(r.item())
                    && order.count==r.amount() && order.goodsPrice==r.copper())
                return true;
        return false;
    }
    public boolean active(UUID town,String item,KingdomSupplyOrders orders){
        for(var r:receipts.values()){
            if(!r.town().equals(town)||!r.item().equals(item))continue;
            if(r.phase()!=Phase.ORDERED)return true;
            var o=orders.find(r.deliveryId());
            if(o==null || (o.state!=KingdomSupplyOrders.State.DELIVERED
                    && o.state!=KingdomSupplyOrders.State.CANCELLED))return true;
            // Cancelled after escrow is a special case: require manual review
            // rather than silently spending the treasury twice.
            if(o.state==KingdomSupplyOrders.State.CANCELLED && o.goodsEscrow>0)return true;
        }
        return false;
    }
    Receipt prepare(UUID town,UUID actor,String item,int amount,long copper,
                    KingdomSupplyOrders orders){
        if(active(town,item,orders))return null;
        var r=new Receipt(UUID.randomUUID(),town,actor,item,amount,copper,Phase.PREPARED,null);
        receipts.put(r.id(),r);setDirty();return r;
    }
    boolean advance(UUID id,Phase expected,Phase next,UUID delivery){
        Receipt old=receipts.get(id);
        if(old==null||old.phase()!=expected)return false;
        receipts.put(id,old.with(next,delivery));setDirty();return true;
    }
    boolean cancelUnused(UUID id) {
        var r=receipts.get(id);
        if(r==null||r.phase()!=Phase.PREPARED)return false;
        receipts.remove(id);setDirty();return true;
    }
    static KingdomMunicipalProcurementData load(CompoundTag root){
        var data=new KingdomMunicipalProcurementData();
        for(Tag raw:root.getList("Receipts",Tag.TAG_COMPOUND)){
            var t=(CompoundTag)raw;
            if(!t.hasUUID("Id")||!t.hasUUID("Town")||!t.hasUUID("Actor"))continue;
            try{
                var r=new Receipt(t.getUUID("Id"),t.getUUID("Town"),t.getUUID("Actor"),
                        t.getString("Item"),t.getInt("Amount"),t.getLong("Copper"),
                        Phase.valueOf(t.getString("Phase")),
                        t.hasUUID("Delivery")?t.getUUID("Delivery"):null);
                data.receipts.put(r.id(),r);
            }catch(IllegalArgumentException ignored){}
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root){
        var list=new ListTag();
        for(var r:receipts.values()){
            var t=new CompoundTag();
            t.putUUID("Id",r.id());t.putUUID("Town",r.town());
            t.putUUID("Actor",r.actor());t.putString("Item",r.item());
            t.putInt("Amount",r.amount());t.putLong("Copper",r.copper());
            t.putString("Phase",r.phase().name());
            if(r.deliveryId()!=null)t.putUUID("Delivery",r.deliveryId());
            list.add(t);
        }
        root.put("Receipts",list);return root;
    }
}
