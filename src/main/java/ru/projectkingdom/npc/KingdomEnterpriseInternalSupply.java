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
import net.minecraft.world.level.block.entity.BlockEntity;
import ru.projectkingdom.enterprise.EnterpriseBindingRole;

/**
 * Stage 2 intra-company replenishment. A merchant purchase is never needed to
 * move materials already owned by the same enterprise. Reuses the actual
 * courier, physical cargo and existing durable supply-order transfer journal.
 *
 * Only registered RAW storage -> this worker's marked RAW chest is supported.
 * Recipe slots 0..8 are reserved and are never used for courier delivery.
 */
public final class KingdomEnterpriseInternalSupply {
    private KingdomEnterpriseInternalSupply() {}
    private static final int MAX_REQUEST = 16;

    /** Can a persisted internal courier order still touch both real chests? */
    public static boolean authorized(ServerLevel level, KingdomSupplyOrders.Order order) {
        if (order == null || order.mode != KingdomSupplyOrders.Mode.INTERNAL
                || !order.buyer.equals(order.seller)
                || !order.dimension.equals(level.dimension().location().toString())
                || order.source.equals(order.destination)
                || !level.hasChunkAt(order.source) || !level.hasChunkAt(order.destination)) return false;
        var data = KingdomEnterpriseData.get(level.getServer());
        var firm = data.find(order.buyer);
        var bound = data.storageBinding(order.buyer, EnterpriseBindingRole.RAW_STORAGE);
        if (firm == null || bound == null || !bound.dimension().equals(order.dimension)
                || !bound.pos().equals(order.source)
                || data.storageClaimedByOther(order.buyer, order.dimension, order.source)
                || data.storageClaimedByOther(order.buyer, order.dimension, order.destination)
                || !KingdomTerritories.get(level.getServer()).canUse(level, order.source, firm.owner())
                || !KingdomTerritories.get(level.getServer()).usable(level, order.destination, firm.owner(), "raw"))
            return false;
        if (!(level.getBlockEntity(order.source) instanceof Container)
                || !(level.getBlockEntity(order.destination) instanceof Container)) return false;
        for (var worker : KingdomNpcData.get(level.getServer()).all()) {
            if (worker.employer == null || !firm.owner().equals(worker.employer)
                    || !worker.hasSource || !worker.hasWorkstation
                    || !worker.dimension.equals(order.dimension)
                    || !new BlockPos(worker.sourceX, worker.sourceY, worker.sourceZ).equals(order.destination))
                continue;
            var plan = KingdomNpcWorkshopData.get(level.getServer()).plan(worker.entityId);
            if (plan != null && plan.enterprise().equals(order.buyer)
                    && !worker.contractPaused && new BlockPos(worker.workX,worker.workY,worker.workZ)
                        .distSqr(order.destination) <= 36)
                return true;
        }
        return false;
    }

    /** True if a real delivery is already pending or was placed. */
    public static boolean request(ServerLevel level, KingdomNpcData.Worker worker,
            KingdomNpcWorkshopData.Plan plan, Container target) {
        Objects.requireNonNull(level);
        if (worker == null || plan == null || target == null
                || worker.employer == null || !worker.hasSource
                || !worker.dimension.equals(level.dimension().location().toString())) return false;
        var firms = KingdomEnterpriseData.get(level.getServer());
        var firm = firms.find(plan.enterprise());
        var bound = firms.storageBinding(plan.enterprise(), EnterpriseBindingRole.RAW_STORAGE);
        if (firm == null || !firm.owner().equals(worker.employer) || bound == null
                || !bound.dimension().equals(worker.dimension)) return false;
        BlockPos from = bound.pos();
        BlockPos to = new BlockPos(worker.sourceX,worker.sourceY,worker.sourceZ);
        if (from.equals(to) || target.getContainerSize() <= 9
                || !level.hasChunkAt(from) || !level.hasChunkAt(to)) return false;
        var sourceEntity = level.getBlockEntity(from);
        if (!(sourceEntity instanceof Container source) || level.getBlockEntity(to) != target) return false;
        // Missing/changing ownership or in-flight uncertain transfers must not
        // be treated as empty storage.
        var ledger = KingdomSupplyOrders.get(level.getServer());
        Set<String> seen = new HashSet<>();
        for (String itemId : plan.grid()) {
            if (itemId.isBlank() || !seen.add(itemId)) continue;
            int recipeUnits = 0;
            for (String ingredient : plan.grid()) if (itemId.equals(ingredient)) recipeUnits++;
            if (plainCount(target,itemId,0) >= recipeUnits) continue;
            if (ledger.hasActiveInternal(plan.enterprise(),worker.dimension,from,to,itemId))
                return true;
            var global = KingdomWarehouseStockCoordinator.raw(level,plan.enterprise(),itemId,to);
            if (!global.complete()) return false;
            // A missing ingredient elsewhere in the company is a logistical
            // request, not a second merchant purchase.
            int desired = Math.max(recipeUnits, 4);
            int local = plainCount(target,itemId,0);
            if (local >= desired || global.physical() <= local) continue;
            int freeAtSource = plainCount(source,itemId,0)
                    - ledger.reserved(worker.dimension,from,itemId);
            int move = Math.min(MAX_REQUEST,Math.min(desired-local,freeAtSource));
            ItemStack prototype = plainExample(source,itemId);
            if (prototype.isEmpty()) continue; // Tagged TFC lots need a lot-aware carrier.
            move = Math.min(move,capacity(target,prototype,9));
            if (move <= 0) continue;
            var order = ledger.createInternal(plan.enterprise(),worker.dimension,itemId,from,to,move);
            if (order != null && authorized(level,order)) return true;
            // Do not create another order for this item if the initial one
            // could not safely be issued. The ledger does not move any items.
            if (order != null) ledger.cancelInternalUnassigned(order.id);
        }
        return false;
    }

    static int plainCount(Container c, String itemId, int fromSlot) {
        long total=0;
        for (int i=fromSlot;i<c.getContainerSize();i++) {
            ItemStack item=c.getItem(i);
            if (!item.isEmpty() && !item.hasTag()
                    && itemId.equals(BuiltInRegistries.ITEM.getKey(item.getItem()).toString()))
                total+=item.getCount();
        }
        return (int)Math.min(total,Integer.MAX_VALUE);
    }

    private static ItemStack plainExample(Container c, String itemId) {
        for(int i=0;i<c.getContainerSize();i++) {
            ItemStack item=c.getItem(i);
            if(!item.isEmpty()&&!item.hasTag()
                    && itemId.equals(BuiltInRegistries.ITEM.getKey(item.getItem()).toString()))
                return item.copy();
        }
        return ItemStack.EMPTY;
    }

    static int capacity(Container dest,ItemStack item,int firstSlot) {
        if (item.isEmpty() || item.hasTag()) return 0;
        long free=0;
        for(int i=firstSlot;i<dest.getContainerSize();i++) {
            if(!dest.canPlaceItem(i,item))continue;
            var existing=dest.getItem(i);
            if(existing.isEmpty())free+=Math.min(item.getMaxStackSize(),dest.getMaxStackSize());
            else if(ItemStack.isSameItemSameTags(existing,item))
                free+=Math.max(0,Math.min(existing.getMaxStackSize(),dest.getMaxStackSize())-existing.getCount());
        }
        return (int)Math.min(free,Integer.MAX_VALUE);
    }
}
