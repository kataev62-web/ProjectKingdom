package ru.projectkingdom.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

/** Server-to-client appearance mapping. Protocol ID 22; 40 chars upper limit. */
public record KingdomNpcAppearancePacket(UUID entityId, String appearanceId) {
    public static void encode(KingdomNpcAppearancePacket message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.entityId);
        buffer.writeUtf(message.appearanceId, 40);
    }
    public static KingdomNpcAppearancePacket decode(FriendlyByteBuf buffer) {
        return new KingdomNpcAppearancePacket(buffer.readUUID(), buffer.readUtf(40));
    }
    public static void handle(KingdomNpcAppearancePacket message, Supplier<NetworkEvent.Context> supplied) {
        NetworkEvent.Context context = supplied.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ru.projectkingdom.client.npc.KingdomFemaleMeshRenderer.sync(message.entityId, message.appearanceId)));
        context.setPacketHandled(true);
    }
}
