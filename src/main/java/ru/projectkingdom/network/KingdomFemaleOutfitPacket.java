package ru.projectkingdom.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Only server can authoritatively assign clothing; sent for tracked Kingdom worker UUIDs. */
public record KingdomFemaleOutfitPacket(UUID villagerId, boolean enabled) {
    public static void encode(KingdomFemaleOutfitPacket packet, FriendlyByteBuf out) {
        out.writeUUID(packet.villagerId);
        out.writeBoolean(packet.enabled);
    }
    public static KingdomFemaleOutfitPacket decode(FriendlyByteBuf in) {
        return new KingdomFemaleOutfitPacket(in.readUUID(), in.readBoolean());
    }
    public static void handle(KingdomFemaleOutfitPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> ru.projectkingdom.npc.client.KingdomFemaleOutfitClient.apply(
                                packet.villagerId, packet.enabled)));
        context.setPacketHandled(true);
    }
}
