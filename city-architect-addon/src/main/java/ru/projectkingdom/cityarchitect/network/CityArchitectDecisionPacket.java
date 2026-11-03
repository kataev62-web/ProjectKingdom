package ru.projectkingdom.cityarchitect.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public record CityArchitectDecisionPacket(UUID token, boolean confirm) {
    public CityArchitectDecisionPacket {
        Objects.requireNonNull(token, "token");
    }

    public static void encode(CityArchitectDecisionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.token());
        buffer.writeBoolean(packet.confirm());
    }

    public static CityArchitectDecisionPacket decode(FriendlyByteBuf buffer) {
        return new CityArchitectDecisionPacket(buffer.readUUID(), buffer.readBoolean());
    }

    public static void handle(
            CityArchitectDecisionPacket packet, Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> CityArchitectNetwork.handleDecision(context.getSender(), packet));
        context.setPacketHandled(true);
    }
}
