package ru.projectkingdom.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import ru.projectkingdom.map.KingdomGraphicAtlasData;

/**
 * C2S view request. Never accepts client-provided owner, nation, rights or plots.
 * A per-player cooldown bounds server work and packets.
 */
public record GraphicAtlasRequestPacket(int requestId, int centerX, int centerZ,
                                        int scale, int layer) {
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    public static void encode(GraphicAtlasRequestPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.requestId);
        buf.writeInt(packet.centerX);
        buf.writeInt(packet.centerZ);
        buf.writeVarInt(packet.scale);
        buf.writeByte(packet.layer);
    }
    public static GraphicAtlasRequestPacket decode(FriendlyByteBuf buf) {
        return new GraphicAtlasRequestPacket(buf.readVarInt(), buf.readInt(),
                buf.readInt(), buf.readVarInt(), buf.readUnsignedByte());
    }
    public static void handle(GraphicAtlasRequestPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get();
        ctx.enqueueWork(() -> {
            var sender = ctx.getSender();
            if (sender == null || sender.getServer() == null
                    || !KingdomGraphicAtlasData.supportedScale(packet.scale)
                    || packet.layer < 0 || packet.layer >= KingdomGraphicAtlasData.Layer.values().length
                    || Math.abs((long)packet.centerX) > 16384
                    || Math.abs((long)packet.centerZ) > 16384) return;
            long now = sender.getServer().overworld().getGameTime();
            UUID uuid = sender.getUUID();
            Long previous = LAST_REQUEST.get(uuid);
            if (previous != null && now >= previous && now - previous < 10) return;
            LAST_REQUEST.put(uuid, now);
            if (LAST_REQUEST.size() > 20000) LAST_REQUEST.clear();
            var snapshot = KingdomGraphicAtlasData.fromPlayer(sender,
                    packet.requestId, packet.centerX, packet.centerZ, packet.scale,
                    KingdomGraphicAtlasData.Layer.values()[packet.layer]);
            KingdomNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender),
                    new GraphicAtlasResponsePacket(snapshot));
        });
        ctx.setPacketHandled(true);
    }
}
