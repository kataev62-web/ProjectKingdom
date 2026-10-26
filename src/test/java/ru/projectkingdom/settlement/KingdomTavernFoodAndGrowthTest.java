package ru.projectkingdom.settlement;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

final class KingdomTavernFoodAndGrowthTest {
    private String code(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+path));
    }

    @Test void growthNeedsPopulationAndIndependentBuildings() {
        var grade=KingdomSettlementGrowth.Rank.HAMLET;
        assertEquals(grade,KingdomSettlementGrowth.rank(64,30,32,false));
        assertEquals(grade,KingdomSettlementGrowth.rank(4,0,4,true));
        assertEquals(grade,KingdomSettlementGrowth.rank(4,1,2,true));
        assertEquals(KingdomSettlementGrowth.Rank.VILLAGE,
                KingdomSettlementGrowth.rank(4,1,3,true));
        assertEquals(KingdomSettlementGrowth.Rank.VILLAGE,
                KingdomSettlementGrowth.rank(12,3,8,true));
        assertEquals(KingdomSettlementGrowth.Rank.SETTLEMENT,
                KingdomSettlementGrowth.rank(12,4,8,true));
        assertEquals(KingdomSettlementGrowth.Rank.SETTLEMENT,
                KingdomSettlementGrowth.rank(32,10,20,true));
        assertEquals(KingdomSettlementGrowth.Rank.CITY,
                KingdomSettlementGrowth.rank(32,11,20,true));
    }

    @Test void foodMustBePhysicalAndEdible() {
        assertTrue(KingdomTavernProvisions.edible(new ItemStack(Items.BREAD)));
        assertFalse(KingdomTavernProvisions.edible(new ItemStack(Items.COBBLESTONE)));
        assertFalse(KingdomTavernProvisions.edible(ItemStack.EMPTY));
        assertEquals(2,KingdomTavernProvisions.MIN_MEALS_TO_ATTRACT);
        assertEquals(4,KingdomTavernImmigration.MAX_TAVERN_RESIDENTS);
        assertEquals(32,KingdomTavernImmigration.MAX_SETTLEMENT_RESIDENTS);
    }

    @Test void tavernServesFreshRealFoodAndImmigrantsHaveNoTrade() throws Exception {
        String pantry=code("settlement/KingdomTavernProvisions.java");
        String immigration=code("settlement/KingdomTavernImmigration.java");
        String menu=code("enterprise/KingdomHospitalityMenu.java");
        assertTrue(pantry.contains("KingdomNpcNutrition.spoiled(stack)"));
        assertTrue(pantry.contains("EnterpriseBindingRole.RAW_STORAGE"));
        assertTrue(pantry.contains("pantryInsideTavern(level,enterprise,view.position())"));
        assertTrue(pantry.contains("properties.atRoom("));
        assertTrue(pantry.contains("box.removeItem(portion.slot(),1)"));
        assertTrue(immigration.contains("KingdomTavernProvisions.consume(serving)"));
        assertTrue(immigration.contains("created.job=\"free\""));
        assertTrue(immigration.contains("created.employer=null"));
        assertTrue(immigration.contains("withIndependentHousing("));
        assertTrue(immigration.contains("KingdomConstructionData.Status.COMPLETED"));
        assertTrue(menu.contains("EnterpriseAccessService.begin(viewer,enterprise,EnterpriseBindingRole.RAW_STORAGE)"));
        assertTrue(menu.contains("arrivals.meals()"));
        String service=code("settlement/KingdomTavernMeals.java");
        assertTrue(service.contains("worker.lastMealDay>=day"));
        assertTrue(service.contains("KingdomTavernProvisions.consume(food)"));
        assertTrue(service.contains("KingdomNpcNutrition.eat(worker,food.nutrition())"));
        assertTrue(service.contains("worker.employer!=null"));
        assertTrue(code("npc/KingdomNpcMod.java").contains("KingdomTavernMeals.tick(server)"));
    }
}
