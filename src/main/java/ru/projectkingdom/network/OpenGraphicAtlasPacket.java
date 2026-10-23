package ru.projectkingdom.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server-approved request to show the read-only graphical atlas on the client. */
public final class OpenGraphicAtlasPacket {
    public static void encode(OpenGraphicAtlasPacket packet, FriendlyByteBuf buf) {}
    public static OpenGraphicAtlasPacket decode(FriendlyByteBuf buf) {
        return new OpenGraphicAtlasPacket();
    }
    public static void handle(OpenGraphicAtlasPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ru.projectkingdom.map.KingdomGraphicAtlasClient.open()));
        ctx.setPacketHandled(true);
    }
}
