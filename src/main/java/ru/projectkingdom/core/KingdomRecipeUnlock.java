package ru.projectkingdom.core;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * One server-side catalog for all Kingdom survival recipes. Resolving through
 * the live RecipeManager avoids granting nonexistent/disabled datapack recipes.
 */
public final class KingdomRecipeUnlock {
    private KingdomRecipeUnlock() {}

    public static final List<String> IDS = List.of(
        "wallet",
        "farm_core", "mine_core", "lumber_core", "smithy_core",
        "bakery_core", "pottery_core", "construction_core", "courier_core",
        "town_hall_core", "treasury_core", "bank_core", "central_warehouse_core",
        "city_market_core", "land_office_core", "court_core", "prison_core",
        "guard_administration_core",
        "managed_storage", "demand_board",
        "drafting_table", "building_blueprint", "builders_hammer"
    );

    public static List<Recipe<?>> all(RecipeManager manager) {
        var available = new ArrayList<Recipe<?>>();
        for (String name : IDS) {
            var id = new ResourceLocation("kingdomnpc", name);
            manager.byKey(id).ifPresent(available::add);
        }
        return available;
    }
}
