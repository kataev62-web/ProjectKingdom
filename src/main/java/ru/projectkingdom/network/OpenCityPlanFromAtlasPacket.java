package ru.projectkingdom.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import ru.projectkingdom.npc.KingdomCityExpansionMapMenu;

/** Read-only atlas navigation to the existing server-validated founder plan menu. */
public final class OpenCityPlanFromAtlasPacket {
    public static void encode(OpenCityPlanFromAtlasPacket packet, FriendlyByteBuf buffer) {}
    public static OpenCityPlanFromAtlasPacket decode(FriendlyByteBuf buffer) {
        return new OpenCityPlanFromAtlasPacket();
    }
    public static void handle(OpenCityPlanFromAtlasPacket packet,
                              Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get();
        ctx.enqueueWork(() -> {
            var player = ctx.getSender();
            if (player == null || player.getServer() == null) return;
            // Only opening a UI: all planning edits and purchases recheck founder
            // and town hall rights in the authoritative server menu.
            KingdomCityExpansionMapMenu.open(player);
        });
        ctx.setPacketHandled(true);
    }
}
