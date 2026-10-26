package ru.projectkingdom.map;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class KingdomAtlasUsabilityTest {
    @Test void compactSidebarControlsFit() {
        for (int[] size : new int[][]{{240,210},{320,240},{480,270},{640,360},{900,510}}) {
            var metrics = KingdomAtlasLayout.calculate(size[0],size[1]);
            int h=metrics.panelHeight();
            int y=h<224?114:128, step=h<224?21:23, button=h<224?19:20;
            assertTrue(y+3*step+button<=h-4);
        }
    }
    @Test void atlasScreenKeepsClearMarkersAndLegends() throws Exception {
        String s=Files.readString(Path.of("src/main/java/ru/projectkingdom/map/KingdomGraphicAtlasScreen.java"));
        assertTrue(s.contains("renderMapLegend(gui)"));
        assertTrue(s.contains("renderSelectionSummary(gui)"));
        assertTrue(s.contains("markerSymbol(mark.kind())"));
        assertTrue(s.contains("projection.onMap()"));
        assertFalse(s.contains("int col=compact?i%2:0"));
    }
}
