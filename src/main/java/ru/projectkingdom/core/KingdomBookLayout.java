package ru.projectkingdom.core;

import java.util.List;
import java.util.stream.IntStream;

/** Pixel-perfect layout shared by the Kingdom book screen and its tests. */
public final class KingdomBookLayout {
    public static final int WIDTH = 352;
    public static final int HEIGHT = 220;

    private static final int TAB_X = 3;
    private static final int TAB_Y = 42;
    private static final int TAB_WIDTH = 25;
    private static final int TAB_HEIGHT = 24;
    private static final int TAB_STEP = 28;
    private static final Rect LEFT_PAGE = new Rect(36, 24, 132, 172);
    private static final Rect RIGHT_PAGE = new Rect(185, 24, 137, 172);

    private KingdomBookLayout() {}

    public static List<Rect> tabs() {
        return IntStream.range(0, BookModel.Section.values().length)
                .mapToObj(index -> new Rect(TAB_X, TAB_Y + index * TAB_STEP, TAB_WIDTH, TAB_HEIGHT))
                .toList();
    }

    public static Rect leftPage() {
        return LEFT_PAGE;
    }

    public static Rect rightPage() {
        return RIGHT_PAGE;
    }

    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width - 1; }
        public int bottom() { return y + height - 1; }
    }
}
