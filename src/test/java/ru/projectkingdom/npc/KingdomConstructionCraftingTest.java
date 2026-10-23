package ru.projectkingdom.npc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Regression checks: every craftable construction item has survival assets. */
final class KingdomConstructionCraftingTest {
    private String asset(String path) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(input, "Missing resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test void constructionEquipmentHasRecipesModelsAndRussianNames() throws Exception {
        String language = asset("/assets/kingdomnpc/lang/ru_ru.json");
        String unlock = asset("/data/kingdomnpc/advancements/recipes/unlock_all.json");
        for (String id : new String[]{"drafting_table", "building_blueprint", "builders_hammer"}) {
            JsonObject recipe = JsonParser.parseString(asset("/data/kingdomnpc/recipes/" + id + ".json"))
                .getAsJsonObject();
            assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString(), id);
            assertEquals("kingdomnpc:" + id,
                recipe.getAsJsonObject("result").get("item").getAsString(), id);
            assertTrue(unlock.contains("kingdomnpc:" + id), id + " recipe unlock");
            assertNotNull(getClass().getResourceAsStream("/assets/kingdomnpc/models/item/" + id + ".json"),
                id + " inventory model");
            assertTrue(language.contains("item.kingdomnpc." + id), id + " Russian translation");
        }
        assertNotNull(getClass().getResourceAsStream("/assets/kingdomnpc/blockstates/drafting_table.json"));
        assertNotNull(getClass().getResourceAsStream("/assets/kingdomnpc/models/block/drafting_table.json"));
        assertNotNull(getClass().getResourceAsStream("/data/kingdomnpc/loot_tables/blocks/drafting_table.json"));
    }

    @Test void materialRecipesAreNotCreativeFreebies() throws Exception {
        for (String id : new String[]{"drafting_table", "building_blueprint", "builders_hammer"}) {
            JsonObject recipe = JsonParser.parseString(asset("/data/kingdomnpc/recipes/" + id + ".json"))
                .getAsJsonObject();
            assertTrue(recipe.getAsJsonObject("key").size() >= 2, id + " must consume ingredients");
            assertTrue(recipe.getAsJsonArray("pattern").size() >= 2, id + " must require crafting");
        }
        String hammer = asset("/data/kingdomnpc/recipes/builders_hammer.json");
        assertTrue(hammer.contains("forge:ingots/wrought_iron"), "TFC wrought iron alternative");
        assertTrue(hammer.contains("forge:ingots/iron"), "vanilla/Forge iron alternative");
    }

    @Test void npcAndPlayerSharePhysicalConstructionEngine() throws Exception {
        // Static source assertions protect against accidentally reintroducing free building.
        String builder = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/ru/projectkingdom/npc/KingdomNpcBuilder.java"));
        String interaction = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/ru/projectkingdom/npc/KingdomNpcMod.java"));
        String placement = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/ru/projectkingdom/npc/KingdomConstructionWork.java"));
        assertTrue(builder.contains("KingdomConstructionWork.placeNext"));
        assertTrue(interaction.contains("KingdomConstructionWork.placeNext"));
        assertTrue(placement.contains("depot.removeItem(slot, 1)"));
        assertTrue(placement.contains("KingdomConstructionAccess.canBuild"));
        assertTrue(placement.contains("level.hasChunkAt(target)"));
    }
}
