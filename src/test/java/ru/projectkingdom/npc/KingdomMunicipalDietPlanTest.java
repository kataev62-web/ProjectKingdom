package ru.projectkingdom.npc;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomMunicipalDietPlanTest {
    private static KingdomMunicipalFoodReserves.Forecast forecast(boolean complete){
        return new KingdomMunicipalFoodReserves.Forecast(150,complete,4,90,3,
            List.of(60,2,9,5,1),15,150,complete?60:0);
    }
    @Test void incompleteTownWarehouseDoesNotBecomeFreePurchasingTarget(){
        var p=KingdomMunicipalDietPlan.calculate(forecast(false));
        assertFalse(p.known());
        assertEquals(-1,p.greatestDeficit());
        assertEquals(0,KingdomMunicipalDietPlan.nutritionValue(p,new int[]{0,8,0,0,0}));
        assertTrue(KingdomMunicipalDietPlan.prioritize(p,List.of(),5).isEmpty());
    }
    @Test void nutrientGroupsAreCountedIndependentlyAndOrderedDeterministically(){
        var p=KingdomMunicipalDietPlan.calculate(forecast(true));
        assertEquals(30,p.groupTarget());
        assertEquals(List.of(0L,28L,21L,25L,29L),p.missing());
        assertEquals(4,p.greatestDeficit());
        assertEquals(List.of(4,1,3,2,0),p.priority());
    }
    @Test void lackingProteinAndDairyBeatCheapGrainWithoutDebit(){
        var need=KingdomMunicipalDietPlan.calculate(true,150,List.of(60,60,40,1,2));
        var grain=new KingdomMunicipalDietPlan.Candidate("tfc:food/bread",1,
            new int[]{10,0,0,0,0},16,Long.MAX_VALUE);
        var meat=new KingdomMunicipalDietPlan.Candidate("tfc:food/cooked_beef",8,
            new int[]{0,0,0,8,0},4,2200L);
        var milk=new KingdomMunicipalDietPlan.Candidate("tfc:food/cheese",8,
            new int[]{0,0,0,0,8},5,2000L);
        var sorted=KingdomMunicipalDietPlan.prioritize(need,List.of(grain,meat,milk),5);
        assertEquals("tfc:food/cooked_beef",sorted.get(0).itemId());
        assertEquals("tfc:food/cheese",sorted.get(1).itemId());
        assertEquals("tfc:food/bread",sorted.get(2).itemId());
        assertEquals(0,KingdomMunicipalDietPlan.nutritionValue(need,grain.nutrients()));
    }
    @Test void mixedMealMayCoverMultipleShortagesWithoutDoubleCountingPortions(){
        var need=KingdomMunicipalDietPlan.calculate(true,100,List.of(0,0,0,0,0));
        long mixed=KingdomMunicipalDietPlan.nutritionValue(need,new int[]{5,0,5,5,0});
        long mono=KingdomMunicipalDietPlan.nutritionValue(need,new int[]{5,0,0,0,0});
        assertTrue(mixed>mono);
        assertThrows(IllegalArgumentException.class,()->
            new KingdomMunicipalDietPlan.Candidate("food",3,new int[]{2,3},1,4));
    }
}
