package ru.projectkingdom.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class KingdomBookLayoutTest {
    @Test
    void createsSixNonOverlappingNavigationTabs() {
        var tabs = KingdomBookLayout.tabs();

        assertEquals(6, tabs.size());
        assertEquals(new KingdomBookLayout.Rect(3, 42, 25, 24), tabs.get(0));
        assertEquals(new KingdomBookLayout.Rect(3, 182, 25, 24), tabs.get(5));
        for (int i = 1; i < tabs.size(); i++) {
            assertTrue(tabs.get(i - 1).bottom() < tabs.get(i).y());
        }
    }

    @Test
    void laysOutTwoSeparatePagesInsideTheBook() {
        var leftPage = KingdomBookLayout.leftPage();
        var rightPage = KingdomBookLayout.rightPage();

        assertEquals(new KingdomBookLayout.Rect(36, 24, 132, 172), leftPage);
        assertEquals(new KingdomBookLayout.Rect(185, 24, 137, 172), rightPage);
        assertTrue(KingdomBookLayout.tabs().stream().allMatch(tab -> tab.right() < leftPage.x()));
        assertTrue(leftPage.right() < rightPage.x());
        assertEquals(352, KingdomBookLayout.WIDTH);
        assertEquals(220, KingdomBookLayout.HEIGHT);
    }
}
