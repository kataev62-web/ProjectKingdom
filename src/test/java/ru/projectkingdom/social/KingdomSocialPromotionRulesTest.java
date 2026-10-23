package ru.projectkingdom.social;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomSocialPromotionRulesTest {
    private static KingdomSocialPromotionRules.Progress progress(long firms,long workers,long plots,
            boolean employed,long debt,long purse,int reputation) {
        return new KingdomSocialPromotionRules.Progress(firms,workers,plots,employed,debt,purse,reputation);
    }

    @Test void employmentOrEnterpriseRequiredForFirstPromotion() {
        var rules=KingdomSocialPromotionRules.forCurrent(KingdomSocialRank.COMMONER);
        assertEquals(100,rules.feeCopper());
        assertFalse(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.COMMONER,
                progress(0,0,0,false,0,100,100)).ready());
        assertTrue(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.COMMONER,
                progress(0,0,0,true,0,100,100)).ready());
        assertTrue(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.COMMONER,
                progress(1,0,0,false,0,100,100)).ready());
    }

    @Test void nextTiersRequireAchievementsAndRealBalance() {
        assertFalse(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.FREE_CITIZEN,
                progress(1,0,0,true,0,250,100)).ready());
        assertTrue(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.FREE_CITIZEN,
                progress(1,1,0,false,0,250,100)).ready());
        assertFalse(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.WEALTHY_CITIZEN,
                progress(2,4,0,false,10,600,100)).ready());
        assertTrue(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.WEALTHY_CITIZEN,
                progress(2,4,0,false,0,600,100)).ready());
        assertFalse(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.HONORED_CITIZEN,
                progress(3,8,0,false,0,1200,100)).ready());
        assertTrue(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.HONORED_CITIZEN,
                progress(3,8,1,false,0,1200,100)).ready());
        assertFalse(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.HONORED_CITIZEN,
                progress(3,8,1,false,0,1199,100)).ready());
    }

    @Test void reputationThresholdIsRequiredAtTheExactBoundary() {
        assertFalse(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.FREE_CITIZEN,
                progress(1,1,0,true,0,250,9)).ready());
        assertTrue(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.FREE_CITIZEN,
                progress(1,1,0,true,0,250,10)).ready());
        assertFalse(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.WEALTHY_CITIZEN,
                progress(2,4,0,true,0,600,29)).ready());
        assertFalse(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.HONORED_CITIZEN,
                progress(3,8,1,true,0,1200,59)).ready());
    }
    @Test void politicalLevelsCannotBeBought() {
        for(int level=5;level<=9;level++) {
            assertNull(KingdomSocialPromotionRules.forCurrent(KingdomSocialRank.fromLevel(level)));
            assertFalse(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.fromLevel(level),
                    progress(16,100,128,true,0,1000000,100)).ready());
        }
        assertFalse(KingdomSocialPromotionRules.evaluate(null,progress(1,1,1,true,0,1000,100)).ready());
    }
}
