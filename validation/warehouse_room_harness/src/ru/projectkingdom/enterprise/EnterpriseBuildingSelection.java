package ru.projectkingdom.enterprise;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/** Stub: world-room selection cannot affect the isolated warehouse harness. */
public final class EnterpriseBuildingSelection {
    private EnterpriseBuildingSelection(){}
    public static void cancel(UUID player){}
    public static void begin(ServerPlayer player, UUID town, String type){
        // Cancel warehouse session like the real implementation
        ru.projectkingdom.administration.KingdomCentralWarehouseRooms.cancel(player.getUUID());
    }
    public static void select(PlayerInteractEvent.RightClickBlock event) {}
}