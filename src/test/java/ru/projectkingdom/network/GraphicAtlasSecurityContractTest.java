package ru.projectkingdom.network;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Server ownership and per-player request bounding must survive UI refactors. */
class GraphicAtlasSecurityContractTest {
    @Test void requestsCannotSupplyOwnershipOrDimension() throws Exception {
        String source = Files.readString(Path.of("src/main/java/ru/projectkingdom/network/GraphicAtlasRequestPacket.java"));
        assertTrue(source.contains("KingdomGraphicAtlasData.fromPlayer(sender"));
        assertTrue(source.contains("now - previous < 10"));
        assertTrue(source.contains("Math.abs((long)packet.centerX) > 16384"));
        assertFalse(source.contains("readUUID("));
        assertFalse(source.contains("readUtf("));
    }
    @Test void atlasIsInformationalAndPlanningRemainsServerAuthorized() throws Exception {
        String open = Files.readString(Path.of("src/main/java/ru/projectkingdom/network/OpenCityPlanFromAtlasPacket.java"));
        String screen = Files.readString(Path.of("src/main/java/ru/projectkingdom/map/KingdomGraphicAtlasScreen.java"));
        assertTrue(open.contains("KingdomCityExpansionMapMenu.open(player)"));
        assertTrue(screen.contains("OpenCityPlanFromAtlasPacket"));
        assertFalse(screen.contains("KingdomCurrency.debit("));
        assertFalse(screen.contains(".claim("));
    }
}
