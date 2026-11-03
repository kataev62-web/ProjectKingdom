package ru.projectkingdom.cityarchitect.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public record CityArchitectPreviewPacket(
        UUID token,
        UUID planId,
        UUID stageId,
        String title,
        String explanation,
        long expiresAt,
        List<BlockPos> markers
) {
    private static final int MAX_MARKERS = 2_048;

    public CityArchitectPreviewPacket {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(stageId, "stageId");
        title = Objects.requireNonNull(title, "title").trim();
        explanation = Objects.requireNonNull(explanation, "explanation").trim();
        markers = List.copyOf(Objects.requireNonNull(markers, "markers"));
        if (title.isEmpty() || explanation.isEmpty() || markers.size() > MAX_MARKERS) {
            throw new IllegalArgumentException("Invalid city architect preview");
        }
    }

    public static void encode(CityArchitectPreviewPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.token());
        buffer.writeUUID(packet.planId());
        buffer.writeUUID(packet.stageId());
        buffer.writeUtf(packet.title(), 96);
        buffer.writeUtf(packet.explanation(), 512);
        buffer.writeLong(packet.expiresAt());
        buffer.writeVarInt(packet.markers().size());
        packet.markers().forEach(buffer::writeBlockPos);
    }

    public static CityArchitectPreviewPacket decode(FriendlyByteBuf buffer) {
        UUID token = buffer.readUUID();
        UUID plan = buffer.readUUID();
        UUID stage = buffer.readUUID();
        String title = buffer.readUtf(96);
        String explanation = buffer.readUtf(512);
        long expiresAt = buffer.readLong();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_MARKERS) {
            throw new IllegalArgumentException("Invalid marker preview size");
        }
        List<BlockPos> markers = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            markers.add(buffer.readBlockPos());
        }
        return new CityArchitectPreviewPacket(token, plan, stage, title, explanation,
                expiresAt, markers);
    }

    public static void handle(
            CityArchitectPreviewPacket packet, Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ru.projectkingdom.cityarchitect.client.CityArchitectPreviewScreen.accept(packet)));
        context.setPacketHandled(true);
    }
}
