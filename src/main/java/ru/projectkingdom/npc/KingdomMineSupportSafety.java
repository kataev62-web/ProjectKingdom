package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * TFC collapse-safety inspection. TFC horizontal support beams protect a
 * 9x5x9 volume (four blocks horizontally, one up/down). Vertical beams alone
 * do not protect rock. We deliberately inspect the real registered block IDs
 * and the real TFC collapse tags instead of treating decorative timber as a
 * support. This class never claims that a tunnel is reinforced if it is not.
 */
public final class KingdomMineSupportSafety {
    private KingdomMineSupportSafety() {}

    public enum Result { SAFE, NEEDS_HORIZONTAL_BEAM, UNSUPPORTED_ROCK, UNLOADED }

    public static boolean isHorizontalSupportId(String id) {
        return id!=null && id.startsWith("tfc:wood/horizontal_support/");
    }

    public static boolean isTfcHorizontalSupport(BlockState state) {
        return isHorizontalSupportId(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }

    public static boolean isCollapseRock(BlockState state) {
        return state.is(net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.BLOCK,
            new net.minecraft.resources.ResourceLocation("tfc","can_trigger_collapse")));
    }

    public static boolean hasHorizontalSupport(ServerLevel level,BlockPos roof) {
        // TFC's native support radius is 4 horizontally and 1 vertically.
        for(int dy=-1;dy<=1;dy++)
            for(int dx=-4;dx<=4;dx++)
                for(int dz=-4;dz<=4;dz++){
                    BlockPos pos=roof.offset(dx,dy,dz);
                    if(!level.hasChunkAt(pos))continue;
                    if(isTfcHorizontalSupport(level.getBlockState(pos)))return true;
                }
        return false;
    }

    public static Result inspect(ServerLevel level,BlockPos target) {
        if(!level.hasChunkAt(target)||!level.hasChunkAt(target.above())
                ||!level.hasChunkAt(target.below()))return Result.UNLOADED;
        BlockState state=level.getBlockState(target);
        if(!isCollapseRock(state))return Result.SAFE;
        // A solid, non-collapsible block below is a native TFC support case.
        BlockState floor=level.getBlockState(target.below());
        boolean floorSupports=floor.isSolidRender(level,target.below())
                && !isCollapseRock(floor);
        if(floorSupports)return Result.SAFE;
        return hasHorizontalSupport(level,target)?Result.SAFE:Result.NEEDS_HORIZONTAL_BEAM;
    }

    public static boolean galleryHasSupport(ServerLevel level,BlockPos cell) {
        for(int dy=0;dy<2;dy++){
            BlockPos target=cell.above(dy);
            if(!level.hasChunkAt(target))return false;
            if(isCollapseRock(level.getBlockState(target))
                    && inspect(level,target)!=Result.SAFE)return false;
        }
        return true;
    }
}
