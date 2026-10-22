package ru.projectkingdom.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class KingdomBookTextureTest {
    @Test
    void bookTextureMatchesTheScreenAndKeepsTransparentCorners() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/assets/kingdomnpc/textures/gui/player_book.png")) {
            assertNotNull(stream, "book texture must be packaged as a resource");
            BufferedImage image = ImageIO.read(stream);
            assertEquals(KingdomBookLayout.WIDTH, image.getWidth());
            assertEquals(KingdomBookLayout.HEIGHT, image.getHeight());
            assertTrue(((image.getRGB(0, 0) >>> 24) & 0xFF) < 16,
                    "the outer corner must stay transparent");
        }
    }
}
