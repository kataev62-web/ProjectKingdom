package ru.projectkingdom.npc;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Stage 2: physical RAW warehouse -> NPC courier -> worker RAW chest, without
 * an internal sale. Uses existing enterprise storage and delivery journal.
 * Source may be the registered central RAW chest or another worker's RAW chest.
 * Tagged TFC stacks are transported as exact, individually recorded lots.
 */
public final class KingdomEnterpriseInternalSupply {
    private KingdomEnterpriseInternalSupply() {}
    private static final int MAX_REQUEST = 16;

    /** A persisted request is checked again before dispatch and every courier step. */
    public static boolean authorized(ServerLevel level,KingdomSupplyOrders.Order order) {
        if(order==null || order.mode!=KingdomSupplyOrders.Mode.INTERNAL
                || !order.buyer.equals(order.seller)
                || !order.dimension.equals(level.dimension().location().toString())
                || order.source.equals(order.destination)
                || !level.hasChunkAt(order.source) || !level.hasChunkAt(order.destination))
            return false;
        var companies=KingdomEnterpriseData.get(level.getServer());
        var firm=companies.find(order.buyer);
        if(firm==null || companies.storageClaimedByOther(order.buyer,order.dimension,order.destination)
                || !KingdomTerritories.get(level.getServer()).usable(
                    level,order.destination,firm.owner(),"raw"))
            return false;
        var source=KingdomEnterpriseRawStores.find(level,order.buyer,order.source);
        if(source==null || !(level.getBlockEntity(order.destination) instanceof Container))return false;
        for(var worker:KingdomNpcData.get(level.getServer()).all()) {
            if(!firm.owner().equals(worker.employer) || !worker.hasSource || !worker.hasWorkstation
                    || worker.contractPaused || !order.dimension.equals(worker.dimension)
                    || !new BlockPos(worker.sourceX,worker.sourceY,worker.sourceZ).equals(order.destination))
                continue;
            var plan=KingdomNpcWorkshopData.get(level.getServer()).plan(worker.entityId);
            if(plan!=null && plan.enterprise().equals(order.buyer)
                    && new BlockPos(worker.workX,worker.workY,worker.workZ)
                        .distSqr(order.destination)<=36)return true;
        }
        return false;
    }

    /** Avoid duplicate requests and do not buy goods already owned by the company. */
    public static boolean request(ServerLevel level,KingdomNpcData.Worker worker,
            KingdomNpcWorkshopData.Plan plan,Container target) {
        Objects.requireNonNull(level);
        if(worker==null || plan==null || target==null || worker.employer==null
                || !worker.hasSource || !worker.dimension.equals(
                    level.dimension().location().toString()) || target.getContainerSize()<=9)
            return false;
        var firms=KingdomEnterpriseData.get(level.getServer());
        var firm=firms.find(plan.enterprise());
        BlockPos destination=new BlockPos(worker.sourceX,worker.sourceY,worker.sourceZ);
        if(firm==null || !firm.owner().equals(worker.employer)
                || !level.hasChunkAt(destination) || level.getBlockEntity(destination)!=target)
            return false;
        var ledger=KingdomSupplyOrders.get(level.getServer());
        Set<String> examined=new HashSet<>();
        for(String itemId:plan.grid()) {
            if(itemId.isBlank() || !examined.add(itemId))continue;
            int units=0;for(String ingredient:plan.grid())if(itemId.equals(ingredient))units++;
            int local=countId(target,itemId,0);
            if(local>=units)continue;
            if(ledger.hasActiveInternalTo(plan.enterprise(),worker.dimension,destination,itemId))
                return true;
            var global=KingdomWarehouseStockCoordinator.raw(
                    level,plan.enterprise(),itemId,destination);
            if(!global.complete())return false;
            int desired=Math.max(units,4);
            if(global.physical()<=local)continue;
            for(var source:KingdomEnterpriseRawStores.loaded(level,plan.enterprise())) {
                if(source.pos().equals(destination))continue;
                Container inventory=source.inventory();
                for(int slot=source.firstFreeSlot();slot<inventory.getContainerSize();slot++) {
                    ItemStack lot=inventory.getItem(slot);
                    if(lot.isEmpty() || !itemId.equals(
                            BuiltInRegistries.ITEM.getKey(lot.getItem()).toString())
                            || !inventory.canTakeItem(inventory,slot,lot)
                            || KingdomCurrency.value(lot)>0 || lot.is(KingdomCurrency.WALLET.get()))
                        continue;
                    int reserved=ledger.reserved(worker.dimension,source.pos(),itemId);
                    int canMove=Math.min(MAX_REQUEST,Math.min(desired-local,
                            Math.max(0,lot.getCount()-reserved)));
                    canMove=Math.min(canMove,capacity(target,lot,9));
                    if(canMove<=0)continue;
                    var order=ledger.createInternal(plan.enterprise(),worker.dimension,itemId,
                            source.pos(),destination,canMove,lot);
                    if(order!=null && authorized(level,order))return true;
                    if(order!=null)ledger.cancelInternalUnassigned(order.id);
                }
            }
        }
        return false;
    }

    /** All item variants count toward recipe demand; NBT is preserved during transport. */
    static int countId(Container c,String itemId,int fromSlot) {
        long sum=0;
        for(int i=fromSlot;i<c.getContainerSize();i++) {
            ItemStack stack=c.getItem(i);
            if(!stack.isEmpty() && itemId.equals(
                    BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))sum+=stack.getCount();
        }
        return (int)Math.min(sum,Integer.MAX_VALUE);
    }

    /** Compatibility helper used in previous tests. */
    static int plainCount(Container c,String itemId,int fromSlot) {
        long sum=0;
        for(int i=fromSlot;i<c.getContainerSize();i++) {
            ItemStack stack=c.getItem(i);
            if(!stack.isEmpty() && !stack.hasTag()
                    && itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
                sum+=stack.getCount();
        }
        return (int)Math.min(sum,Integer.MAX_VALUE);
    }

    /** Physical capacity of the exact item + NBT lot, not just its registry ID. */
    static int capacity(Container dest,ItemStack item,int firstSlot) {
        if(item.isEmpty())return 0;
        long free=0;
        for(int i=firstSlot;i<dest.getContainerSize();i++) {
            if(!dest.canPlaceItem(i,item))continue;
            var stored=dest.getItem(i);
            if(stored.isEmpty())free+=Math.min(item.getMaxStackSize(),dest.getMaxStackSize());
            else if(ItemStack.isSameItemSameTags(stored,item))
                free+=Math.max(0,Math.min(stored.getMaxStackSize(),dest.getMaxStackSize())-stored.getCount());
        }
        return (int)Math.min(free,Integer.MAX_VALUE);
    }
}
