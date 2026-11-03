package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Supply-only check. Never takes material until a verified native TFC
 * support placement executor commits a physical placement.
 */
public final class KingdomMineSupportSupply {
    private KingdomMineSupportSupply() {}

    public enum State { AVAILABLE, NEEDS_SUPPORT_BEAMS, WAREHOUSE_UNAVAILABLE }

    public static int count(Container inventory) {
        if(inventory==null)return 0;
        int total=0;
        for(int slot=0;slot<inventory.getContainerSize();slot++) {
            ItemStack item=inventory.getItem(slot);
            if(item.isEmpty())continue;
            String id=BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
            if(KingdomMineSupportPlan.supports(id))
                total=Math.min(10_000,total+item.getCount());
        }
        return total;
    }

    public static State inspect(ServerLevel level,KingdomNpcData.Worker worker) {
        if(level==null || worker==null || worker.employer==null || !worker.hasSource)
            return State.WAREHOUSE_UNAVAILABLE;
        BlockPos pos=new BlockPos(worker.sourceX,worker.sourceY,worker.sourceZ);
        if(!level.hasChunkAt(pos)
                ||!KingdomTerritories.get(level.getServer())
                    .usable(level,pos,worker.employer,"raw"))
            return State.WAREHOUSE_UNAVAILABLE;
        Container storage=KingdomContainerAccess.resolve(level,pos);
        if(storage==null)return State.WAREHOUSE_UNAVAILABLE;
        // Two three-high poles + a native horizontal link need eight real items.
        return count(storage)>=8?State.AVAILABLE:State.NEEDS_SUPPORT_BEAMS;
    }
}
