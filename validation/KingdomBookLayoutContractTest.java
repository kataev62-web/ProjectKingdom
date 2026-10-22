package ru.projectkingdom.core;

public final class KingdomBookLayoutContractTest {
    public static void main(String[] args) {
        var tabs = KingdomBookLayout.tabs();
        require(tabs.size() == 6, "six navigation tabs");
        require(tabs.get(0).equals(new KingdomBookLayout.Rect(3, 42, 25, 24)), "first tab bounds");
        require(tabs.get(5).equals(new KingdomBookLayout.Rect(3, 182, 25, 24)), "last tab bounds");
        for (int i = 1; i < tabs.size(); i++) {
            require(tabs.get(i - 1).bottom() < tabs.get(i).y(), "tabs do not overlap");
        }

        var leftPage = KingdomBookLayout.leftPage();
        var rightPage = KingdomBookLayout.rightPage();
        require(leftPage.equals(new KingdomBookLayout.Rect(36, 24, 132, 172)), "left page bounds");
        require(rightPage.equals(new KingdomBookLayout.Rect(185, 24, 137, 172)), "right page bounds");
        require(tabs.stream().allMatch(tab -> tab.right() < leftPage.x()), "navigation is separate");
        require(leftPage.right() < rightPage.x(), "pages are separate");
        require(KingdomBookLayout.WIDTH == 352, "screen width");
        require(KingdomBookLayout.HEIGHT == 220, "screen height");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
