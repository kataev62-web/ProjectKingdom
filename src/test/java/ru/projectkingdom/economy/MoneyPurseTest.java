package ru.projectkingdom.economy;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class MoneyPurseTest {
    @Test void countsThreeDenominationsAndCopperValue() {
        MoneyPurse purse = new MoneyPurse(40, 12, 3, false);
        assertEquals(55, purse.coinCount());
        assertEquals(31_240, purse.valueCopper());
    }

    @Test void rejectsMoreThanOneThousandPhysicalCoins() {
        MoneyPurse purse = MoneyPurse.empty().add(MoneyDenomination.COPPER, 1000);
        assertEquals(1000, purse.coinCount());
        assertFalse(purse.canAccept(MoneyDenomination.SILVER, 1));
        assertThrows(IllegalArgumentException.class,
                () -> purse.add(MoneyDenomination.SILVER, 1));
    }

    @Test void migrationUsesLargestCoinsAndPreservesValue() {
        MoneyPurse purse = LegacyPurseMigration.fromCopperValue(1_234_567);
        assertEquals(1_234_567, purse.valueCopper());
        assertEquals(123, purse.gold());
        assertEquals(45, purse.silver());
        assertEquals(67, purse.copper());
        assertFalse(purse.legacyOverflow());
    }

    @Test void legacyOverflowIsPreservedInsteadOfTruncated() {
        MoneyPurse purse = LegacyPurseMigration.fromCopperValue(10_000_001);
        assertEquals(10_000_001, purse.valueCopper());
        assertEquals(1001, purse.coinCount());
        assertTrue(purse.legacyOverflow());
        assertFalse(purse.canAccept(MoneyDenomination.COPPER, 1));
    }
}
