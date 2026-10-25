package ru.projectkingdom.npc;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomFoodBalanceTest {
    @Test void lowProteinDemandOutranksSurplusGrain(){
        int[] stock={120,15,25,1,14};
        int[] grain={20,0,0,0,0};
        int[] stew={2,0,5,20,0};
        assertTrue(KingdomFoodBalance.score(stock,stew)>
            KingdomFoodBalance.score(stock,grain));
        assertEquals(0,KingdomFoodBalance.score(
            new int[]{100,100,100,100,100},grain));
    }
    @Test void mixedMealContributesToMultipleMissingGroups(){
        int[] stock={20,0,0,0,0};
        assertTrue(KingdomFoodBalance.score(stock,new int[]{2,2,2,2,2})>
            KingdomFoodBalance.score(stock,new int[]{2,0,0,0,0}));
    }
    @Test void corruptScoreVectorsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->
            KingdomFoodBalance.score(new int[]{1,2},new int[]{1,2,3,4,5}));
    }
}
