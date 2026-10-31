package ru.projectkingdom.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server-authoritative visual classification of a persistent Kingdom NPC.
 * 0=unmanaged vanilla villager; 1=male/unspecified player model;
 * 2=adult female player model with skirt/bust; 3=underage female player model.
 */
public record KingdomFemaleOutfitPacket(UUID villagerId, byte appearance) {
    public static void encode(KingdomFemaleOutfitPacket packet, FriendlyByteBuf out) {
        out.writeUUID(packet.villagerId);
        out.writeByte(packet.appearance);
    }

    public static KingdomFemaleOutfitPacket decode(FriendlyByteBuf in) {
        return new KingdomFemaleOutfitPacket(in.readUUID(), in.readByte());
    }

    public static void handle(KingdomFemaleOutfitPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ru.projectkingdom.npc.client.KingdomFemaleOutfitClient.apply(
                        packet.villagerId, packet.appearance)));
        context.setPacketHandled(true);
    }
}
