package ru.projectkingdom.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import ru.projectkingdom.npc.KingdomMainMenu;

/** Return from a graphical Kingdom atlas into the unified maps hub. */
public final class OpenKingdomMapsHubPacket {
    public static void encode(OpenKingdomMapsHubPacket packet, FriendlyByteBuf buf) {}
    public static OpenKingdomMapsHubPacket decode(FriendlyByteBuf buf) {
        return new OpenKingdomMapsHubPacket();
    }
    public static void handle(OpenKingdomMapsHubPacket packet,
                              Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get();
        ctx.enqueueWork(() -> {
            var sender = ctx.getSender();
            if (sender == null || sender.getServer() == null) return;
            KingdomMainMenu.openMaps(sender);
        });
        ctx.setPacketHandled(true);
    }
}
