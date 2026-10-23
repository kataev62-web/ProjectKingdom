package ru.projectkingdom.map;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomExpansionPlans;

class KingdomGraphicAtlasDataTest {
    private static final String WORLD = "minecraft:overworld";
    @Test void drawsDistinctCitiesAndNationsAndEnterpriseMarkers() {
        var civic = new KingdomCivicsData();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var first = civic.foundTown(a,"Авалон");
        var second = civic.foundTown(b,"Шахтёрск");
        assertNotNull(first);
        assertNotNull(second);
        assertTrue(civic.claim(a, WORLD, 0,0));
        assertTrue(civic.claim(b, WORLD, 1,0));
        assertNotNull(civic.foundNation(b, "Северное королевство"));
        var view = KingdomGraphicAtlasData.project(1, 0,0,1,
            KingdomGraphicAtlasData.Layer.POLITICAL,WORLD,first.id(),civic,
            List.of(new KingdomGraphicAtlasData.Business(WORLD, 1,0,"Рудник")),Set.of());
        assertEquals(KingdomGraphicAtlasData.MAX_CELLS,view.tiles().size());
        var central = view.tile(KingdomGraphicAtlasData.COLUMNS/2,KingdomGraphicAtlasData.ROWS/2);
        var neighbor = view.tile(KingdomGraphicAtlasData.COLUMNS/2+1,KingdomGraphicAtlasData.ROWS/2);
        assertEquals("Авалон",central.town());
        assertTrue(central.own());
        assertEquals("Шахтёрск",neighbor.town());
        assertEquals("Северное королевство",neighbor.nation());
        assertFalse(neighbor.own());
        assertEquals("Рудник",neighbor.business());
        assertEquals(1,neighbor.enterprises());
    }

    @Test void zoomAndPlansShowWithoutClaimingLand() {
        var civic = new KingdomCivicsData();
        UUID owner = UUID.randomUUID();
        var town = civic.foundTown(owner,"Столица");
        assertNotNull(town);
        assertTrue(civic.claim(owner,WORLD,-1,-1));
        var future = new KingdomExpansionPlans.Cell(WORLD,0,0);
        var view = KingdomGraphicAtlasData.project(17,0,0,4,
                KingdomGraphicAtlasData.Layer.LAND,WORLD,town.id(),civic,
                List.of(),List.of(future));
        var target = view.tile(KingdomGraphicAtlasData.COLUMNS/2,KingdomGraphicAtlasData.ROWS/2);
        assertTrue(target.planned());
        assertEquals(0,target.claims());
        assertEquals(1,civic.mapPlots(WORLD).size());
        assertEquals(17,view.requestId());
    }

    @Test void differentDimensionsHaveNoCrossedCompanyOrCityMarkers() {
        var civic = new KingdomCivicsData();
        UUID owner = UUID.randomUUID();
        assertNotNull(civic.foundTown(owner,"Лесной"));
        assertTrue(civic.claim(owner,WORLD,-2,-3));
        var view = KingdomGraphicAtlasData.project(3,-2,-3,1,
                KingdomGraphicAtlasData.Layer.ENTERPRISES,"minecraft:the_nether",null,civic,
                List.of(new KingdomGraphicAtlasData.Business(WORLD,-2,-3,"Лесопилка")),Set.of());
        var center = view.tile(KingdomGraphicAtlasData.COLUMNS/2,KingdomGraphicAtlasData.ROWS/2);
        assertTrue(center.empty());
        assertEquals("",center.business());
    }
}
