package ru.projectkingdom.map;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class KingdomAtlasPlayerMarkerTest {
    @Test void playerPositionIsAccurateInsideAChunkAtNormalZoom() {
        var p = KingdomAtlasPlayerMarker.project(8.0, 24.0, 0.0f, 0, 1, 1);
        assertTrue(p.onMap());
        assertEquals(11.5, p.gridX(), 1e-9);
        assertEquals(7.5, p.gridY(), 1e-9);
        assertEquals(0, p.facingX(), 1e-9);
        assertEquals(1, p.facingY(), 1e-9);
    }
    @Test void positionUsesFloorCompatibleNegativeMinecraftCoordinates() {
        var p = KingdomAtlasPlayerMarker.project(-0.5, -16.5, -90f, -1, -2, 1);
        assertTrue(p.onMap());
        assertEquals(11.96875, p.gridX(), 1e-9);
        assertEquals(7.96875, p.gridY(), 1e-9);
        assertEquals(1, p.facingX(), 1e-9);
        assertEquals(0, p.facingY(), 1e-9);
    }
    @Test void zoomAndManualPanningCanPutPlayerOutsideVisibleMap() {
        var center = KingdomAtlasPlayerMarker.project(16 * 40.0, 16 * 40.0, 90f, 40, 40, 4);
        assertTrue(center.onMap());
        assertEquals(11, center.gridX(), 1e-9);
        assertEquals(7, center.gridY(), 1e-9);
        var away = KingdomAtlasPlayerMarker.project(16 * 100.0, 16 * 100.0, 90f, 0, 0, 1);
        assertFalse(away.onMap());
        assertEquals(-1, center.facingX(), 1e-9);
    }
    @Test void impossibleCoordinatesAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                KingdomAtlasPlayerMarker.project(Double.NaN, 0, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () ->
                KingdomAtlasPlayerMarker.project(0, 0, 0, 0, 0, 2));
    }
}
