package ru.projectkingdom.npc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomMineSupportSafetyTest {
    @Test void onlyNativeTfcHorizontalSupportBlocksCountAsCollapseSupports() {
        assertTrue(KingdomMineSupportSafety.isHorizontalSupportId("tfc:wood/horizontal_support/oak"));
        assertTrue(KingdomMineSupportSafety.isHorizontalSupportId("tfc:wood/horizontal_support/ash"));
        assertFalse(KingdomMineSupportSafety.isHorizontalSupportId("tfc:wood/vertical_support/oak"));
        assertFalse(KingdomMineSupportSafety.isHorizontalSupportId("minecraft:oak_fence"));
        assertFalse(KingdomMineSupportSafety.isHorizontalSupportId(null));
    }
}
