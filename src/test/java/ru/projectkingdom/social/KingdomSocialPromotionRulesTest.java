package ru.projectkingdom.social;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomSocialPromotionRulesTest {
    private static KingdomSocialPromotionRules.Progress progress(long firms,long workers,long plots,
            boolean employed,long debt,long purse) {
        return new KingdomSocialPromotionRules.Progress(firms,workers,plots,employed,debt,purse);
    }

    @Test void employmentOrEnterpriseRequiredForFirstPromotion() {
        var rules=KingdomSocialPromotionRules.forCurrent(KingdomSocialRank.COMMONER);
        assertEquals(100,rules.feeCopper());
        assertFalse(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.COMMONER,
                progress(0,0,0,false,0,100)).ready());
        assertTrue(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.COMMONER,
                progress(0,0,0,true,0,100)).ready());
        assertTrue(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.COMMONER,
                progress(1,0,0,false,0,100)).ready());
    }

    @Test void nextTiersRequireAchievementsAndRealBalance() {
        assertFalse(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.FREE_CITIZEN,
                progress(1,0,0,true,0,250)).ready());
        assertTrue(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.FREE_CITIZEN,
                progress(1,1,0,false,0,250)).ready());
        assertFalse(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.WEALTHY_CITIZEN,
                progress(2,4,0,false,10,600)).ready());
        assertTrue(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.WEALTHY_CITIZEN,
                progress(2,4,0,false,0,600)).ready());
        assertFalse(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.HONORED_CITIZEN,
                progress(3,8,0,false,0,1200)).ready());
        assertTrue(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.HONORED_CITIZEN,
                progress(3,8,1,false,0,1200)).ready());
        assertFalse(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.HONORED_CITIZEN,
                progress(3,8,1,false,0,1199)).ready());
    }

    @Test void politicalLevelsCannotBeBought() {
        for(int level=5;level<=9;level++) {
            assertNull(KingdomSocialPromotionRules.forCurrent(KingdomSocialRank.fromLevel(level)));
            assertFalse(KingdomSocialPromotionRules.evaluate(KingdomSocialRank.fromLevel(level),
                    progress(16,100,128,true,0,1000000)).ready());
        }
        assertFalse(KingdomSocialPromotionRules.evaluate(null,progress(1,1,1,true,0,1000)).ready());
    }
}
