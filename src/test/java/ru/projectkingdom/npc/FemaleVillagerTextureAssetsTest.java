package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashSet;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Regression: old skin_3.png was corrupt (magenta/black missing texture);
 * other old skins had blank head UVs that made the women's faces disappear.
 * All skins use the unchanged vanilla VillagerModel 64x64 UV atlas.
 */
final class FemaleVillagerTextureAssetsTest {
    private static BufferedImage load(String name) throws Exception {
        try (InputStream in = FemaleVillagerTextureAssetsTest.class
                .getResourceAsStream("/assets/kingdomnpc/textures/entity/" + name)) {
            assertNotNull(in, "Missing NPC texture: " + name);
            BufferedImage png = ImageIO.read(in);
            assertNotNull(png, "Corrupt PNG: " + name);
            assertEquals(64, png.getWidth(), name + " width");
            assertEquals(64, png.getHeight(), name + " height");
            return png;
        }
    }

    @Test
    void fourFemaleSkinsHaveOpaqueVillagerFacesAndVisibleEyes() throws Exception {
        var appearances = new HashSet<Integer>();
        for (int i = 0; i < 4; i++) {
            String name = "female_skin_" + i + ".png";
            var png = load(name);
            int blues = 0;
            for (int y = 8; y < 18; y++) {
                for (int x = 8; x < 16; x++) {
                    int pixel = png.getRGB(x, y);
                    assertEquals(255, (pixel >>> 24) & 255,
                            name + " transparent head-front UV: " + x + "," + y);
                    int r = (pixel >>> 16) & 255;
                    int g = (pixel >>> 8) & 255;
                    int b = pixel & 255;
                    assertFalse(r > 245 && g < 12 && b > 245,
                            name + " contains missing-texture magenta");
                    if (b > r * 1.6 && b > g * 1.2) blues++;
                }
            }
            assertTrue(blues >= 2, name + " missing blue eyes");
            appearances.add(png.getRGB(9, 3));
        }
        assertEquals(4, appearances.size(), "Female hair variants must differ");
    }

    @Test
    void extraGeometryAlwaysHasNontransparentSkirtAndBlouseTexture() throws Exception {
        for (int i = 0; i < 4; i++) {
            String name = "female_outfit_" + i + ".png";
            var png = load(name);
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    assertEquals(255, (png.getRGB(x, y) >>> 24) & 255,
                            name + " unexpected alpha at " + x + "," + y);
                }
            }
        }
    }
}
