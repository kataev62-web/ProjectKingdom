package ru.projectkingdom.settlement;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class KingdomSettlementGrowthTest {
    @Test void fourRanksRequireRealLandAndFinishedBuildings() {
        assertEquals(KingdomSettlementGrowth.Rank.HAMLET,KingdomSettlementGrowth.rank(1,0));
        assertEquals(KingdomSettlementGrowth.Rank.HAMLET,KingdomSettlementGrowth.rank(4,1));
        assertEquals(KingdomSettlementGrowth.Rank.VILLAGE,KingdomSettlementGrowth.rank(4,2));
        assertEquals(KingdomSettlementGrowth.Rank.VILLAGE,KingdomSettlementGrowth.rank(12,4));
        assertEquals(KingdomSettlementGrowth.Rank.SETTLEMENT,KingdomSettlementGrowth.rank(12,5));
        assertEquals(KingdomSettlementGrowth.Rank.SETTLEMENT,KingdomSettlementGrowth.rank(32,11));
        assertEquals(KingdomSettlementGrowth.Rank.CITY,KingdomSettlementGrowth.rank(32,12));
        assertEquals("Город",KingdomSettlementGrowth.Rank.CITY.title());
    }
    @Test void foundationNamesAreValidated() {
        assertNull(KingdomSettlementFounding.cleanName(""));
        assertNull(KingdomSettlementFounding.cleanName("  "));
        assertNull(KingdomSettlementFounding.cleanName("Bad§3"));
        assertNull(KingdomSettlementFounding.cleanName("a".repeat(33)));
        assertNull(KingdomSettlementFounding.cleanName("Bad\nName"));
        assertEquals("Новая Дубрава",KingdomSettlementFounding.cleanName("  Новая  Дубрава  "));
    }
}
