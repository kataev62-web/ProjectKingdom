package ru.projectkingdom.map;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Keeps GUI panels/buttons inside both compact and large Minecraft windows. */
class KingdomAtlasLayoutTest {
    @Test void fitsSupportedGuiSizesWithoutOffscreenMapOrSidebar() {
        for(int[] size:new int[][]{
                {240,210},{320,240},{360,240},{480,270},{640,360},{900,510},{1280,720}}) {
            var v=KingdomAtlasLayout.calculate(size[0],size[1]);
            assertTrue(v.fits(), "Atlas clipped at "+size[0]+"x"+size[1]+": "+v);
            assertEquals(23*v.cell(),v.mapWidth());
            assertEquals(15*v.cell(),v.mapHeight());
            assertTrue(v.gridX()>=v.left()+v.navWidth());
            assertTrue(v.panelWidth()<=size[0]);
            assertTrue(v.panelHeight()<=size[1]);
        }
    }

    @Test void detailedGuiGainsInspectorAndLargerMapCells() {
        var compact=KingdomAtlasLayout.calculate(320,240);
        var large=KingdomAtlasLayout.calculate(1280,720);
        assertEquals(0,compact.inspectorWidth());
        assertTrue(compact.compact());
        assertTrue(large.inspectorWidth()>=140);
        assertFalse(large.compact());
        assertTrue(large.cell()>compact.cell());
    }
    @Test void rejectsTinyScreensRatherThanClaimingToFit() {
        assertThrows(IllegalArgumentException.class,()->
                KingdomAtlasLayout.calculate(160,120));
    }
}
