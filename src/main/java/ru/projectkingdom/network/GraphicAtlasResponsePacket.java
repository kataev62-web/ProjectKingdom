package ru.projectkingdom.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ru.projectkingdom.map.KingdomGraphicAtlasData;

/** Bounded sparse S2C projection. The receiver cannot mutate land or money. */
public record GraphicAtlasResponsePacket(KingdomGraphicAtlasData.Snapshot snapshot) {
    private static final KingdomGraphicAtlasData.Tile EMPTY =
            new KingdomGraphicAtlasData.Tile(0, false, false, false, 0, 0, 0, "", "", "");

    public static void encode(GraphicAtlasResponsePacket packet, FriendlyByteBuf buf) {
        var view = packet.snapshot;
        buf.writeVarInt(view.requestId());
        buf.writeInt(view.centerX());
        buf.writeInt(view.centerZ());
        buf.writeVarInt(view.scale());
        buf.writeByte(view.layer().ordinal());
        int count = 0;
        for (var cell : view.tiles()) if (!cell.empty()) count++;
        buf.writeVarInt(count);
        for (int i = 0; i < view.tiles().size(); i++) {
            var cell = view.tiles().get(i);
            if (cell.empty()) continue;
            buf.writeVarInt(i);
            buf.writeVarInt(cell.claims());
            buf.writeBoolean(cell.own());
            buf.writeBoolean(cell.mixed());
            buf.writeBoolean(cell.planned());
            buf.writeInt(cell.townColor());
            buf.writeInt(cell.nationColor());
            buf.writeVarInt(cell.enterprises());
            buf.writeUtf(cell.town(), 72);
            buf.writeUtf(cell.nation(), 72);
            buf.writeUtf(cell.business(), 72);
            buf.writeByte(cell.terrain());
            buf.writeShort(cell.elevation());
        }
    }

    public static GraphicAtlasResponsePacket decode(FriendlyByteBuf buf) {
        int request = buf.readVarInt();
        int x = buf.readInt(), z = buf.readInt();
        int scale = buf.readVarInt();
        int layerId = buf.readUnsignedByte();
        if (!KingdomGraphicAtlasData.supportedScale(scale)
                || layerId >= KingdomGraphicAtlasData.Layer.values().length)
            throw new IllegalArgumentException("Invalid atlas response scale or layer");
        int count = buf.readVarInt();
        if (count < 0 || count > KingdomGraphicAtlasData.MAX_CELLS)
            throw new IllegalArgumentException("Oversized atlas response");
        List<KingdomGraphicAtlasData.Tile> cells = new ArrayList<>();
        for (int i = 0; i < KingdomGraphicAtlasData.MAX_CELLS; i++) cells.add(EMPTY);
        boolean[] used = new boolean[KingdomGraphicAtlasData.MAX_CELLS];
        for (int i = 0; i < count; i++) {
            int index = buf.readVarInt();
            if (index < 0 || index >= used.length || used[index])
                throw new IllegalArgumentException("Duplicate or invalid atlas tile");
            used[index] = true;
            int claims = buf.readVarInt();
            boolean own = buf.readBoolean(), mixed = buf.readBoolean(), planned = buf.readBoolean();
            int townColor = buf.readInt(), nationColor = buf.readInt();
            int businesses = buf.readVarInt();
            String town = buf.readUtf(72), nation = buf.readUtf(72), business = buf.readUtf(72);
            int terrain = buf.readUnsignedByte();
            int elevation = buf.readShort();
            if (claims < 0 || businesses < 0 || terrain >= ru.projectkingdom.map.KingdomAtlasTerrain.Type.values().length)
                throw new IllegalArgumentException("Invalid atlas tile data");
            cells.set(index, new KingdomGraphicAtlasData.Tile(claims, own, mixed, planned,
                    townColor, nationColor, businesses, town, nation, business, terrain, elevation));
        }
        return new GraphicAtlasResponsePacket(new KingdomGraphicAtlasData.Snapshot(
                request, x, z, scale, KingdomGraphicAtlasData.Layer.values()[layerId], cells));
    }

    public static void handle(GraphicAtlasResponsePacket packet, Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ru.projectkingdom.map.KingdomGraphicAtlasClient.accept(packet.snapshot)));
        ctx.setPacketHandled(true);
    }
}
