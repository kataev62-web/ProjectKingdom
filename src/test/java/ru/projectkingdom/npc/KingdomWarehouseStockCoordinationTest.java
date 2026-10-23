package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Stage 2 regression checks: one stock projection drives work and logistics. */
final class KingdomWarehouseStockCoordinationTest {
    @Test void productionUsesHysteresisAndReservations() {
        var limits = new EnterpriseStockPolicy.Thresholds(16, 64);
        var low = new EnterpriseStockPolicy.StockState(16, 0, 0, true);
        assertTrue(EnterpriseStockPolicy.nextProduction(limits, low, false));
        assertTrue(EnterpriseStockPolicy.nextProduction(limits,
                new EnterpriseStockPolicy.StockState(40, 0, 0, true), true));
        assertFalse(EnterpriseStockPolicy.nextProduction(limits,
                new EnterpriseStockPolicy.StockState(40, 0, 0, true), false));
        assertFalse(EnterpriseStockPolicy.nextProduction(limits,
                new EnterpriseStockPolicy.StockState(40, 12, 12, true), true));
        assertEquals(2, EnterpriseStockPolicy.productionBatch(limits,
                new EnterpriseStockPolicy.StockState(60, 0, 2, true), true, 4));
    }

    @Test void oneForecastDrivesOrdersWithoutDoublePurchasing() {
        var limits = new EnterpriseStockPolicy.Thresholds(20, 100);
        var key = new EnterpriseDemandBoard.Key(UUID.randomUUID(), "minecraft:charcoal");
        var stock = new EnterpriseStockPolicy.StockState(15, 40, 5, true);
        assertEquals(40, EnterpriseStockPolicy.reorderQuantity(limits, stock));
        var listing = EnterpriseDemandBoard.refresh(null, key, limits, stock, 10);
        assertNotNull(listing);
        assertEquals(40, listing.remaining());
        assertEquals(listing.listingId(), EnterpriseDemandBoard.refresh(listing, key,
                limits, new EnterpriseStockPolicy.StockState(15, 50, 5, true), 10).listingId());
        assertEquals(30, EnterpriseDemandBoard.refresh(listing, key, limits,
                new EnterpriseStockPolicy.StockState(15, 50, 5, true), 10).remaining());
        assertNull(EnterpriseDemandBoard.refresh(listing, key, limits,
                new EnterpriseStockPolicy.StockState(15, 80, 5, true), 10));
    }

    @Test void unknownWarehouseDoesNotCancelExistingDemandOrCreateMore() {
        var limits = new EnterpriseStockPolicy.Thresholds(8, 32);
        var key = new EnterpriseDemandBoard.Key(UUID.randomUUID(), "minecraft:iron_ingot");
        var known = new EnterpriseStockPolicy.StockState(5, 0, 0, true);
        var old = EnterpriseDemandBoard.refresh(null, key, limits, known, 2);
        var unknown = new EnterpriseStockPolicy.StockState(0, 0, 0, false);
        assertSame(old, EnterpriseDemandBoard.refresh(old, key, limits, unknown, 2));
        assertNull(EnterpriseDemandBoard.refresh(null, key, limits, unknown, 2));
        assertEquals(0, KingdomWarehouseStockCoordinator.needed(limits, unknown));
        assertFalse(EnterpriseStockPolicy.nextProduction(limits, unknown, true));
        assertThrows(IllegalArgumentException.class, () ->
                EnterpriseDemandBoard.refresh(old,
                    new EnterpriseDemandBoard.Key(UUID.randomUUID(), "minecraft:iron_ingot"),
                    limits, unknown, 2));
    }

    @Test void previouslyPublishedDemandPreservesLegacyCompatibility() {
        var limits = new EnterpriseStockPolicy.Thresholds(20, 100);
        var key = new EnterpriseDemandBoard.Key(UUID.randomUUID(), "minecraft:bread");
        var legacy = EnterpriseDemandBoard.refresh(null, key, limits, 15, 40, 10);
        var shared = EnterpriseDemandBoard.refresh(null, key, limits,
                new EnterpriseStockPolicy.StockState(15, 40, 0, true), 10);
        assertEquals(legacy.remaining(), shared.remaining());
        assertEquals(45, shared.remaining());
    }

    @Test void stockLimitsAndDemandNeverOverflow() {
        var limits = new EnterpriseStockPolicy.Thresholds(8, 32);
        var fullyCommitted = new EnterpriseStockPolicy.StockState(
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, true);
        assertEquals(Integer.MAX_VALUE, fullyCommitted.committedTotal());
        assertEquals(0, EnterpriseStockPolicy.reorderQuantity(limits, fullyCommitted));
        assertEquals(0, EnterpriseStockPolicy.productionBatch(limits, fullyCommitted, true, 4));
    }
}
