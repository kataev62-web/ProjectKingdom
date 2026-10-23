package ru.projectkingdom.map;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Existing Kingdom paper maps are not deleted from worlds, but NEW navigation
 * happens in the native Kingdom management GUI (MineColonies-style).
 */
class KingdomHeldMapSourceTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+path));
    }

    @Test void legacyTaggedMapsRemainReadableWithoutOverwritingVanillaMaps() throws Exception {
        String old = source("map/KingdomHeldMapService.java");
        assertTrue(old.contains("stack.is(Items.FILLED_MAP) && stack.hasTag()"));
        assertTrue(old.contains("stack.getTag().getBoolean(ITEM_TAG)"));
        assertTrue(old.contains("KingdomAtlasNavigator.plan("));
        assertFalse(old.contains("getChunkSource().getChunk("));
    }

    @Test void noNewPhysicalMapsOrBackgroundHandPaintingInKingdomWorkflow() throws Exception {
        String menu=source("npc/KingdomMainMenu.java");
        String mod=source("npc/KingdomNpcMod.java");
        String screen=source("map/KingdomGraphicAtlasScreen.java");
        assertFalse(menu.contains("KingdomHeldMapService.issue(viewer)"));
        assertFalse(mod.contains("KingdomHeldMapService.tick(player)"));
        assertFalse(screen.contains("new SelectHeldMapDestinationPacket("));
        assertTrue(menu.contains("new ru.projectkingdom.network.OpenGraphicAtlasPacket("));
        assertTrue(screen.contains("KingdomAtlasNavigator.plan("));
    }
}
