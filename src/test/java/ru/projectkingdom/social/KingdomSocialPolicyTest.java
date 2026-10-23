package ru.projectkingdom.social;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomSocialPolicyTest {
    @Test void companyAndWorkerLimitsNeverExceedPolicy() {
        assertFalse(KingdomSocialPolicy.canOpenEnterprise(KingdomSocialRank.COMMONER, 1));
        assertTrue(KingdomSocialPolicy.canOpenEnterprise(KingdomSocialRank.FREE_CITIZEN, 1));
        assertFalse(KingdomSocialPolicy.canOpenEnterprise(KingdomSocialRank.SOVEREIGN, 16));
        assertFalse(KingdomSocialPolicy.canHirePersonalWorker(KingdomSocialRank.WEALTHY_CITIZEN, 12));
        assertTrue(KingdomSocialPolicy.canHirePersonalWorker(KingdomSocialRank.WEALTHY_CITIZEN, 11));
        assertFalse(KingdomSocialPolicy.canHirePersonalWorker(KingdomSocialRank.COMMONER, -1));
    }
    @Test void landAndMunicipalRightsUnlockInCorrectOrder() {
        assertFalse(KingdomSocialPolicy.canPurchasePlot(KingdomSocialRank.COMMONER, 0));
        assertTrue(KingdomSocialPolicy.canPurchasePlot(KingdomSocialRank.FREE_CITIZEN, 0));
        assertFalse(KingdomSocialPolicy.canPurchasePlot(KingdomSocialRank.FREE_CITIZEN, 1));
        assertFalse(KingdomSocialPolicy.mayHoldAdministrativeOffice(KingdomSocialRank.FREE_CITIZEN));
        assertTrue(KingdomSocialPolicy.mayHoldAdministrativeOffice(KingdomSocialRank.WEALTHY_CITIZEN));
    }
    @Test void onlyOneSettlementCanBeFounded() {
        assertFalse(KingdomSocialPolicy.canFoundSettlement(KingdomSocialRank.HONORED_CITIZEN, false));
        assertTrue(KingdomSocialPolicy.canFoundSettlement(KingdomSocialRank.LANDOWNER, false));
        assertFalse(KingdomSocialPolicy.canFoundSettlement(KingdomSocialRank.SOVEREIGN, true));
    }
    @Test void allTiersMonotonicAndCappedAtSixteenFirms() {
        int enterprises=0, workers=0, plots=0;
        for (KingdomSocialRank rank : KingdomSocialRank.values()) {
            var p=KingdomSocialPolicy.privileges(rank);
            assertTrue(p.maxEnterprises()>=enterprises);
            assertTrue(p.maxPersonalWorkers()>=workers);
            assertTrue(p.maxPurchasedPlots()>=plots);
            assertTrue(p.maxEnterprises()<=16);
            assertEquals(rank, KingdomSocialRank.fromLevel(rank.level()));
            enterprises=p.maxEnterprises();workers=p.maxPersonalWorkers();plots=p.maxPurchasedPlots();
        }
        assertEquals(KingdomSocialRank.COMMONER,KingdomSocialRank.fromLevel(-1));
    }
}
