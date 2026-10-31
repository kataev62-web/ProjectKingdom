package ru.projectkingdom.npc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import ru.projectkingdom.enterprise.EnterpriseKind;

/** Matching a worker to the correct real company is the first safety gate. */
final class KingdomNpcQuickSetupTest {
    @Test void commonJobsDoNotAutoHireIntoUnrelatedEnterprises() {
        assertTrue(KingdomNpcQuickSetup.compatible("farmer",EnterpriseKind.FARM));
        assertTrue(KingdomNpcQuickSetup.compatible("breeder",EnterpriseKind.FARM));
        assertTrue(KingdomNpcQuickSetup.compatible("lumberjack",EnterpriseKind.LUMBER));
        assertTrue(KingdomNpcQuickSetup.compatible("miner",EnterpriseKind.MINE));
        assertTrue(KingdomNpcQuickSetup.compatible("builder",EnterpriseKind.CONSTRUCTION));
        assertTrue(KingdomNpcQuickSetup.compatible("courier",EnterpriseKind.COURIER));
        assertFalse(KingdomNpcQuickSetup.compatible("lumberjack",EnterpriseKind.FARM));
        assertFalse(KingdomNpcQuickSetup.compatible("farmer",EnterpriseKind.SMITHY));
        assertFalse(KingdomNpcQuickSetup.compatible("builder",EnterpriseKind.LUMBER));
    }
    @Test void tfcSpecialistsRequireTheCorrectPhysicalWorkshopCompany() {
        assertTrue(KingdomNpcQuickSetup.compatible("weaver",EnterpriseKind.TEXTILE));
        assertTrue(KingdomNpcQuickSetup.compatible("tanner",EnterpriseKind.TANNERY));
        assertTrue(KingdomNpcQuickSetup.compatible("baker",EnterpriseKind.BAKERY));
        assertTrue(KingdomNpcQuickSetup.compatible("cook",EnterpriseKind.BAKERY));
        assertTrue(KingdomNpcQuickSetup.compatible("potter",EnterpriseKind.POTTERY));
        assertTrue(KingdomNpcQuickSetup.compatible("smith",EnterpriseKind.SMITHY));
        assertFalse(KingdomNpcQuickSetup.compatible("tanner",EnterpriseKind.TEXTILE));
        assertFalse(KingdomNpcQuickSetup.compatible("weaver",EnterpriseKind.TANNERY));
        assertFalse(KingdomNpcQuickSetup.compatible("free",EnterpriseKind.FARM));
        assertFalse(KingdomNpcQuickSetup.compatible("hunter",EnterpriseKind.FARM));
    }
    @Test void noExecutorTradesCannotMintGoodsFromAutoSetup() {
        assertFalse(KingdomNpcQuickSetup.compatible("charcoal_burner",EnterpriseKind.LUMBER));
        assertFalse(KingdomNpcQuickSetup.compatible("smelter",EnterpriseKind.SMITHY));
        assertFalse(KingdomNpcQuickSetup.compatible("fisher",EnterpriseKind.FARM));
        assertFalse(KingdomNpcQuickSetup.compatible("guard",EnterpriseKind.HOTEL));
        assertFalse(KingdomNpcQuickSetup.compatible("free",EnterpriseKind.LEGACY));
    }
}
