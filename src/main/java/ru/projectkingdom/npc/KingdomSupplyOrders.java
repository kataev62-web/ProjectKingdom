package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent physical pickup/delivery orders. No virtual item generation. */
public final class KingdomSupplyOrders extends SavedData {
    private static final String KEY = "kingdom_supply_orders_v1";
    public enum Mode { SELF_PICKUP, COURIER, INTERNAL, MUNICIPAL }
    public enum State { OPEN, ASSIGNED, IN_TRANSIT, DELIVERED, CANCELLED }
    /** Unfinished inventory mutations are quarantined; NEVER replay them by creating items. */
    public enum TransferKind { PICKUP, DELIVERY }
    public static final class Order {
        public final UUID id, buyer, seller;
        public final String dimension, itemId;
        public final BlockPos source, destination;
        public final int count;
        public final long goodsPrice, deliveryFee;
        public final Mode mode;
        public State state = State.OPEN;
        public UUID courier;
        public int pickedUp, delivered;
        public TransferKind pendingKind;
        public int pendingQuantity;
        public UUID pendingCourier;
        /** Ledger counters captured before the inventory mutation. */
        public int pendingPickedBefore, pendingDeliveredBefore;
        public boolean goodsPaid, deliveryPaid;
        /** Cash held in custody until the courier pays the seller; not a bank transfer. */
        public long goodsEscrow, deliveryEscrow;
        /** Original lot metadata, including TFC freshness/quality. Null means untagged. */
        public CompoundTag itemTag;
        public boolean matchesLot(net.minecraft.world.item.ItemStack stack) {
            return !stack.isEmpty() && itemId.equals(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                    && Objects.equals(itemTag,stack.getTag());
        }
        private Order(UUID id, UUID buyer, UUID seller, String dimension, String itemId, BlockPos source, BlockPos destination, int count, long goodsPrice, long deliveryFee, Mode mode) {
            this.id=id; this.buyer=buyer; this.seller=seller; this.dimension=dimension; this.itemId=itemId;
            this.source=source; this.destination=destination; this.count=count; this.goodsPrice=goodsPrice; this.deliveryFee=deliveryFee; this.mode=mode;
        }
    }
    private final Map<UUID, Order> orders = new LinkedHashMap<>();
    public static KingdomSupplyOrders get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(KingdomSupplyOrders::load, KingdomSupplyOrders::new, KEY);
    }
    public Collection<Order> all() { return Collections.unmodifiableCollection(orders.values()); }
    public Order find(UUID id) { return orders.get(id); }
    /** The caller must validate ownership of both containers and their inventory before submission. */
    public Order create(UUID buyer, UUID seller, String dimension, String itemId, BlockPos source, BlockPos destination, int count, long goodsPrice, long deliveryFee, Mode mode) {
        if (buyer==null || seller==null || dimension==null || dimension.isBlank() || itemId==null || itemId.isBlank() || source==null || destination==null || source.equals(destination) || mode==null || mode==Mode.INTERNAL || mode==Mode.MUNICIPAL || count<1 || count>2304 || goodsPrice<0 || deliveryFee<0 || goodsPrice>1000000 || deliveryFee>1000000 || (!buyer.equals(seller) && goodsPrice==0)) return null;
        Order order=new Order(UUID.randomUUID(),buyer,seller,dimension,itemId,source.immutable(),destination.immutable(),count,goodsPrice,deliveryFee,mode);
        orders.put(order.id,order);setDirty();return order;
    }
    /**
     * Internal transfer, not a trade. Physical warehouse ownership and access
     * are verified by KingdomEnterpriseInternalSupply before issuance AND at
     * every courier step. Delivery has no fee and neither cashbox is touched.
     */
    Order createInternal(UUID enterprise, String dimension, String itemId,
            BlockPos source, BlockPos destination, int count) {
        return createInternal(enterprise,dimension,itemId,source,destination,count,null);
    }

    Order createInternal(UUID enterprise, String dimension, String itemId,
            BlockPos source, BlockPos destination, int count,
            net.minecraft.world.item.ItemStack sample) {
        if (sample != null && (sample.isEmpty()
                || !itemId.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(sample.getItem()).toString()))) return null;
        if (enterprise == null || dimension == null || dimension.isBlank()
                || itemId == null || itemId.isBlank() || source == null
                || destination == null || source.equals(destination)
                || count <= 0 || count > 16
                || hasActiveInternal(enterprise,dimension,source,destination,itemId)) return null;
        Order order = new Order(UUID.randomUUID(),enterprise,enterprise,dimension,
                itemId,source.immutable(),destination.immutable(),count,0,0,Mode.INTERNAL);
        order.deliveryPaid = true; // No transport charge, no money movement.
        order.itemTag = sample != null && sample.hasTag() ? sample.getTag().copy() : null;
        orders.put(order.id,order);setDirty();
        return order;
    }

    boolean hasActiveInternal(UUID enterprise,String dimension,
            BlockPos source,BlockPos destination,String itemId) {
        for (Order o:orders.values())
            if(o.mode==Mode.INTERNAL && o.buyer.equals(enterprise)
                    && o.dimension.equals(dimension) && o.source.equals(source)
                    && o.destination.equals(destination) && o.itemId.equals(itemId)
                    && o.state!=State.DELIVERED && o.state!=State.CANCELLED)
                return true;
        return false;
    }

    /** One pending shipment per destination/item, even across multiple source chests. */
    boolean hasActiveInternalTo(UUID enterprise,String dimension,BlockPos destination,String itemId) {
        for(Order o:orders.values())
            if(o.mode==Mode.INTERNAL && o.buyer.equals(enterprise)
                    && o.dimension.equals(dimension) && o.destination.equals(destination)
                    && o.itemId.equals(itemId)
                    && o.state!=State.DELIVERED && o.state!=State.CANCELLED)
                return true;
        return false;
    }

    /** Only rollback of a new, never-dispatched, zero-value internal order. */
    boolean cancelInternalUnassigned(UUID id) {
        Order o=orders.get(id);
        if(o==null || o.mode!=Mode.INTERNAL || o.state!=State.OPEN
                || o.courier!=null || o.pickedUp!=0 || o.delivered!=0) return false;
        o.state=State.CANCELLED;setDirty();return true;
    }

    /**
     * A specific TFC lot may spoil or be removed before pickup. Releasing an
     * untouched INTERNAL order is safe; after any physical pickup it is not.
     */
    boolean cancelInternalUntouched(UUID id, UUID carrier) {
        Order o=orders.get(id);
        if(o==null || o.mode!=Mode.INTERNAL || o.pendingKind!=null
                || o.pickedUp!=0 || o.delivered!=0 || o.goodsEscrow!=0
                || o.deliveryEscrow!=0 || o.state==State.CANCELLED
                || o.state==State.DELIVERED
                || (o.state==State.ASSIGNED && !Objects.equals(o.courier,carrier))
                || (o.state!=State.OPEN && o.state!=State.ASSIGNED))return false;
        o.state=State.CANCELLED;setDirty();return true;
    }

    /** Only the reviewed city purchase coordinator can create a municipal order. */
    Order createMunicipal(UUID town,UUID seller,String dimension,String itemId,
            BlockPos source,BlockPos destination,int count,long goodsPrice,
            net.minecraft.world.item.ItemStack lot) {
        if(town==null || seller==null || town.equals(seller)||dimension==null
                ||dimension.isBlank()||itemId==null||itemId.isBlank()
                ||source==null||destination==null||source.equals(destination)
                ||count<1||count>16||goodsPrice<1||goodsPrice>1_000_000
                ||lot==null||lot.isEmpty()||!itemId.equals(net.minecraft.core.registries.BuiltInRegistries
                    .ITEM.getKey(lot.getItem()).toString()))return null;
        var order=new Order(UUID.randomUUID(),town,seller,dimension,itemId,
                source.immutable(),destination.immutable(),count,goodsPrice,0,Mode.MUNICIPAL);
        order.itemTag=lot.hasTag()?lot.getTag().copy():null;
        orders.put(order.id,order);setDirty();return order;
    }

    /** Reserve prepaid physical cash for a new order. Called only after a successful wallet debit. */
    public boolean escrow(UUID id, long goods, long delivery) {
        Order o=orders.get(id);
        if(o==null || o.state!=State.OPEN || o.goodsEscrow!=0 || o.deliveryEscrow!=0
                || goods!=o.goodsPrice || delivery!=o.deliveryFee)return false;
        o.goodsEscrow=goods;o.deliveryEscrow=delivery;
        o.deliveryPaid=delivery>0 || o.mode==Mode.MUNICIPAL;
        // goodsPaid deliberately remains false until seller actually receives cash.
        setDirty();return true;
    }
    /** A quote's outstanding stock is reserved across all open orders for the same chest. */
    public int reserved(String dimension,BlockPos source,String itemId) {
        int total=0;
        for(Order o:orders.values())if(o.dimension.equals(dimension)&&o.source.equals(source)
                && o.itemId.equals(itemId)&&o.state!=State.CANCELLED&&o.state!=State.DELIVERED)
            total+=o.count-o.pickedUp;
        return total;
    }
    public boolean assign(UUID id, UUID carrier) {
        Order o=orders.get(id);
        if(o==null || carrier==null || o.state!=State.OPEN) return false;
        o.courier=carrier;o.state=State.ASSIGNED;setDirty();return true;
    }
    public boolean cancel(UUID id, UUID actor) {
        Order o=orders.get(id);
        if(o==null || !o.buyer.equals(actor) || (o.state!=State.OPEN && o.state!=State.ASSIGNED) || o.pickedUp!=0 || o.goodsPaid || o.goodsEscrow!=0 || o.deliveryEscrow!=0)return false;
        o.state=State.CANCELLED;setDirty();return true;
    }
    /** Release prepaid cash to a seller who physically visits their selling chest.
     * This is an escrow handover, NOT a remote bank transfer. The same cash cannot be claimed twice.
     */
    public boolean collectGoodsCash(UUID id, net.minecraft.server.level.ServerPlayer seller) {
        Order o=orders.get(id);
        if(o==null || !o.seller.equals(seller.getUUID()) || o.state==State.CANCELLED || o.state==State.DELIVERED
                || o.goodsPaid || o.goodsEscrow<=0 || o.pickedUp!=0 || o.courier!=null)return false;
        if(!KingdomCurrency.credit(seller,o.goodsEscrow))return false;
        o.goodsEscrow=0;o.goodsPaid=true;setDirty();return true;
    }
    /** Refund only an untouched order, atomically with the receiving physical wallet. */
    public boolean refund(UUID id, net.minecraft.server.level.ServerPlayer buyer) {
        Order o=orders.get(id);
        if(o==null || !o.buyer.equals(buyer.getUUID()) || o.state!=State.OPEN || o.courier!=null
                || o.pickedUp!=0 || o.goodsPaid || o.goodsEscrow<0 || o.deliveryEscrow<0)return false;
        long sum=o.goodsEscrow+o.deliveryEscrow;
        if(sum<=0 || sum>1_000_000 || !KingdomCurrency.credit(buyer,sum))return false;
        o.goodsEscrow=0;o.deliveryEscrow=0;o.deliveryPaid=false;
        o.state=State.CANCELLED;setDirty();return true;
    }
    /** Transport payment is released only after the full order has been delivered. */
    public boolean collectDeliveryCash(UUID id, net.minecraft.server.level.ServerPlayer carrier) {
        Order o=orders.get(id);
        if(o==null || !Objects.equals(o.courier,carrier.getUUID()) || o.state!=State.DELIVERED || o.deliveryEscrow<=0)return false;
        if(!KingdomCurrency.credit(carrier,o.deliveryEscrow))return false;
        o.deliveryEscrow=0;setDirty();return true;
    }
    /** Transfer prepaid physical value to the seller's real cashbox at pickup.
     * No goods can leave the seller before this succeeds. Cannot be paid twice.
     */
    public boolean paySellerAtPickup(UUID id, UUID carrier, net.minecraft.world.Container cashbox) {
        Order o=orders.get(id);
        if(o==null || !Objects.equals(o.courier,carrier) || (o.mode!=Mode.COURIER && o.mode!=Mode.MUNICIPAL)
                || (o.state!=State.ASSIGNED && o.state!=State.IN_TRANSIT)
                || o.pickedUp!=0 || o.goodsPaid || o.goodsEscrow!=o.goodsPrice || o.goodsEscrow<=0)
            return false;
        if(!KingdomCourierCashbox.deposit(cashbox,o.goodsEscrow))return false;
        o.goodsEscrow=0;
        o.goodsPaid=true;
        setDirty();
        return true;
    }
    /** Payment is performed by the caller with actual cash at the seller's location. */
    public boolean markPaid(UUID id, boolean goods, boolean delivery) {
        Order o=orders.get(id);
        if(o==null || o.state==State.CANCELLED || o.state==State.DELIVERED || (!goods && !delivery))return false;
        if(goods)o.goodsPaid=true;
        if(delivery)o.deliveryPaid=true;
        setDirty();return true;
    }
    /** Record the intended inventory operation before changing a physical container.
     * If the server stops before finishTransfer, a human must reconcile physical stock.
     * This is a recovery guard, not a claim of cross-SavedData crash atomicity.
     */
    public boolean beginTransfer(UUID id, UUID courier, TransferKind kind, int quantity) {
        Order o=orders.get(id);
        if(o==null || courier==null || kind==null || quantity<=0 || o.pendingKind!=null
                || !courier.equals(o.courier) || (o.state!=State.ASSIGNED && o.state!=State.IN_TRANSIT))return false;
        if(kind==TransferKind.PICKUP && (quantity>o.count-o.pickedUp || !o.deliveryPaid
                || (!o.buyer.equals(o.seller) && !o.goodsPaid)))return false;
        if(kind==TransferKind.DELIVERY && (o.state!=State.IN_TRANSIT || quantity>o.pickedUp-o.delivered))return false;
        o.pendingKind=kind;o.pendingQuantity=quantity;o.pendingCourier=courier;
        o.pendingPickedBefore=o.pickedUp;o.pendingDeliveredBefore=o.delivered;setDirty();return true;
    }
    /** Complete the journal only after the inventory operation and ledger update succeed. */
    public boolean finishTransfer(UUID id, UUID courier, TransferKind kind, int quantity) {
        Order o=orders.get(id);
        if(o==null || o.pendingKind!=kind || o.pendingQuantity!=quantity
                || !Objects.equals(o.pendingCourier,courier))return false;
        clearPending(o);setDirty();return true;
    }
    /** Safe only if no physical inventory mutation occurred. */
    public boolean abortTransfer(UUID id, UUID courier, TransferKind kind, int quantity) {
        return finishTransfer(id,courier,kind,quantity);
    }
    private static void clearPending(Order o) {
        o.pendingKind=null;o.pendingQuantity=0;o.pendingCourier=null;
        o.pendingPickedBefore=0;o.pendingDeliveredBefore=0;
    }
    /** Administrative ledger repair ONLY. The operator must first inspect actual
     * seller/buyer chests, NPC inventory and cart contents. This method never
     * moves or creates items. A delta of zero means the physical transfer did
     * not happen; a full delta means it did. Partial deltas are deliberately
     * unsupported and must be corrected physically before reconciliation.
     * Existing counters are checked to avoid crediting a completed transfer twice.
     */
    public boolean reconcile(UUID id, TransferKind expectedKind, int physicalDelta) {
        Order o=orders.get(id);
        if(o==null || o.pendingKind!=expectedKind || o.pendingCourier==null
                || o.pendingQuantity<=0 || (physicalDelta!=0 && physicalDelta!=o.pendingQuantity))return false;
        // Old journals (written before the baseline fields existed) cannot be
        // safely reconciled automatically: require manual migration instead.
        if(o.pendingPickedBefore<0 || o.pendingDeliveredBefore<0)return false;
        int picked=o.pendingPickedBefore, delivered=o.pendingDeliveredBefore;
        if(expectedKind==TransferKind.PICKUP) {
            if(o.delivered!=delivered || (o.pickedUp!=picked && o.pickedUp!=picked+o.pendingQuantity))return false;
            if(o.pickedUp==picked+o.pendingQuantity && physicalDelta==0)return false;
            if(o.pickedUp==picked && physicalDelta>0) {
                if(o.state!=State.ASSIGNED && o.state!=State.IN_TRANSIT)return false;
                o.pickedUp+=physicalDelta;o.state=State.IN_TRANSIT;
            }
        } else {
            if(o.pickedUp!=picked || (o.delivered!=delivered && o.delivered!=delivered+o.pendingQuantity))return false;
            if(o.delivered==delivered+o.pendingQuantity && physicalDelta==0)return false;
            if(o.delivered==delivered && physicalDelta>0) {
                if(o.state!=State.IN_TRANSIT || o.delivered+physicalDelta>o.pickedUp)return false;
                o.delivered+=physicalDelta;
                if(o.delivered==o.count)o.state=State.DELIVERED;
            }
        }
        clearPending(o);setDirty();return true;
    }
    public boolean needsReconciliation(UUID id) {
        Order o=orders.get(id);return o!=null && o.pendingKind!=null;
    }
    /** Never remove cargo until a real container extraction has succeeded. */
    public boolean recordPickup(UUID id, UUID carrier, int quantity) {
        Order o=orders.get(id);
        if(o==null || !Objects.equals(o.courier,carrier) || (o.state!=State.ASSIGNED && o.state!=State.IN_TRANSIT) || quantity<=0 || quantity>o.count-o.pickedUp || (!o.buyer.equals(o.seller) && !o.goodsPaid) || (o.mode==Mode.COURIER && !o.deliveryPaid))return false;
        if(o.pendingKind!=TransferKind.PICKUP || o.pendingQuantity!=quantity || !Objects.equals(o.pendingCourier,carrier))return false;
        o.pickedUp+=quantity;o.state=State.IN_TRANSIT;setDirty();return true;
    }
    public boolean recordDelivery(UUID id, UUID carrier, int quantity) {
        Order o=orders.get(id);
        if(o==null || !Objects.equals(o.courier,carrier) || o.state!=State.IN_TRANSIT || quantity<=0 || quantity>o.pickedUp-o.delivered)return false;
        if(o.pendingKind!=TransferKind.DELIVERY || o.pendingQuantity!=quantity || !Objects.equals(o.pendingCourier,carrier))return false;
        o.delivered+=quantity;
        if(o.delivered==o.count)o.state=State.DELIVERED;
        setDirty();return true;
    }
    private static KingdomSupplyOrders load(CompoundTag root) {
        KingdomSupplyOrders d=new KingdomSupplyOrders();ListTag list=root.getList("Orders",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++) {
            CompoundTag t=list.getCompound(i);
            if(!t.hasUUID("Id") || !t.hasUUID("Buyer") || !t.hasUUID("Seller"))continue;
            try {
                Order o=new Order(t.getUUID("Id"),t.getUUID("Buyer"),t.getUUID("Seller"),t.getString("Dimension"),t.getString("Item"),new BlockPos(t.getInt("SX"),t.getInt("SY"),t.getInt("SZ")),new BlockPos(t.getInt("DX"),t.getInt("DY"),t.getInt("DZ")),t.getInt("Count"),t.getLong("GoodsPrice"),t.getLong("DeliveryFee"),Mode.valueOf(t.getString("Mode")));
                if(o.count<1 || o.count>2304 || o.goodsPrice<0 || o.deliveryFee<0)continue;
                o.state=State.valueOf(t.getString("State"));if(t.hasUUID("Courier"))o.courier=t.getUUID("Courier");
                o.pickedUp=Math.max(0,Math.min(o.count,t.getInt("Picked")));o.delivered=Math.max(0,Math.min(o.pickedUp,t.getInt("Delivered")));
                o.goodsPaid=t.getBoolean("GoodsPaid");o.deliveryPaid=t.getBoolean("DeliveryPaid");
                o.goodsEscrow=t.getLong("GoodsEscrow");o.deliveryEscrow=t.getLong("DeliveryEscrow");
                if(o.mode==Mode.INTERNAL && t.contains("ItemTag",Tag.TAG_COMPOUND))
                    o.itemTag=t.getCompound("ItemTag").copy();
                if(t.contains("PendingKind",Tag.TAG_STRING)) {
                    o.pendingKind=TransferKind.valueOf(t.getString("PendingKind"));
                    o.pendingQuantity=t.getInt("PendingQuantity");
                    o.pendingPickedBefore=t.contains("PendingPickedBefore",Tag.TAG_INT)?t.getInt("PendingPickedBefore"):-1;
                    o.pendingDeliveredBefore=t.contains("PendingDeliveredBefore",Tag.TAG_INT)?t.getInt("PendingDeliveredBefore"):-1;
                    if(t.hasUUID("PendingCourier"))o.pendingCourier=t.getUUID("PendingCourier");
                }
                d.orders.put(o.id,o);
            }catch(IllegalArgumentException ignored) { /* invalid saved enum */ }
        }return d;
    }
    @Override public CompoundTag save(CompoundTag root) {
        ListTag list=new ListTag();for(Order o:orders.values()) {
            CompoundTag t=new CompoundTag();t.putUUID("Id",o.id);t.putUUID("Buyer",o.buyer);t.putUUID("Seller",o.seller);
            t.putString("Dimension",o.dimension);t.putString("Item",o.itemId);t.putString("Mode",o.mode.name());t.putString("State",o.state.name());
            t.putInt("SX",o.source.getX());t.putInt("SY",o.source.getY());t.putInt("SZ",o.source.getZ());
            t.putInt("DX",o.destination.getX());t.putInt("DY",o.destination.getY());t.putInt("DZ",o.destination.getZ());
            t.putInt("Count",o.count);t.putLong("GoodsPrice",o.goodsPrice);t.putLong("DeliveryFee",o.deliveryFee);
            if(o.courier!=null)t.putUUID("Courier",o.courier);t.putInt("Picked",o.pickedUp);t.putInt("Delivered",o.delivered);
            t.putBoolean("GoodsPaid",o.goodsPaid);t.putBoolean("DeliveryPaid",o.deliveryPaid);
            t.putLong("GoodsEscrow",o.goodsEscrow);t.putLong("DeliveryEscrow",o.deliveryEscrow);
            if(o.mode==Mode.INTERNAL && o.itemTag!=null)t.put("ItemTag",o.itemTag.copy());
            if(o.pendingKind!=null){t.putString("PendingKind",o.pendingKind.name());
                t.putInt("PendingQuantity",o.pendingQuantity);
                t.putInt("PendingPickedBefore",o.pendingPickedBefore);
                t.putInt("PendingDeliveredBefore",o.pendingDeliveredBefore);
                if(o.pendingCourier!=null)t.putUUID("PendingCourier",o.pendingCourier);}
            list.add(t);
        }root.put("Orders",list);return root;
    }
}
