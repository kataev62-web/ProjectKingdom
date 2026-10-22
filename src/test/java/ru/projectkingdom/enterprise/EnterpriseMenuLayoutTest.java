package ru.projectkingdom.enterprise;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class EnterpriseMenuLayoutTest {
    @Test void sevenSideTabsDoNotOverlapParchmentContent(){
        var tabs=EnterpriseMenuLayout.tabs();var content=EnterpriseMenuLayout.content();
        assertEquals(7,tabs.size());
        for(int i=1;i<tabs.size();i++)assertTrue(tabs.get(i-1).bottom()<tabs.get(i).y());
        assertTrue(tabs.stream().allMatch(tab->tab.right()<content.x()));
        assertTrue(content.right()<=EnterpriseMenuLayout.WIDTH);
        assertTrue(content.bottom()<=EnterpriseMenuLayout.HEIGHT);
    }
    @Test void tabCatalogMatchesApprovedOrder(){
        assertArrayEquals(new EnterpriseMenuLayout.Tab[]{EnterpriseMenuLayout.Tab.INFO,
                EnterpriseMenuLayout.Tab.WORKERS,EnterpriseMenuLayout.Tab.PRODUCTION,
                EnterpriseMenuLayout.Tab.STORAGE,EnterpriseMenuLayout.Tab.CASH,
                EnterpriseMenuLayout.Tab.ORDERS,EnterpriseMenuLayout.Tab.SETTINGS},
                EnterpriseMenuLayout.Tab.values());
    }
}
