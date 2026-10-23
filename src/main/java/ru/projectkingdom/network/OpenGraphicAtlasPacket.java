package ru.projectkingdom.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server-approved request to show the read-only graphical atlas on the client. */
public record OpenGraphicAtlasPacket(int layer) {
    public OpenGraphicAtlasPacket {
        if (layer < 0 || layer >= ru.projectkingdom.map.KingdomGraphicAtlasData.Layer.values().length)
            throw new IllegalArgumentException("Unknown Kingdom map layer");
    }
    public static void encode(OpenGraphicAtlasPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.layer());
    }
    public static OpenGraphicAtlasPacket decode(FriendlyByteBuf buf) {
        return new OpenGraphicAtlasPacket(buf.readVarInt());
    }
    public static void handle(OpenGraphicAtlasPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ru.projectkingdom.map.KingdomGraphicAtlasClient.open(packet.layer())));
        ctx.setPacketHandled(true);
    }
}
