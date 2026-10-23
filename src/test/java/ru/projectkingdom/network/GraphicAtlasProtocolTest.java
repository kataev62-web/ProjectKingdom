package ru.projectkingdom.network;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import ru.projectkingdom.map.KingdomGraphicAtlasData;

class GraphicAtlasProtocolTest {
    @Test void sparseGraphicSnapshotSurvivesNetworkSerialization() {
        var tiles = new ArrayList<KingdomGraphicAtlasData.Tile>();
        for (int i=0;i<KingdomGraphicAtlasData.MAX_CELLS;i++)
            tiles.add(new KingdomGraphicAtlasData.Tile(0,false,false,false,0,0,0,"","",""));
        int position = (KingdomGraphicAtlasData.ROWS/2)*KingdomGraphicAtlasData.COLUMNS
                + KingdomGraphicAtlasData.COLUMNS/2;
        tiles.set(position,new KingdomGraphicAtlasData.Tile(1,true,false,true,23,44,2,
                "Авалон", "Держава", "Кузница",
                ru.projectkingdom.map.KingdomAtlasTerrain.Type.FOREST.ordinal(), 41));
        var original = new KingdomGraphicAtlasData.Snapshot(
                42,-123,321,4,KingdomGraphicAtlasData.Layer.INFRASTRUCTURE,tiles,
                List.of(new ru.projectkingdom.map.KingdomAtlasInfrastructure.Marker(
                        ru.projectkingdom.map.KingdomAtlasInfrastructure.Kind.TOWN_HALL, -1980, 5120,
                        "Ратуша Авалона"),
                        new ru.projectkingdom.map.KingdomAtlasInfrastructure.Marker(
                        ru.projectkingdom.map.KingdomAtlasInfrastructure.Kind.BRIDGE, -1900, 5100,
                        "Каменный мост")));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            GraphicAtlasResponsePacket.encode(new GraphicAtlasResponsePacket(original),buffer);
            var copy = GraphicAtlasResponsePacket.decode(buffer).snapshot();
            assertEquals(original,copy);
            assertTrue(copy.tiles().get(0).empty());
            assertEquals("Авалон",copy.tiles().get(position).town());
            assertEquals(41,copy.tiles().get(position).elevation());
            assertEquals(ru.projectkingdom.map.KingdomAtlasTerrain.Type.FOREST.ordinal(),
                    copy.tiles().get(position).terrain());
            assertEquals(2, copy.markers().size());
            assertEquals("Ратуша Авалона", copy.markers().get(0).label());
            assertEquals(ru.projectkingdom.map.KingdomAtlasInfrastructure.Kind.BRIDGE,
                    copy.markers().get(1).kind());
        } finally { buffer.release(); }
    }

    @Test void maliciousResponseTileCountIsRejectedBeforeAllocation() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(1);buffer.writeInt(0);buffer.writeInt(0);
            buffer.writeVarInt(1);buffer.writeByte(0);
            buffer.writeVarInt(KingdomGraphicAtlasData.MAX_CELLS+1);
            assertThrows(IllegalArgumentException.class, () -> GraphicAtlasResponsePacket.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void rejectsExcessiveInfrastructurePayload() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(1);buffer.writeInt(0);buffer.writeInt(0);
            buffer.writeVarInt(1);buffer.writeByte(0);
            buffer.writeVarInt(0); // no terrain / property tiles
            buffer.writeVarInt(ru.projectkingdom.map.KingdomAtlasInfrastructure.MAX_MARKERS + 1);
            assertThrows(IllegalArgumentException.class, () ->
                    GraphicAtlasResponsePacket.decode(buffer));
        } finally { buffer.release(); }
    }

}
