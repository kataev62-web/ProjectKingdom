package ru.projectkingdom.npc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PhysicalCoinSelectionTest {
    @Test void hundredsOfCopperAreNotRemintedAsSilver() {
        var selected=PhysicalCoinSelection.select(100,0,0,100);
        assertNotNull(selected);
        assertEquals(0,selected.silver());
        assertEquals(100,selected.copper());
        assertEquals(100,selected.copperValue());
    }

    @Test void existingSilverIsPreferredWhenActuallyPresent() {
        var selected=PhysicalCoinSelection.select(100,0,1,100);
        assertNotNull(selected);
        assertEquals(1,selected.silver());
        assertEquals(0,selected.copper());
    }

    @Test void mixedCoinsPayExactSumWithoutVirtualChange() {
        var selected=PhysicalCoinSelection.select(10_201,2,3,5);
        assertEquals(new PhysicalCoinSelection.Coins(1,2,1),selected);
        assertEquals(10_201,selected.copperValue());
    }

    @Test void tooLargeDenominationCannotBeBrokenForSmallerPayment() {
        assertNull(PhysicalCoinSelection.select(100,1,0,0));
    }

    @Test void invalidAmountsOrCountsFailClosed() {
        assertNull(PhysicalCoinSelection.select(0,0,0,1));
        assertNull(PhysicalCoinSelection.select(1_000_001,100,100,100));
        assertNull(PhysicalCoinSelection.select(100,0,-1,100));
    }
}
