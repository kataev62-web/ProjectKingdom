package ru.projectkingdom.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ru.projectkingdom.core.KingdomBookMenu;
import ru.projectkingdom.npc.KingdomLandMapMenu;

/** Opens the existing server-authoritative land map from the player book.
 * No user-supplied coordinates, town identifiers, or privilege claims.
 */
public final class OpenLandMapFromBookPacket {
    public static void encode(OpenLandMapFromBookPacket packet, FriendlyByteBuf buffer) {}
    public static OpenLandMapFromBookPacket decode(FriendlyByteBuf buffer) {
        return new OpenLandMapFromBookPacket();
    }
    public static void handle(OpenLandMapFromBookPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof KingdomBookMenu)) return;
            KingdomLandMapMenu.open(player);
        });
        context.setPacketHandled(true);
    }
}
