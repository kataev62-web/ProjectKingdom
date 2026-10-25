package ru.projectkingdom.npc;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Nutrition first; among similar meals use actual TFC expiry (never fake a date). */
class KingdomFoodFreshnessTest {
    private static KingdomNpcData.Worker worker(){
        return new KingdomNpcData.Worker(91,UUID.randomUUID(),"Test cook","minecraft:overworld",0);
    }
    @Test void nearExpiryReceivesPriorityButRottenFoodDoesNot(){
        assertEquals(0,KingdomFoodFreshness.urgency(100L,100L));
        assertEquals(0,KingdomFoodFreshness.urgency(99L,100L));
        assertEquals(24,KingdomFoodFreshness.urgency(24100L,100L));
        assertEquals(16,KingdomFoodFreshness.urgency(24101L,100L));
        assertEquals(8,KingdomFoodFreshness.urgency(72101L,100L));
        assertEquals(0,KingdomFoodFreshness.urgency(Long.MAX_VALUE,100L));
        assertTrue(KingdomFoodFreshness.prefers(25L,35L));
    }
    @Test void choosingSoonerExpirationNeverMintsCalories(){
        var w=worker();
        var meal=new KingdomNpcNutrition.Meal(4,new int[]{6,0,4,0,0});
        int newer=KingdomFoodFreshness.mealScore(w,meal,Long.MAX_VALUE,0L);
        int older=KingdomFoodFreshness.mealScore(w,meal,12000L,0L);
        assertEquals(newer+24,older);
        assertEquals(4,meal.hunger());
        assertEquals(6,meal.nutrients()[0]);
    }
    @Test void nutrientDeficitsStillMatter(){
        var w=worker();
        w.dietProtein=10;
        var protein=new KingdomNpcNutrition.Meal(4,new int[]{0,0,0,32,0});
        var grain=new KingdomNpcNutrition.Meal(4,new int[]{32,0,0,0,0});
        int a=KingdomFoodFreshness.mealScore(w,protein,Long.MAX_VALUE,0L);
        int b=KingdomFoodFreshness.mealScore(w,grain,Long.MAX_VALUE,0L);
        assertTrue(a>b);
    }
}
