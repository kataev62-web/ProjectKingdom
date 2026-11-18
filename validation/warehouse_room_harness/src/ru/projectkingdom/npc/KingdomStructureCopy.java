package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Stub: schematic copy sessions are irrelevant to city room tests. */
public final class KingdomStructureCopy {
    private KingdomStructureCopy(){}
    public static void cancel(UUID player){}
    public static void begin(ServerPlayer player, UUID town){
        // Cancel warehouse session like the real implementation
        ru.projectkingdom.administration.KingdomCentralWarehouseRooms.cancel(player.getUUID());
    }
}