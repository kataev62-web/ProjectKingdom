package ru.projectkingdom.network;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
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
                "Авалон", "Держава", "Кузница"));
        var original = new KingdomGraphicAtlasData.Snapshot(
                42,-123,321,4,KingdomGraphicAtlasData.Layer.POLITICAL,tiles);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            GraphicAtlasResponsePacket.encode(new GraphicAtlasResponsePacket(original),buffer);
            var copy = GraphicAtlasResponsePacket.decode(buffer).snapshot();
            assertEquals(original,copy);
            assertTrue(copy.tiles().get(0).empty());
            assertEquals("Авалон",copy.tiles().get(position).town());
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
}
