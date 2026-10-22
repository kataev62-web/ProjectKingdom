import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Deterministic 16x16 original pixel art for Project Kingdom currency. */
public final class EconomyTextureGenerator {
    private EconomyTextureGenerator() {}

    public static void main(String[] args) throws Exception {
        Path output = args.length == 0
                ? Path.of("src/main/resources/assets/kingdomnpc/textures/item") : Path.of(args[0]);
        Files.createDirectories(output);
        coin(output.resolve("copper_coin.png"), 0xFF5B2C, 0xC66B32, 0xF2A45F, 'C');
        coin(output.resolve("silver_coin.png"), 0x59636D, 0xAEB8C2, 0xEEF4F7, 'S');
        coin(output.resolve("gold_coin.png"), 0x8B5A12, 0xD69B22, 0xFFE17A, 'G');
        purse(output.resolve("wallet.png"));
    }

    private static void coin(Path file, int dark, int base, int light, char mark) throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(dark)); g.fillRect(4, 1, 8, 1); g.fillRect(2, 3, 12, 10); g.fillRect(4, 14, 8, 1); g.fillRect(1, 5, 14, 6);
        g.setColor(new Color(base)); g.fillRect(4, 2, 8, 12); g.fillRect(2, 5, 12, 6);
        g.setColor(new Color(light)); g.fillRect(5, 3, 6, 1); g.fillRect(3, 5, 2, 5); g.fillRect(5, 5, 1, 1);
        g.setColor(new Color(dark));
        if (mark == 'C') { g.fillRect(6, 6, 1, 5); g.fillRect(7, 5, 4, 1); g.fillRect(7, 11, 4, 1); }
        if (mark == 'S') { g.fillRect(6, 5, 5, 1); g.fillRect(5, 6, 1, 2); g.fillRect(6, 8, 4, 1); g.fillRect(10, 9, 1, 2); g.fillRect(5, 11, 5, 1); }
        if (mark == 'G') { g.fillRect(6, 5, 5, 1); g.fillRect(5, 6, 1, 5); g.fillRect(6, 11, 5, 1); g.fillRect(9, 8, 2, 3); g.fillRect(8, 8, 1, 1); }
        g.dispose(); ImageIO.write(image, "PNG", file.toFile());
    }

    private static void purse(Path file) throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Color outline = new Color(0x4A2819), leather = new Color(0x98562F), light = new Color(0xD18A50);
        g.setColor(outline); g.fillRect(5, 1, 6, 2); g.fillRect(3, 4, 10, 2); g.fillRect(2, 7, 12, 6); g.fillRect(4, 13, 8, 2);
        g.setColor(leather); g.fillRect(5, 3, 6, 1); g.fillRect(4, 6, 8, 1); g.fillRect(3, 8, 10, 5); g.fillRect(5, 13, 6, 1);
        g.setColor(light); g.fillRect(4, 8, 2, 4); g.fillRect(6, 7, 4, 1);
        g.setColor(new Color(0xE0B067)); g.fillRect(2, 5, 12, 1); g.fillRect(4, 4, 1, 3); g.fillRect(11, 4, 1, 3);
        g.dispose(); ImageIO.write(image, "PNG", file.toFile());
    }
}
