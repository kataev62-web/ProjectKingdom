package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * All old lectern projects remain valid, but new projects may use a craftable
 * Kingdom drafting table without rewriting saved construction coordinates.
 */
public final class KingdomConstructionMarkers {
    private KingdomConstructionMarkers() {}

    public static boolean isMarker(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        var state = level.getBlockState(pos);
        return state.is(Blocks.LECTERN) || state.is(KingdomConstructionRegistry.DRAFTING_TABLE.get());
    }
}
