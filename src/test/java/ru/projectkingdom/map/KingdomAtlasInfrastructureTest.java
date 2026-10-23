package ru.projectkingdom.map;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class KingdomAtlasInfrastructureTest {
    private static final String WORLD = "minecraft:overworld";
    private static final KingdomAtlasInfrastructure.Kind BUILDING =
            KingdomAtlasInfrastructure.Kind.BUILDING;

    @Test void filtersDimensionsAndOffscreenInfrastructure() {
        var sources = List.of(
            new KingdomAtlasInfrastructure.Source(WORLD,
                    new KingdomAtlasInfrastructure.Marker(BUILDING, 8, 8, "Кузница")),
            new KingdomAtlasInfrastructure.Source("minecraft:the_nether",
                    new KingdomAtlasInfrastructure.Marker(BUILDING, 8, 8, "Не наша карта")),
            new KingdomAtlasInfrastructure.Source(WORLD,
                    new KingdomAtlasInfrastructure.Marker(BUILDING, 16 * 900, 0, "За кадром")));
        var result = KingdomAtlasInfrastructure.select(sources, WORLD, 0, 0, 1);
        assertEquals(1, result.size());
        assertEquals("Кузница", result.get(0).label());
    }

    @Test void includesNegativeCoordinatesAndClipsToCurrentWindow() {
        var sources = List.of(
            new KingdomAtlasInfrastructure.Source(WORLD,
                    new KingdomAtlasInfrastructure.Marker(BUILDING, -17, -17, "Мельница")),
            new KingdomAtlasInfrastructure.Source(WORLD,
                    new KingdomAtlasInfrastructure.Marker(BUILDING, -1, -1, "Мост")));
        var result = KingdomAtlasInfrastructure.select(sources, WORLD, 0, 0, 1);
        assertEquals(2, result.size());
    }

    @Test void limitsMarkersTo96AndPrefersNearbyObjects() {
        var items = new ArrayList<KingdomAtlasInfrastructure.Source>();
        for (int i = 0; i < 220; i++) {
            items.add(new KingdomAtlasInfrastructure.Source(WORLD,
                    new KingdomAtlasInfrastructure.Marker(BUILDING, i % 40, i / 40,
                            "Объект " + i)));
        }
        items.add(new KingdomAtlasInfrastructure.Source(WORLD,
                new KingdomAtlasInfrastructure.Marker(
                        KingdomAtlasInfrastructure.Kind.TOWN_HALL, 0, 0, "Ратуша")));
        var selected = KingdomAtlasInfrastructure.select(items, WORLD, 0, 0, 1);
        assertEquals(KingdomAtlasInfrastructure.MAX_MARKERS, selected.size());
        assertTrue(selected.stream().anyMatch(m -> m.label().equals("Ратуша")));
    }

    @Test void restrictsLabelSizeAndDoesNotAcceptInvalidKind() {
        assertEquals(72, new KingdomAtlasInfrastructure.Marker(
                BUILDING, 0, 0, "x".repeat(100)).label().length());
        assertThrows(IllegalArgumentException.class, () ->
                new KingdomAtlasInfrastructure.Marker(null, 0, 0, "invalid"));
    }
}
