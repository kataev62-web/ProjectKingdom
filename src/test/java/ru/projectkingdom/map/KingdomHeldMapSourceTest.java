package ru.projectkingdom.map;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Regression contracts for actual in-hand map, vanilla isolation and material cost. */
class KingdomHeldMapSourceTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+path));
    }

    @Test void onlyKingdomTaggedFilledMapsArePainted() throws Exception {
        String s=source("map/KingdomHeldMapService.java");
        assertTrue(s.contains("stack.is(Items.FILLED_MAP) && stack.hasTag()"));
        assertTrue(s.contains("stack.getTag().getBoolean(ITEM_TAG)"));
        assertTrue(s.contains("MapItem.create(level"));
        assertTrue(s.contains("level.setMapData(MapItem.makeKey(id),data.locked())"));
        assertTrue(s.contains("data.setColor("));
        assertTrue(s.contains("KingdomAtlasNavigator.plan("));
        assertTrue(s.contains("MapDecoration.Type.RED_X"));
        assertFalse(s.contains("getChunkSource().getChunk("));
    }

    @Test void physicalMapRequiresRealPaperAndUsesServerHandUpdate() throws Exception {
        String s=source("map/KingdomHeldMapService.java");
        String main=source("npc/KingdomNpcMod.java");
        assertTrue(s.contains("consumePaper(player)"));
        assertTrue(s.contains("stack.shrink(1)"));
        assertTrue(main.contains("KingdomHeldMapService.tick(player)"));
        assertTrue(s.contains("getMainHandItem()"));
        assertTrue(s.contains("getOffhandItem()"));
    }

    @Test void mapsMenuOffersOnePhysicalCopyAndWaypointUsesNoClientAuthority() throws Exception {
        String menu=source("npc/KingdomMainMenu.java");
        String packet=source("network/SelectHeldMapDestinationPacket.java");
        String screen=source("map/KingdomGraphicAtlasScreen.java");
        assertTrue(menu.contains("KingdomHeldMapService.issue(viewer)"));
        assertTrue(screen.contains("new SelectHeldMapDestinationPacket("));
        assertTrue(packet.contains("KingdomHeldMapService.selectDestination(player,"));
        assertFalse(packet.contains("readUUID("));
    }
}
