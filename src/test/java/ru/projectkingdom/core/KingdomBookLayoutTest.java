package ru.projectkingdom.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class KingdomBookLayoutTest {
    @Test
    void createsSixNonOverlappingNavigationTabs() {
        var tabs = KingdomBookLayout.tabs();

        assertEquals(6, tabs.size());
        assertEquals(new KingdomBookLayout.Rect(14, 42, 106, 23), tabs.get(0));
        assertEquals(new KingdomBookLayout.Rect(14, 182, 106, 23), tabs.get(5));
        for (int i = 1; i < tabs.size(); i++) {
            assertTrue(tabs.get(i - 1).bottom() < tabs.get(i).y());
        }
    }

    @Test
    void keepsTheContentPanelSeparateFromNavigation() {
        var content = KingdomBookLayout.contentPanel();

        assertEquals(new KingdomBookLayout.Rect(128, 42, 198, 163), content);
        assertTrue(KingdomBookLayout.tabs().stream().allMatch(tab -> tab.right() < content.x()));
        assertEquals(340, KingdomBookLayout.WIDTH);
        assertEquals(220, KingdomBookLayout.HEIGHT);
    }
}
