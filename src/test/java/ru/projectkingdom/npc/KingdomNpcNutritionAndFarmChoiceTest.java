package ru.projectkingdom.npc;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomNpcNutritionAndFarmChoiceTest {
    private static KingdomNpcData.Worker resident() {
        return new KingdomNpcData.Worker(7, UUID.randomUUID(), "Farmer",
            "minecraft:overworld", 0);
    }

    @Test void differentFoodGroupsAffectDietWithoutMintingItems() {
        var person=resident();person.hunger=45;
        var actualMeal=new KingdomNpcNutrition.Meal(4,new int[]{16,0,22,0,0});
        KingdomNpcNutrition.eat(person,actualMeal);
        assertEquals(65,person.hunger);
        assertEquals(66,person.dietGrain);
        assertEquals(72,person.dietVegetables);
        assertEquals(50,person.dietProtein);
        assertEquals(57,KingdomNpcNutrition.quality(person));
    }

    @Test void poorDietReducesEnergyMoraleAndWorkFrequency() {
        var w=resident();w.dietGrain=10;w.dietFruit=10;w.dietVegetables=10;
        w.dietProtein=10;w.dietDairy=10;
        int oldEnergy=w.energy,oldMorale=w.morale;
        KingdomNpcNutrition.age(w,1);
        assertTrue(w.energy<oldEnergy);
        assertTrue(w.morale<oldMorale);
        assertFalse(KingdomNpcNutrition.permittedWorkTick(w,80));
        assertTrue(KingdomNpcNutrition.permittedWorkTick(w,120));
        w.dietGrain=85;w.dietFruit=85;w.dietVegetables=85;
        w.dietProtein=85;w.dietDairy=85;
        assertTrue(KingdomNpcNutrition.permittedWorkTick(w,80));
    }

    @Test void emptyStoreOrUnsuitableCropsDoNotProducePlans() {
        assertEquals("",KingdomFarmAutonomy.choose(List.of(
            new KingdomFarmAutonomy.Option("tfc:seeds/carrot",0,0,15,true,.5f,false),
            new KingdomFarmAutonomy.Option("tfc:seeds/barley",48,0,10,false,.5f,false))));
        assertEquals(KingdomNpcNutrition.GRAIN,
            KingdomFarmAutonomy.nutritionGroup("tfc:seeds/wheat"));
        assertEquals(KingdomNpcNutrition.VEGETABLES,
            KingdomFarmAutonomy.nutritionGroup("tfc:seeds/cabbage"));
        assertEquals(-1,KingdomFarmAutonomy.nutritionGroup("tfc:seeds/tomato"));
    }

    @Test void lowFoodStocksAndPoorNutritionOutweighLargeSurplus() {
        var stocks=List.of(
            new KingdomFarmAutonomy.Option("tfc:seeds/barley",32,128,85,true,.65f,true),
            new KingdomFarmAutonomy.Option("tfc:seeds/carrot",12,4,20,true,.55f,false));
        assertEquals("tfc:seeds/carrot",KingdomFarmAutonomy.choose(stocks));
        var noCarrots=List.of(stocks.get(0),
            new KingdomFarmAutonomy.Option("tfc:seeds/carrot",0,4,20,true,.55f,false));
        assertEquals("tfc:seeds/barley",KingdomFarmAutonomy.choose(noCarrots));
    }
}
