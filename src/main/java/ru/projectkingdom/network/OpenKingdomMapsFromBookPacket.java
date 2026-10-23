package ru.projectkingdom.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ru.projectkingdom.core.KingdomBookMenu;
import ru.projectkingdom.npc.KingdomMainMenu;

/**
 * Book -> single Kingdom Maps hub. The client provides no coordinates,
 * ownership, role or purchased territory.
 */
public final class OpenKingdomMapsFromBookPacket {
    public static void encode(OpenKingdomMapsFromBookPacket packet, FriendlyByteBuf buf) {}
    public static OpenKingdomMapsFromBookPacket decode(FriendlyByteBuf buf) {
        return new OpenKingdomMapsFromBookPacket();
    }
    public static void handle(OpenKingdomMapsFromBookPacket packet,
                              Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !(player.containerMenu instanceof KingdomBookMenu)) return;
            KingdomMainMenu.openMaps(player);
        });
        ctx.setPacketHandled(true);
    }
}
