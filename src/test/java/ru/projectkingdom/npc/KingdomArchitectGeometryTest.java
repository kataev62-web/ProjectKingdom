package ru.projectkingdom.npc;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomArchitectGeometryTest {
    @Test void rotationsOfRectangularSchematicStayWithinFootprint() {
        int width = 7, length = 13;
        for (int turn = 0; turn < 4; turn++) {
            var dimensions = KingdomArchitectGeometry.footprint(width, length, turn);
            assertEquals(turn % 2 == 0 ? width : length, dimensions.width());
            assertEquals(turn % 2 == 0 ? length : width, dimensions.length());
            var visited = new HashSet<KingdomArchitectGeometry.Offset>();
            for (int x = 0; x < width; x++) for (int z = 0; z < length; z++) {
                var point = KingdomArchitectGeometry.rotate(x, z, width, length, turn);
                assertTrue(point.x() >= 0 && point.x() < dimensions.width());
                assertTrue(point.z() >= 0 && point.z() < dimensions.length());
                assertTrue(visited.add(point), "Rotation must not merge cells");
            }
            assertEquals(width * length, visited.size());
        }
    }

    @Test void positiveQuarterTurnsRotateClockwiseAndFourTurnsRestore() {
        assertEquals(new KingdomArchitectGeometry.Offset(0, 0),
            KingdomArchitectGeometry.rotate(0, 0, 7, 13, 0));
        assertEquals(new KingdomArchitectGeometry.Offset(12, 0),
            KingdomArchitectGeometry.rotate(0, 0, 7, 13, 1));
        assertEquals(new KingdomArchitectGeometry.Offset(6, 12),
            KingdomArchitectGeometry.rotate(0, 0, 7, 13, 2));
        assertEquals(new KingdomArchitectGeometry.Offset(0, 6),
            KingdomArchitectGeometry.rotate(0, 0, 7, 13, 3));
        assertEquals(KingdomArchitectGeometry.rotate(2, 8, 7, 13, 0),
            KingdomArchitectGeometry.rotate(2, 8, 7, 13, 4));
        assertEquals(KingdomArchitectGeometry.rotate(2, 8, 7, 13, 3),
            KingdomArchitectGeometry.rotate(2, 8, 7, 13, -1));
    }

    @Test void translatedPositionUsesArchitectOrigin() {
        assertEquals(new KingdomArchitectGeometry.Offset(100, -50),
            KingdomArchitectGeometry.world(0, 12, 7, 13, 1, 100, -50));
        assertEquals(new KingdomArchitectGeometry.Offset(112, -50),
            KingdomArchitectGeometry.world(0, 0, 7, 13, 1, 100, -50));
    }

    @Test void invalidFootprintsCannotProduceCoordinates() {
        assertThrows(IllegalArgumentException.class,
            () -> KingdomArchitectGeometry.rotate(7, 0, 7, 13, 1));
        assertThrows(IllegalArgumentException.class,
            () -> KingdomArchitectGeometry.rotate(-1, 0, 7, 13, 1));
        assertThrows(IllegalArgumentException.class,
            () -> KingdomArchitectGeometry.footprint(0, 13, 1));
        assertThrows(ArithmeticException.class,
            () -> KingdomArchitectGeometry.world(0, 0, 7, 13, 1, Integer.MAX_VALUE, 0));
    }
}
