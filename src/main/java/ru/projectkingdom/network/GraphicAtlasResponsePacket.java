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
        // Registry-based buildings and businesses are sparse and bounded.
        buf.writeVarInt(view.markers().size());
        for (var marker : view.markers()) {
            buf.writeByte(marker.kind().ordinal());
            buf.writeInt(marker.blockX());
            buf.writeInt(marker.blockZ());
            buf.writeUtf(marker.label(), 72);
        }
        buf.writeVarInt(view.outlines().size());
        for (var outline : view.outlines()) {
            buf.writeByte(outline.kind().ordinal());
            buf.writeInt(outline.minX()); buf.writeInt(outline.minZ());
            buf.writeInt(outline.maxX()); buf.writeInt(outline.maxZ());
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
        int markerCount = buf.readVarInt();
        if (markerCount < 0 || markerCount > ru.projectkingdom.map.KingdomAtlasInfrastructure.MAX_MARKERS)
            throw new IllegalArgumentException("Oversized infrastructure list");
        List<ru.projectkingdom.map.KingdomAtlasInfrastructure.Marker> markers = new ArrayList<>(markerCount);
        for (int i = 0; i < markerCount; i++) {
            int kind = buf.readUnsignedByte();
            if (kind >= ru.projectkingdom.map.KingdomAtlasInfrastructure.Kind.values().length)
                throw new IllegalArgumentException("Invalid map infrastructure kind");
            int px = buf.readInt(), pz = buf.readInt();
            String label = buf.readUtf(72);
            markers.add(new ru.projectkingdom.map.KingdomAtlasInfrastructure.Marker(
                    ru.projectkingdom.map.KingdomAtlasInfrastructure.Kind.values()[kind], px, pz, label));
        }
        int outlineCount = buf.readVarInt();
        if (outlineCount < 0 || outlineCount > ru.projectkingdom.map.KingdomAtlasBuildingOutlines.MAX_OUTLINES)
            throw new IllegalArgumentException("Oversized building outlines");
        List<ru.projectkingdom.map.KingdomAtlasBuildingOutlines.Outline> outlines = new ArrayList<>(outlineCount);
        for (int i = 0; i < outlineCount; i++) {
            int kind = buf.readUnsignedByte();
            if (kind >= ru.projectkingdom.map.KingdomAtlasInfrastructure.Kind.values().length)
                throw new IllegalArgumentException("Invalid outline type");
            outlines.add(new ru.projectkingdom.map.KingdomAtlasBuildingOutlines.Outline(
                    ru.projectkingdom.map.KingdomAtlasInfrastructure.Kind.values()[kind],
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));
        }
        return new GraphicAtlasResponsePacket(new KingdomGraphicAtlasData.Snapshot(
                request, x, z, scale, KingdomGraphicAtlasData.Layer.values()[layerId], cells, markers, outlines));
    }

    public static void handle(GraphicAtlasResponsePacket packet, Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ru.projectkingdom.map.KingdomGraphicAtlasClient.accept(packet.snapshot)));
        ctx.setPacketHandled(true);
    }
}
