import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Rebuilds the original Project Kingdom open-book GUI texture. */
public final class BookTextureGenerator {
    private static final int WIDTH = 352;
    private static final int HEIGHT = 220;

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0
                ? "src/main/resources/assets/kingdomnpc/textures/gui/player_book.png"
                : args[0]);
        Files.createDirectories(output.getParent());

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        Path2D cover = path(new double[][] {
            {24, 17}, {27, 6}, {43, 5}, {164, 9}, {176, 10}, {181, 21},
            {187, 10}, {199, 9}, {330, 5}, {345, 7}, {348, 21}, {348, 200},
            {345, 213}, {329, 214}, {198, 209}, {187, 209}, {181, 198},
            {175, 209}, {163, 209}, {42, 214}, {26, 212}, {23, 199}
        });
        g.translate(2, 3);
        g.setColor(new Color(8, 5, 4, 45));
        for (int stroke = 11; stroke >= 3; stroke -= 2) {
            g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(cover);
        }
        g.translate(-2, -3);
        g.setPaint(new GradientPaint(0, 7, new Color(103, 56, 46), 0, 214, new Color(41, 19, 17)));
        g.fill(cover);
        g.setColor(new Color(26, 12, 11));
        g.setStroke(new BasicStroke(3));
        g.draw(cover);

        Path2D leftInset = page(new double[][] {
            {30, 22}, {36, 12}, {47, 11}, {161, 15}, {173, 16}, {179, 27},
            {179, 194}, {171, 202}, {160, 202}, {45, 207}, {32, 206}, {29, 196}
        });
        Path2D rightInset = page(new double[][] {
            {184, 27}, {190, 16}, {201, 15}, {326, 11}, {339, 12}, {342, 23},
            {342, 196}, {339, 206}, {327, 207}, {201, 202}, {190, 202}, {184, 194}
        });
        g.setColor(new Color(143, 91, 57));
        g.fill(leftInset);
        g.fill(rightInset);
        g.setColor(new Color(182, 139, 80));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(leftInset);
        g.draw(rightInset);

        Path2D leftPage = page(new double[][] {
            {35, 24}, {42, 16}, {56, 17}, {163, 20}, {173, 21}, {179, 31},
            {178, 193}, {170, 198}, {162, 197}, {51, 202}, {45, 199}, {39, 201},
            {34, 195}, {36, 187}, {32, 179}, {35, 169}, {32, 159}, {35, 149},
            {33, 137}, {36, 126}, {33, 115}, {36, 104}, {33, 92}, {36, 82},
            {34, 70}, {37, 59}, {34, 47}, {37, 37}
        });
        Path2D rightPage = page(new double[][] {
            {184, 31}, {191, 21}, {202, 20}, {321, 17}, {334, 17}, {340, 24},
            {337, 36}, {340, 47}, {337, 58}, {340, 70}, {337, 82}, {340, 94},
            {337, 105}, {340, 116}, {337, 128}, {340, 139}, {337, 150}, {340, 161},
            {337, 172}, {340, 183}, {336, 195}, {330, 201}, {323, 199}, {317, 202},
            {201, 197}, {191, 198}, {184, 193}
        });
        g.setPaint(new GradientPaint(35, 0, new Color(213, 187, 125), 178, 0, new Color(242, 229, 187)));
        g.fill(leftPage);
        g.setPaint(new GradientPaint(184, 0, new Color(188, 157, 99), 340, 0, new Color(231, 210, 155)));
        g.fill(rightPage);
        g.setColor(new Color(128, 96, 50));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(leftPage);
        g.draw(rightPage);

        g.setPaint(new GradientPaint(174, 0, new Color(119, 84, 45, 20), 181, 0, new Color(48, 29, 18, 190)));
        g.fillRect(174, 28, 8, 165);
        g.setPaint(new GradientPaint(182, 0, new Color(48, 29, 18, 185), 189, 0, new Color(250, 237, 193, 30)));
        g.fillRect(182, 28, 7, 165);

        g.setColor(new Color(145, 107, 57, 145));
        g.setStroke(new BasicStroke(1));
        corners(g, 48, 34, 115, 151);
        corners(g, 201, 34, 124, 151);

        g.setColor(new Color(126, 91, 44, 24));
        g.fillOval(51, 64, 38, 16);
        g.fillOval(132, 142, 25, 40);
        g.fillOval(280, 55, 43, 20);
        g.fillOval(207, 163, 34, 14);

        g.setColor(new Color(130, 94, 48, 62));
        g.drawArc(51, 184, 108, 12, 8, 164);
        g.drawArc(202, 184, 123, 12, 8, 164);
        g.drawArc(44, 22, 123, 12, 188, 164);
        g.drawArc(197, 22, 133, 12, 188, 164);
        g.dispose();
        ImageIO.write(image, "png", output.toFile());
    }

    private static Path2D path(double[][] points) {
        return polygon(points, true);
    }

    private static Path2D page(double[][] points) {
        return polygon(points, true);
    }

    private static Path2D polygon(double[][] points, boolean close) {
        Path2D result = new Path2D.Double();
        result.moveTo(points[0][0], points[0][1]);
        for (int i = 1; i < points.length; i++) result.lineTo(points[i][0], points[i][1]);
        if (close) result.closePath();
        return result;
    }

    private static void corners(Graphics2D g, int x, int y, int width, int height) {
        int arm = 24;
        g.drawLine(x, y, x + arm, y);
        g.drawLine(x, y, x, y + arm);
        g.drawLine(x + width, y, x + width - arm, y);
        g.drawLine(x + width, y, x + width, y + arm);
        g.drawLine(x, y + height, x + arm, y + height);
        g.drawLine(x, y + height, x, y + height - arm);
        g.drawLine(x + width, y + height, x + width - arm, y + height);
        g.drawLine(x + width, y + height, x + width, y + height - arm);
    }
}
