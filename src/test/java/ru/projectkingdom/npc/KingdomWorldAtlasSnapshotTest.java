package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KingdomWorldAtlasSnapshotTest {
    private static final String DIM = "minecraft:overworld";

    @Test void displaysCitiesNationsAndPlacedEnterprisesWithoutTheirCashBalances() {
        var cities = new KingdomCivicsData();
        UUID founder = UUID.randomUUID(), other = UUID.randomUUID();
        var home = cities.foundTown(founder, "Авалон");
        var neighboringCity = cities.foundTown(other, "Рудный");
        assertNotNull(home);
        assertNotNull(neighboringCity);
        assertTrue(cities.claim(founder, DIM, 0, 0));
        assertTrue(cities.claim(other, DIM, 1, 0));
        assertNotNull(cities.foundNation(other, "Северное королевство"));
        var businesses = List.of(new KingdomWorldAtlasSnapshot.EnterprisePoint(
                DIM, 1, 0, "Шахта", "Горное дело"));
        var view = KingdomWorldAtlasSnapshot.create(cities, businesses, DIM, home.id(), 0, 0, 1);
        var mineCell = view.at(4, 1);
        assertEquals(1, mineCell.occupied());
        assertFalse(mineCell.ownTown());
        assertTrue(mineCell.foreignTown());
        assertEquals("Рудный", mineCell.town());
        assertEquals("Северное королевство", mineCell.nation());
        assertEquals("Шахта", mineCell.enterpriseName());
        assertEquals(1, mineCell.enterprises());
        assertTrue(view.at(3,1).ownTown());
        assertEquals(0, view.at(6, 3).occupied());
        assertEquals(0, view.at(6, 3).enterprises());
    }

    @Test void supportsNegativeCoordinatesAndFiltersOtherDimensions() {
        var cities = new KingdomCivicsData();
        UUID founder = UUID.randomUUID();
        assertNotNull(cities.foundTown(founder, "Горный"));
        assertTrue(cities.claim(founder, DIM, -2, -1));
        var businesses = List.of(
                new KingdomWorldAtlasSnapshot.EnterprisePoint(DIM, -2, -1, "Лес", "Лесозаготовка"),
                new KingdomWorldAtlasSnapshot.EnterprisePoint("minecraft:the_nether", -2, -1,
                        "Не должен показываться", "Шахта"));
        var view = KingdomWorldAtlasSnapshot.create(cities, businesses, DIM,
                cities.townOf(founder).id(), -2, -1, 1);
        var tile = view.at(3, 1);
        assertEquals(-2, tile.chunkX());
        assertEquals(-1, tile.chunkZ());
        assertTrue(tile.ownTown());
        assertEquals(1, tile.enterprises());
    }

    @Test void zoomBucketsMultipleChunksIntoOneTile() {
        var cities = new KingdomCivicsData();
        UUID founder = UUID.randomUUID();
        var home = cities.foundTown(founder, "Деревня");
        assertTrue(cities.claim(founder, DIM, 0, 0));
        assertTrue(cities.claim(founder, DIM, 1, 0));
        var view = KingdomWorldAtlasSnapshot.create(cities, List.of(), DIM, home.id(), 0, 0, 4);
        var central = view.at(3, 1);
        assertEquals(0, central.chunkX());
        assertEquals(0, central.chunkZ());
        assertEquals(2, central.occupied());
        assertEquals(4, central.scale());
    }

    @Test void rejectsUnboundedAtlasScale() {
        assertThrows(IllegalArgumentException.class, () ->
                KingdomWorldAtlasSnapshot.create(new KingdomCivicsData(), List.of(),
                        DIM, null, 0, 0, 4096));
    }
}
