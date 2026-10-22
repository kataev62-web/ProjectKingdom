package ru.projectkingdom.core;

import java.util.List;
import java.util.stream.IntStream;

/** Pixel-perfect layout shared by the Kingdom book screen and its tests. */
public final class KingdomBookLayout {
    public static final int WIDTH = 340;
    public static final int HEIGHT = 220;

    private static final int TAB_X = 14;
    private static final int TAB_Y = 42;
    private static final int TAB_WIDTH = 106;
    private static final int TAB_HEIGHT = 23;
    private static final int TAB_STEP = 28;
    private static final Rect CONTENT_PANEL = new Rect(128, 42, 198, 163);

    private KingdomBookLayout() {}

    public static List<Rect> tabs() {
        return IntStream.range(0, BookModel.Section.values().length)
                .mapToObj(index -> new Rect(TAB_X, TAB_Y + index * TAB_STEP, TAB_WIDTH, TAB_HEIGHT))
                .toList();
    }

    public static Rect contentPanel() {
        return CONTENT_PANEL;
    }

    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width - 1; }
        public int bottom() { return y + height - 1; }
    }
}
