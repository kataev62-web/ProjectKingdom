package ru.projectkingdom.enterprise;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.projectkingdom.npc.KingdomEnterpriseData;
import ru.projectkingdom.npc.KingdomNpcMod;

/**
 * Server-side player protection for ordinary containers enrolled as enterprise
 * cashboxes, raw/finished warehouses or legacy counters. Public access to
 * enterprise core menus must NEVER imply raw inventory access.
 *
 * This restricts manual interactions, destruction and explosions. Vanilla
 * hoppers and third-party item capabilities are a separate trust boundary:
 * for secure automatic logistics use the Kingdom managed-storage block.
 */
@Mod.EventBusSubscriber(modid = KingdomNpcMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EnterpriseContainerProtection {
    private EnterpriseContainerProtection() {}

    /** The zero UUID is reserved as a deny-all marker for conflicting bindings. */
    private static final UUID CONFLICT = new UUID(0L, 0L);

    private static UUID directOwner(ServerLevel level, BlockPos pos, KingdomEnterpriseData data) {
        if (!level.hasChunkAt(pos)) return null;
        String dimension = level.dimension().location().toString();
        UUID found = null;
        for (var enterprise : data.all()) {
            boolean enrolled = false;
            var legacy = data.counter(enterprise.id());
            if (legacy != null && dimension.equals(legacy.dimension()) && pos.equals(legacy.pos()))
                enrolled = true;
            for (EnterpriseBindingRole role : EnterpriseBindingRole.values()) {
                var binding = data.storageBinding(enterprise.id(), role);
                if (binding != null && dimension.equals(binding.dimension())
                        && pos.equals(binding.pos())) enrolled = true;
            }
            if (!enrolled) continue;
            if (found != null && !found.equals(enterprise.owner())) return CONFLICT;
            found = enterprise.owner();
        }
        return found;
    }

    /** A bound double chest protects both halves, even if only one was enrolled. */
    private static UUID ownerAt(ServerLevel level, BlockPos pos, KingdomEnterpriseData data) {
        UUID found = directOwner(level, pos, data);
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)
                || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return found;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos otherPos = pos.relative(direction);
            if (!level.hasChunkAt(otherPos)) continue;
            BlockState neighbor = level.getBlockState(otherPos);
            if (!neighbor.is(state.getBlock())
                    || neighbor.getValue(ChestBlock.TYPE) == ChestType.SINGLE
                    || neighbor.getValue(ChestBlock.FACING) != state.getValue(ChestBlock.FACING))
                continue;
            UUID another = directOwner(level, otherPos, data);
            if (another == null) continue;
            if (found != null && !found.equals(another)) return CONFLICT;
            found = another;
        }
        return found;
    }

    public static boolean denied(ServerLevel level, BlockPos pos, UUID actor) {
        if (!level.hasChunkAt(pos)) return true; // do not load chunks for rights checks
        UUID owner = ownerAt(level, pos, KingdomEnterpriseData.get(level.getServer()));
        return owner != null && !owner.equals(actor);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void opening(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer actor)) return;
        if (!denied(actor.serverLevel(), event.getPos(), actor.getUUID())) return;
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
        actor.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "Частный склад предприятия. Товары покупают и продают через меню предприятия."), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void breaking(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer actor)) return;
        if (denied(actor.serverLevel(), event.getPos(), actor.getUUID())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void explosions(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var data = KingdomEnterpriseData.get(level.getServer());
        event.getAffectedBlocks().removeIf(pos -> {
            UUID owner = ownerAt(level, pos, data);
            return owner != null; // no explosion is authorized to destroy enrolled enterprise stock
        });
    }
}
