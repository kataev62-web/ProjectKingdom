package ru.projectkingdom.npc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Defensive event-level protection for enrolled containers during a projection.
 * NOTE: inventory menu clicks, hopper transfers, capabilities, and other mods
 * can bypass these events; this is NOT comprehensive mutation isolation.
 */
@Mod.EventBusSubscriber(modid=KingdomNpcMod.MOD_ID, bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class TradeForgeInteractionGuard {
    private static volatile TradeProtectedLocations locations;
    private TradeForgeInteractionGuard() {}
    /** Install only after rebuilding the binding index from persistent world data. */
    public static void install(TradeProtectedLocations index) { locations = index; }
    public static void clear() { locations = null; }
    private static boolean blocked(ServerLevel level, BlockPos pos) {
        var index=locations;
        return index!=null && !index.mayMutate(new TradeProtectedLocations.Location(
            level.dimension().location().toString(),pos.asLong()));
    }
    @SubscribeEvent public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if(event.getLevel() instanceof ServerLevel level && blocked(level,event.getPos())) {
            event.setCancellationResult(InteractionResult.FAIL); event.setCanceled(true);
        }
    }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) {
        if(event.getLevel() instanceof ServerLevel level && blocked(level,event.getPos()))event.setCanceled(true);
    }
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        if(event.getLevel() instanceof ServerLevel level)
            event.getAffectedBlocks().removeIf(pos -> blocked(level,pos));
    }
}
