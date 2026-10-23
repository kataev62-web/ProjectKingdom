package ru.projectkingdom.map;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Catch missing or corrupt JAR-embedded map art before release. */
class KingdomAtlasSkinAssetsTest {
    private static final Path GUI = Path.of(
            "src/main/resources/assets/kingdomnpc/textures/gui");

    @Test void fourOriginalPixelTexturesAreValidPngFiles() throws Exception {
        for(var name:new String[]{
                "atlas_wood.png","atlas_parchment.png","atlas_panel.png","atlas_button.png"}) {
            var path=GUI.resolve(name);
            assertTrue(Files.exists(path), "Missing "+name);
            try(var in=Files.newInputStream(path)) {
                var png=ImageIO.read(in);
                assertNotNull(png,"Not a PNG: "+name);
                assertEquals(64,png.getWidth());
                assertEquals(name.contains("button")?20:64,png.getHeight());
                assertTrue(png.getRGB(32,png.getHeight()/2)!=0,"Blank texture");
            }
        }
    }

    @Test void customKingdomButtonsUseItemIconsAndTooltips() throws Exception {
        String skin=Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/map/KingdomAtlasSkin.java"));
        String screen=Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/map/KingdomGraphicAtlasScreen.java"));
        assertTrue(skin.contains("class AtlasButton extends AbstractButton"));
        assertTrue(skin.contains("setTooltip(Tooltip.create("));
        assertTrue(skin.contains("gui.renderItem(icon,"));
        assertTrue(skin.contains("defaultButtonNarrationText(narration)"));
        assertTrue(screen.contains("new KingdomAtlasSkin.AtlasButton("));
        assertTrue(screen.contains("KingdomAtlasSkin.mapBezel("));
        assertFalse(screen.contains("Button.builder("));
    }
}
