package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * TFC collapse-safety inspection. The native data-driven support registry
 * decides whether the mined block is supported. Decorative timber never
 * grants permission, and unavailable integration fails closed.
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

    /**
     * TFC 1.20.x uses a data-driven support registry. Use its authoritative
     * Support.isSupported(BlockGetter, BlockPos) instead of guessing a radius.
     * Reflection avoids adding a hard runtime dependency to Kingdom.
     * Failure is fail-closed: no collapse-triggering block is excavated.
     */
    private static final java.lang.reflect.Method NATIVE_IS_SUPPORTED=lookupNativeSupport();

    private static java.lang.reflect.Method lookupNativeSupport() {
        try {
            return Class.forName("net.dries007.tfc.util.Support").getMethod(
                "isSupported",net.minecraft.world.level.BlockGetter.class,BlockPos.class);
        } catch(ReflectiveOperationException | LinkageError unavailable) {
            return null;
        }
    }

    public static boolean hasHorizontalSupport(ServerLevel level,BlockPos roof) {
        if(NATIVE_IS_SUPPORTED==null||!level.hasChunkAt(roof))return false;
        try {
            return Boolean.TRUE.equals(NATIVE_IS_SUPPORTED.invoke(null,level,roof));
        } catch(ReflectiveOperationException | LinkageError unavailable) {
            return false;
        }
    }

    public static Result inspect(ServerLevel level,BlockPos target) {
        if(!level.hasChunkAt(target)||!level.hasChunkAt(target.above())
                ||!level.hasChunkAt(target.below()))return Result.UNLOADED;
        BlockState state=level.getBlockState(target);
        if(!isCollapseRock(state))return Result.SAFE;
        // Solid ground does not prove the surrounding rock cannot collapse.
        return hasHorizontalSupport(level,target)
            ?Result.SAFE:Result.NEEDS_HORIZONTAL_BEAM;
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
