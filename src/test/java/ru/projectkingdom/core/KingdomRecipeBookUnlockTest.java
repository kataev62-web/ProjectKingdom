package ru.projectkingdom.core;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class KingdomRecipeBookUnlockTest {
    @Test void allCraftableKingdomItemsBecomeVisibleInRecipeBook() throws Exception {
        Path advancement=Path.of("src/main/resources/data/kingdomnpc/advancements/recipes/unlock_all.json");
        assertTrue(Files.isRegularFile(advancement),"recipe-book unlock advancement is missing");
        String json=Files.readString(advancement);
        assertTrue(json.contains("minecraft:tick"),"recipes must unlock automatically for every player");
        List<String> ids=List.of(
                "wallet",
                "farm_core","mine_core","lumber_core","smithy_core","bakery_core","pottery_core","construction_core","courier_core",
                "town_hall_core","treasury_core","bank_core","central_warehouse_core","city_market_core","land_office_core","court_core","prison_core","guard_administration_core");
        for(String id:ids){
            assertTrue(Files.isRegularFile(Path.of("src/main/resources/data/kingdomnpc/recipes/"+id+".json")),id+" recipe is missing");
            assertTrue(json.contains("kingdomnpc:"+id),id+" is not unlocked in the recipe book");
        }
        assertEquals(18,ids.stream().distinct().count());
    }
}
