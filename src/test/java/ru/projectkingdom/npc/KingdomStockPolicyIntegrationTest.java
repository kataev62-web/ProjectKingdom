package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class KingdomStockPolicyIntegrationTest {
    @Test void hysteresisUsesPhysicalAndIncomingStockTogether() {
        var limits=new EnterpriseStockPolicy.Thresholds(16,64);
        var low=new EnterpriseStockPolicy.StockState(16,0,0,true);
        assertTrue(EnterpriseStockPolicy.nextProduction(limits,low,false));
        assertTrue(EnterpriseStockPolicy.nextProduction(limits,
                new EnterpriseStockPolicy.StockState(30,0,0,true),true));
        assertFalse(EnterpriseStockPolicy.nextProduction(limits,
                new EnterpriseStockPolicy.StockState(30,0,0,true),false));
        assertFalse(EnterpriseStockPolicy.nextProduction(limits,
                new EnterpriseStockPolicy.StockState(30,34,0,true),true));
    }

    @Test void unfinishedWorkReservesCapacityForOtherWorkers() {
        var limits=new EnterpriseStockPolicy.Thresholds(8,32);
        var stock=new EnterpriseStockPolicy.StockState(27,0,4,true);
        assertEquals(1,EnterpriseStockPolicy.productionBatch(limits,stock,true,4));
        assertEquals(1,EnterpriseStockPolicy.productionBatch(limits,stock,true,2));
        assertTrue(EnterpriseStockPolicy.productionBatch(limits,stock,true,2)<2);
    }

    @Test void unloadingOneRegisteredContainerFailsClosed() {
        var limits=new EnterpriseStockPolicy.Thresholds(8,32);
        var unavailable=new EnterpriseStockPolicy.StockState(0,0,0,false);
        assertFalse(EnterpriseStockPolicy.nextProduction(limits,unavailable,false));
        assertEquals(0,EnterpriseStockPolicy.productionBatch(limits,unavailable,true,4));
        assertEquals(0,EnterpriseStockPolicy.reorderQuantity(limits,unavailable));
    }

    @Test void deliveryReservationsReduceNewPurchases() {
        var limits=new EnterpriseStockPolicy.Thresholds(20,100);
        assertEquals(85,EnterpriseStockPolicy.reorderQuantity(limits,
                new EnterpriseStockPolicy.StockState(15,0,0,true)));
        assertEquals(45,EnterpriseStockPolicy.reorderQuantity(limits,
                new EnterpriseStockPolicy.StockState(15,40,0,true)));
        assertEquals(0,EnterpriseStockPolicy.reorderQuantity(limits,
                new EnterpriseStockPolicy.StockState(15,85,0,true)));
        assertEquals(0,EnterpriseStockPolicy.reorderQuantity(
                new EnterpriseStockPolicy.Thresholds(0,100),0,0));
    }

    @Test void mathSaturatesInsteadOfOverflowing() {
        var inventory=new EnterpriseStockPolicy.StockState(Integer.MAX_VALUE,Integer.MAX_VALUE,100,true);
        assertEquals(Integer.MAX_VALUE,inventory.committedTotal());
        assertFalse(EnterpriseStockPolicy.nextProduction(
                new EnterpriseStockPolicy.Thresholds(16,64),inventory,true));
    }
}
