package ru.projectkingdom.administration;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomContainerAccess;
import ru.projectkingdom.npc.KingdomNpcMod;

/**
 * Whole-room protection for ordinary players. Municipal stock is still
 * stored as genuine in-world items, never in a virtual slot balance.
 * Non-player hopper/capability automation needs a separate logistics policy.
 */
@Mod.EventBusSubscriber(modid=KingdomNpcMod.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class KingdomWarehouseRoomProtection {
    private KingdomWarehouseRoomProtection(){}
    private static boolean mayManage(ServerPlayer player,java.util.UUID townId){
        var city=KingdomCivicsData.get(player.getServer()).townById(townId);
        if(city==null)return false;
        var admin=KingdomAdministrationData.get(player.getServer());
        return AdministrativePermissionService.mayManage(player.getUUID(),
            admin.founder(townId),city.mayor(),admin.roles(townId,player.getUUID()),
            AdministrativeKind.CENTRAL_WAREHOUSE);
    }
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void interact(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        var room=KingdomCentralWarehouseRooms.get(player.getServer()).at(player.serverLevel(),event.getPos());
        if(room==null||mayManage(player,room.town()))return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        player.displayClientMessage(Component.literal(
            "Это охраняемый городской склад. Обратитесь к управляющему складом."),true);
    }
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void breakBlock(BlockEvent.BreakEvent event){
        if(!(event.getPlayer() instanceof ServerPlayer player))return;
        var room=KingdomCentralWarehouseRooms.get(player.getServer()).at(player.serverLevel(),event.getPos());
        if(room==null)return;
        if(!mayManage(player,room.town())){
            event.setCanceled(true);return;
        }
        // Never inadvertently break a filled chest when rearranging rooms.
        if(player.serverLevel().getBlockEntity(event.getPos())!=null
                &&player.serverLevel().getBlockState(event.getPos()).getBlock() instanceof ChestBlock){
            var contents=KingdomContainerAccess.resolve(player.serverLevel(),event.getPos());
            if(contents==null || !contents.isEmpty()){
                event.setCanceled(true);
                player.displayClientMessage(Component.literal(
                    "Сначала выньте все товары из сундука центрального склада."),true);
            }
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void place(BlockEvent.EntityPlaceEvent event){
        if(!(event.getLevel() instanceof ServerLevel level)
            ||!(event.getEntity() instanceof ServerPlayer player))return;
        var room=KingdomCentralWarehouseRooms.get(level.getServer()).at(level,event.getPos());
        if(room!=null&&!mayManage(player,room.town()))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void explode(ExplosionEvent.Detonate event){
        if(event.getLevel() instanceof ServerLevel level)
            event.getAffectedBlocks().removeIf(pos->
                KingdomCentralWarehouseRooms.get(level.getServer()).at(level,pos)!=null);
    }
}
