package ru.projectkingdom.map;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * All map entry points belong to the Kingdom menu, not vanilla map item
 * interactions. Purchasing land stays in authoritative city administration.
 */
class KingdomMapNavigationSourceTest {
    @Test void mapsHubContainsEveryMapType() throws Exception {
        String menu = Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/npc/KingdomMainMenu.java"));
        assertTrue(menu.contains("public static void openMaps(ServerPlayer player)"));
        assertTrue(menu.contains("new ru.projectkingdom.network.OpenGraphicAtlasPacket("));
        assertTrue(menu.contains("case MAPS -> {"));
        assertTrue(menu.contains("KingdomGraphicAtlasData.Layer.LAND"));
        assertTrue(menu.contains("KingdomGraphicAtlasData.Layer.POLITICAL"));
        assertTrue(menu.contains("KingdomGraphicAtlasData.Layer.ENTERPRISES"));
        assertTrue(menu.contains("KingdomCityExpansionMapMenu.open(viewer)"));
        assertTrue(menu.contains("KingdomLandMapMenu.open(viewer)"));
        assertFalse(menu.contains("if(!old.claim(viewer.serverLevel()"));
    }

    @Test void playerBookUsesCentralKingdomMapMenu() throws Exception {
        String client = Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/core/KingdomBookScreen.java"));
        String packet = Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/network/OpenKingdomMapsFromBookPacket.java"));
        assertTrue(client.contains("OpenKingdomMapsFromBookPacket"));
        assertTrue(packet.contains("KingdomMainMenu.openMaps(player)"));
        assertTrue(packet.contains("player.containerMenu instanceof KingdomBookMenu"));
    }

    @Test void graphicAtlasOffersNavigationAndAdministrativeInspections() throws Exception {
        String screen = Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/map/KingdomGraphicAtlasScreen.java"));
        assertTrue(screen.contains("renderKingdomInspector(gui)"));
        assertTrue(screen.contains("OpenLandMapFromAtlasPacket"));
        assertTrue(screen.contains("OpenCityPlanFromAtlasPacket"));
        assertTrue(screen.contains("KingdomAtlasNavigator.plan("));
    }

    @Test void vanillaMapUseIsNotIntercepted() throws Exception {
        String mod = Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/npc/KingdomNpcMod.java"));
        assertFalse(mod.contains("openAtlasFromMap"));
        assertFalse(mod.contains("event.getItemStack().is(Items.FILLED_MAP)"));
    }

    @Test void graphicAtlasGoesBackToKingdomHub() throws Exception {
        String screen = Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/map/KingdomGraphicAtlasScreen.java"));
        String packet = Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/network/OpenKingdomMapsHubPacket.java"));
        assertTrue(screen.contains("OpenKingdomMapsHubPacket"));
        assertTrue(packet.contains("KingdomMainMenu.open(sender)"));
    }
}
