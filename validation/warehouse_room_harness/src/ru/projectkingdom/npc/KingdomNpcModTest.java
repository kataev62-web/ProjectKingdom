package ru.projectkingdom.npc;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * Test-specific version of KingdomNpcMod that only includes the selectWarehouseCorners handler
 * without the complex constructor that requires many dependencies.
 */
public class KingdomNpcModTest {
    
    /** Register rooms even if another mod's block-use handler canceled the click.
     * While the player has an explicit room-selection session the click belongs
     * to Kingdom, not to the block's inventory/UI. */
    public void selectWarehouseCorners(PlayerInteractEvent.RightClickBlock event){
        // Forge can deliver both hands for one click; each survey needs two distinct clicks.
        if(event.getHand()!=net.minecraft.world.InteractionHand.MAIN_HAND
                ||!(event.getEntity() instanceof ServerPlayer player))return;
        var warehouse=ru.projectkingdom.administration.KingdomCentralWarehouseRooms.get(player.getServer());
        if(!warehouse.hasSession(player.getUUID()))return;
        if(!event.getItemStack().isEmpty()){
            // The user can switch away from an item without losing the first
            // corner. A wrong hand does not silently restart the whole survey.
            player.sendSystemMessage(Component.literal("[Центральный склад] Нужна пустая основная рука. Первая точка сохранена; выберите пустой слот и повторите ПКМ."));
        }else{
            var outcome=warehouse.select(player,event.getPos());
            String notice=ru.projectkingdom.administration.KingdomCentralWarehouseRooms.message(outcome);
            if(outcome!=ru.projectkingdom.administration.KingdomCentralWarehouseRooms.Result.CREATED
                    &&outcome!=ru.projectkingdom.administration.KingdomCentralWarehouseRooms.Result.FIRST_CORNER
                    &&warehouse.hasSession(player.getUUID()))
                notice+=" Первая точка сохранена; укажите вторую ещё раз.";
            player.sendSystemMessage(Component.literal("[Центральный склад] "+notice));
        }
        event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);
    }
}