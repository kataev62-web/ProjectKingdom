package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class AdministrativeMenuLayoutTest {
    @Test void eachKindHasInfoLogAndNonOverlappingTabs() {
        for(var kind:AdministrativeKind.values()){
            List<AdministrativeMenuLayout.Tab> tabs=AdministrativeMenuLayout.tabs(kind);
            assertTrue(tabs.contains(AdministrativeMenuLayout.Tab.INFO),kind.id());
            assertTrue(tabs.contains(AdministrativeMenuLayout.Tab.LOG),kind.id());
            var rects=AdministrativeMenuLayout.tabRects(kind);
            assertEquals(tabs.size(),rects.size());
            for(int i=1;i<rects.size();i++)assertTrue(rects.get(i-1).bottom()<rects.get(i).y());
            assertTrue(rects.stream().allMatch(rect->rect.right()<AdministrativeMenuLayout.content().x()));
        }
    }
    @Test void specialistTabsMatchService() {
        assertTrue(AdministrativeMenuLayout.tabs(AdministrativeKind.TOWN_HALL).containsAll(
                List.of(AdministrativeMenuLayout.Tab.CITIZENS,AdministrativeMenuLayout.Tab.OFFICES)));
        assertTrue(AdministrativeMenuLayout.tabs(AdministrativeKind.COURT).contains(AdministrativeMenuLayout.Tab.CASES));
        assertTrue(AdministrativeMenuLayout.tabs(AdministrativeKind.PRISON).contains(AdministrativeMenuLayout.Tab.CELLS));
        assertTrue(AdministrativeMenuLayout.tabs(AdministrativeKind.GUARD_ADMINISTRATION).contains(AdministrativeMenuLayout.Tab.GUARDS));
    }
}
