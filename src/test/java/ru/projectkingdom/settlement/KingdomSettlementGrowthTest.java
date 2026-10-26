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
    @Test void canRestoreOnlyEmptyGhostTownWithoutLosingItsIdentity() {
        var civics=new ru.projectkingdom.npc.KingdomCivicsData();
        java.util.UUID founder=java.util.UUID.randomUUID();
        var old=civics.foundTown(founder,"Город_старый");
        assertNotNull(old);
        assertTrue(civics.bootstrapEmptyLegacyTown(founder,"Новая Дубрава",
                "minecraft:overworld",5,7));
        assertEquals(old.id(),civics.townOf(founder).id());
        assertEquals("Новая Дубрава",civics.townOf(founder).name());
        assertEquals(1,civics.mapPlots("minecraft:overworld").size());
        assertEquals(old.id(),civics.mapPlots("minecraft:overworld").iterator().next().town());
        assertFalse(civics.bootstrapEmptyLegacyTown(founder,"Второй город",
                "minecraft:overworld",8,7));
        java.util.UUID second=java.util.UUID.randomUUID();
        var another=civics.foundTown(second,"Старый хутор");
        assertNotNull(another);
        assertTrue(civics.joinTown(java.util.UUID.randomUUID(),another.id()));
        assertFalse(civics.bootstrapEmptyLegacyTown(second,"Чужая территория",
                "minecraft:overworld",12,12));
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
