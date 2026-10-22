package ru.projectkingdom.core;

public final class KingdomBookLayoutContractTest {
    public static void main(String[] args) {
        var tabs = KingdomBookLayout.tabs();
        require(tabs.size() == 6, "six navigation tabs");
        require(tabs.get(0).equals(new KingdomBookLayout.Rect(14, 42, 106, 23)), "first tab bounds");
        require(tabs.get(5).equals(new KingdomBookLayout.Rect(14, 182, 106, 23)), "last tab bounds");
        for (int i = 1; i < tabs.size(); i++) {
            require(tabs.get(i - 1).bottom() < tabs.get(i).y(), "tabs do not overlap");
        }

        var content = KingdomBookLayout.contentPanel();
        require(content.equals(new KingdomBookLayout.Rect(128, 42, 198, 163)), "content panel bounds");
        require(tabs.stream().allMatch(tab -> tab.right() < content.x()), "navigation is separate");
        require(KingdomBookLayout.WIDTH == 340, "screen width");
        require(KingdomBookLayout.HEIGHT == 220, "screen height");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
