package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Conservative, server-only chest-to-chest hauling: existing items move, never spawn. */
public final class NpcLogistics {
    private NpcLogistics() {}

    public static boolean isContainer(ServerLevel level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof Container;
    }

    public static void tick(ServerLevel level, Villager npc, KingdomNpcData.Worker worker) {
        if (!"haul".equals(worker.order) || worker.employer == null || worker.contractPaused || worker.ageYears < 18) return;
        if (!worker.hasSource || !worker.hasDestination || !worker.dimension.equals(level.dimension().location().toString())) return;
        if (worker.sourceX == worker.destX && worker.sourceY == worker.destY && worker.sourceZ == worker.destZ) return;
        // The employer must own both chunks AND explicitly mark their storage roles.
        KingdomTerritories access=KingdomTerritories.get(level.getServer());
        BlockPos source=new BlockPos(worker.sourceX,worker.sourceY,worker.sourceZ);
        BlockPos destination=new BlockPos(worker.destX,worker.destY,worker.destZ);
        if (!access.usable(level,source,worker.employer,"raw") || !access.usable(level,destination,worker.employer,"finished")) return;

        BlockPos target = worker.cargo.isEmpty() ? new BlockPos(worker.sourceX, worker.sourceY, worker.sourceZ) : new BlockPos(worker.destX, worker.destY, worker.destZ);
        if (!level.hasChunkAt(target)) return; // Never force-load remote chunks.
        if (npc.distanceToSqr(target.getX()+0.5, target.getY()+0.5, target.getZ()+0.5) > 6.25) {
            if (npc.getNavigation().isDone()) npc.getNavigation().moveTo(target.getX()+0.5,target.getY(),target.getZ()+0.5,0.8);
            return;
        }
        npc.getNavigation().stop();
        BlockEntity be=level.getBlockEntity(target);
        if (!(be instanceof Container chest)) return;
        if (worker.cargo.isEmpty()) {
            for (int i=0;i<chest.getContainerSize();i++) {
                ItemStack original=chest.getItem(i);
                if (original.isEmpty() || !chest.canTakeItem(chest, i, original)) continue;
                int quantity=Math.min(8,original.getCount());
                ItemStack extracted=chest.removeItem(i,quantity);
                if (extracted.isEmpty()) continue;
                worker.cargo=extracted.copy();
                chest.setChanged();
                return;
            }
        } else {
            ItemStack remaining=worker.cargo.copy();
            for (int i=0;i<chest.getContainerSize() && !remaining.isEmpty();i++) {
                if (!chest.canPlaceItem(i,remaining)) continue;
                ItemStack existing=chest.getItem(i);
                if (existing.isEmpty()) {
                    int move=Math.min(remaining.getCount(),Math.min(remaining.getMaxStackSize(),chest.getMaxStackSize()));
                    ItemStack placed=remaining.copy(); placed.setCount(move);
                    chest.setItem(i,placed); remaining.shrink(move);
                } else if (ItemStack.isSameItemSameTags(existing,remaining)) {
                    int free=Math.min(existing.getMaxStackSize(),chest.getMaxStackSize())-existing.getCount();
                    if (free<=0) continue;
                    int move=Math.min(free,remaining.getCount());
                    existing.grow(move); remaining.shrink(move);
                    chest.setChanged();
                }
            }
            if (remaining.getCount()!=worker.cargo.getCount()) {
                chest.setChanged();
                if (remaining.isEmpty()) worker.deliveredItems+=worker.cargo.getCount();
                else worker.deliveredItems+=worker.cargo.getCount()-remaining.getCount();
                worker.cargo=remaining;
            }
        }
    }
}
