package ru.projectkingdom.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ru.projectkingdom.core.KingdomBookMenu;
import ru.projectkingdom.npc.KingdomWorldAtlasMenu;

/** The client requests only OPEN; permissions and coordinates are server-owned. */
public final class OpenWorldAtlasPacket {
    public static void encode(OpenWorldAtlasPacket packet, FriendlyByteBuf buffer) {}
    public static OpenWorldAtlasPacket decode(FriendlyByteBuf buffer) { return new OpenWorldAtlasPacket(); }
    public static void handle(OpenWorldAtlasPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof KingdomBookMenu)) return;
            KingdomWorldAtlasMenu.open(player);
        });
        context.setPacketHandled(true);
    }
}
