package ru.projectkingdom.network;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Safety contract for the preliminary administrative map UI. */
class CityExpansionMapSourceTest {
    @Test void bookEntryRequiresServerSideOpenBook() throws Exception {
        String source = Files.readString(Path.of("src/main/java/ru/projectkingdom/network/OpenCityExpansionMapPacket.java"));
        assertTrue(source.contains("player.containerMenu instanceof KingdomBookMenu"));
        assertTrue(source.contains("KingdomCityExpansionMapMenu.open(player)"));
        assertFalse(source.contains("readUUID("));
    }

    @Test void expansionPlanCannotClaimOrDebitRemotely() throws Exception {
        String source = Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomCityExpansionMapMenu.java"));
        assertTrue(source.contains("KingdomCivicsData.get"));
        assertTrue(source.contains("founder()"));
        assertTrue(source.contains("contiguous("));
        assertTrue(source.contains("100L * planned.size()"));
        assertFalse(source.contains("KingdomCurrency.debit("));
        assertFalse(source.contains(".claim("));
    }
    @Test void persistentPlanHasBoundedStorageAndMapRevalidates() throws Exception {
        String model = Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomExpansionPlans.java"));
        String menu = Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomCityExpansionMapMenu.java"));
        assertTrue(model.contains("extends SavedData"));
        assertTrue(model.contains("cells.size() > 256"));
        assertTrue(menu.contains("pruneInvalid()"));
        assertTrue(menu.contains("plans.replace(cityId, merged)"));
        assertFalse(menu.contains("KingdomCurrency.debit("));
    }
}

