package ru.projectkingdom.administration;

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
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomNpcMod;

/** Protect the physical coins belonging to a public municipal bank/treasury.
 * Bank access is public via its core; raw vault inventory access is private.
 * This blocks vanilla interaction/break/explosions, not untrusted hopper capabilities.
 */
@Mod.EventBusSubscriber(modid=KingdomNpcMod.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class MunicipalVaultProtection {
    private MunicipalVaultProtection() {}
    private record Binding(UUID town,AdministrativeBindingRole role) {}

    private static Binding binding(ServerLevel level,BlockPos pos) {
        if(!level.hasChunkAt(pos))return null;
        var cities=KingdomCivicsData.get(level.getServer());
        var admin=KingdomAdministrationData.get(level.getServer());
        String dim=level.dimension().location().toString();
        for(var town:cities.towns())
            for(var role:AdministrativeBindingRole.values())
                for(var bound:admin.containerBindings(town.id(),role))
                    if(dim.equals(bound.dimension())&&bound.packedPos()==pos.asLong())
                        return new Binding(town.id(),role);
        return null;
    }
    private static Binding bindingIncludingDoubleChest(ServerLevel level,BlockPos pos){
        Binding own=binding(level,pos);
        BlockState state=level.getBlockState(pos);
        if(!(state.getBlock() instanceof ChestBlock)
                ||state.getValue(ChestBlock.TYPE)==ChestType.SINGLE)return own;
        for(Direction face:Direction.Plane.HORIZONTAL){
            BlockPos other=pos.relative(face);
            if(!level.hasChunkAt(other))continue;
            var neighbor=level.getBlockState(other);
            if(!neighbor.is(state.getBlock())
                    ||neighbor.getValue(ChestBlock.TYPE)==ChestType.SINGLE
                    ||neighbor.getValue(ChestBlock.FACING)!=state.getValue(ChestBlock.FACING))continue;
            Binding otherBound=binding(level,other);
            if(otherBound!=null)return otherBound;
        }
        return own;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        var bound=bindingIncludingDoubleChest(player.serverLevel(),event.getPos());
        if(bound==null)return;
        var admin=KingdomAdministrationData.get(player.getServer());
        var town=KingdomCivicsData.get(player.getServer()).townById(bound.town());
        if(town!=null&&AdministrativePermissionService.mayManage(player.getUUID(),
                admin.founder(town.id()),town.mayor(),
                admin.roles(town.id(),player.getUUID()),bound.role().kind()))return;
        event.setCancellationResult(InteractionResult.FAIL);event.setCanceled(true);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
            "Городская касса защищена. Платежи выполняются через окно ратуши, банка или казначейства."),true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void destroy(BlockEvent.BreakEvent event) {
        if(!(event.getPlayer() instanceof ServerPlayer player))return;
        var bound=bindingIncludingDoubleChest(player.serverLevel(),event.getPos());
        if(bound==null)return;
        var admin=KingdomAdministrationData.get(player.getServer());
        var town=KingdomCivicsData.get(player.getServer()).townById(bound.town());
        if(town==null||!AdministrativePermissionService.mayManage(player.getUUID(),
                admin.founder(town.id()),town.mayor(),admin.roles(town.id(),player.getUUID()),
                bound.role().kind()))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void explosions(ExplosionEvent.Detonate event) {
        if(event.getLevel() instanceof ServerLevel level)
            event.getAffectedBlocks().removeIf(pos->bindingIncludingDoubleChest(level,pos)!=null);
    }
}
