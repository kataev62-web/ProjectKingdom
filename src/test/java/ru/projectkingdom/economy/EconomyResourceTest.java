package ru.projectkingdom.economy;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class EconomyResourceTest {
    @Test void fourOriginalItemTexturesArePackagedAtSixteenPixels() throws Exception {
        for (String id : new String[]{"copper_coin", "silver_coin", "gold_coin", "wallet"}) {
            String path = "/assets/kingdomnpc/textures/item/" + id + ".png";
            try (InputStream stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                BufferedImage image = ImageIO.read(stream);
                assertEquals(16, image.getWidth(), path);
                assertEquals(16, image.getHeight(), path);
            }
            String model = resource("/assets/kingdomnpc/models/item/" + id + ".json");
            assertTrue(model.contains("kingdomnpc:item/" + id), id + " must use its own texture");
        }
    }

    @Test void readableFontIsScopedAndLicensed() throws Exception {
        String font = resource("/assets/kingdomnpc/font/readable.json");
        assertTrue(font.contains("kingdomnpc:font/dejavu_sans.ttf"));
        assertNotNull(getClass().getResourceAsStream("/assets/kingdomnpc/font/dejavu_sans.ttf"));
        assertNotNull(getClass().getResourceAsStream("/licenses/DejaVu-Fonts-LICENSE.txt"));
        assertNull(getClass().getResourceAsStream("/assets/minecraft/font/default.json"));
    }

    private String resource(String path) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
