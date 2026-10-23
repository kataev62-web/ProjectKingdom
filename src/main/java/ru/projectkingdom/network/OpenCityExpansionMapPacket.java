package ru.projectkingdom.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ru.projectkingdom.core.KingdomBookMenu;
import ru.projectkingdom.npc.KingdomCityExpansionMapMenu;

/** Client sends an empty request; actor and town are resolved on the server. */
public final class OpenCityExpansionMapPacket {
    public static void encode(OpenCityExpansionMapPacket packet, FriendlyByteBuf buffer) {}
    public static OpenCityExpansionMapPacket decode(FriendlyByteBuf buffer) {
        return new OpenCityExpansionMapPacket();
    }
    public static void handle(OpenCityExpansionMapPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof KingdomBookMenu)) return;
            KingdomCityExpansionMapMenu.open(player);
        });
        context.setPacketHandled(true);
    }
}
