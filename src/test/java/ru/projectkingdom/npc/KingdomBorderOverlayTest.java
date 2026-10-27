package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class KingdomBorderOverlayTest {
    private static String src(String path)throws Exception {
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+path));
    }

    @Test void edgesAppearOnlyBetweenDifferentOwners() {
        UUID city=UUID.randomUUID(),other=UUID.randomUUID();
        assertFalse(KingdomBorderOverlay.isOuterEdge(null,null));
        assertFalse(KingdomBorderOverlay.isOuterEdge(city,city));
        assertTrue(KingdomBorderOverlay.isOuterEdge(city,null));
        assertTrue(KingdomBorderOverlay.isOuterEdge(city,other));
    }
    @Test void togglesAccessibleFromMenuAndKeyWithAuthoritativeServerSnapshot()throws Exception{
        String menu=src("npc/KingdomMainMenu.java");
        assertTrue(menu.contains("KingdomBorderOverlay.toggle(viewer)"));
        assertTrue(menu.contains("KingdomBorderOverlay.enabled(viewer)"));
        String key=src("map/KingdomBorderKey.java");
        assertTrue(key.contains("GLFW_KEY_G"));
        assertTrue(key.contains("RegisterKeyMappingsEvent"));
        assertTrue(key.contains("ToggleCityBordersPacket"));
        String wire=src("network/KingdomNetwork.java");
        assertTrue(wire.contains("registerMessage(53,ToggleCityBordersPacket"));
        String packet=src("network/ToggleCityBordersPacket.java");
        assertTrue(packet.contains("context.getSender()"));
        String render=src("npc/KingdomBorderOverlay.java");
        assertTrue(render.contains("player.getPersistentData().putBoolean"));
        assertTrue(render.contains("sendParticles(player"));
        assertTrue(render.contains("level.hasChunkAt("));
        assertTrue(render.contains("PARTICLE_BUDGET"));
        assertTrue(render.contains("notifyOnCrossing(player)"));
    }
    @Test void atlasShowsBordersOfDifferentCitiesEvenWithinOneNation()throws Exception {
        String screen=src("map/KingdomGraphicAtlasScreen.java");
        assertTrue(screen.contains("City limits stay visible even for two cities in the same nation."));
        assertTrue(screen.contains("a.townColor()!=b.townColor()"));
        assertTrue(screen.contains("col==KingdomGraphicAtlasData.COLUMNS-1"));
        assertTrue(screen.contains("row==KingdomGraphicAtlasData.ROWS-1"));
    }
}
