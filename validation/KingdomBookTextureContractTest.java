package ru.projectkingdom.core;

import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class KingdomBookTextureContractTest {
    public static void main(String[] args) throws Exception {
        var path = Path.of("src/main/resources/assets/kingdomnpc/textures/gui/player_book.png");
        var image = ImageIO.read(path.toFile());
        require(image != null, "texture is a readable PNG");
        require(image.getWidth() == KingdomBookLayout.WIDTH, "texture width matches layout");
        require(image.getHeight() == KingdomBookLayout.HEIGHT, "texture height matches layout");
        require(((image.getRGB(0, 0) >>> 24) & 0xFF) < 16, "outer corner is transparent");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
