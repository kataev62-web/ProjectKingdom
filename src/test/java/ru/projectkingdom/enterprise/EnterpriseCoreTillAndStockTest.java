package ru.projectkingdom.enterprise;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source-level integration checks keep the new core-owned cash and stock routes together.
 * Real Forge game integration is still validated on server/client runtime. */
final class EnterpriseCoreTillAndStockTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+relative));
    }
    @Test void internalCashHasPersistentNbtAndNoHopperContainerCapability() throws Exception {
        String core=source("enterprise/EnterpriseCoreBlockEntity.java");
        assertTrue(core.contains("private final SimpleContainer till"));
        assertTrue(core.contains("public Container cashInventory()"));
        assertTrue(core.contains("ContainerHelper.saveAllItems"));
        assertTrue(core.contains("ContainerHelper.loadAllItems"));
        assertTrue(core.contains("PhysicalCash"));
        assertFalse(core.contains("implements Container"));
        assertFalse(core.contains("ForgeCapabilities.ITEM_HANDLER"));
    }
    @Test void externalCashboxCannotBeRegisteredAndExistingCoinsCanBeMigrated() throws Exception {
        String access=source("enterprise/EnterpriseAccessService.java");
        String data=source("npc/KingdomEnterpriseData.java");
        String menu=source("enterprise/EnterpriseCoreMenu.java");
        assertTrue(access.contains("role==EnterpriseBindingRole.CASHBOX"));
        assertTrue(access.contains("core.cashInventory()"));
        assertTrue(data.contains("KingdomPhysicalCash.move(from,inner.container(),amount)"));
        assertTrue(data.contains("source+inside!=firm.cash()"));
        assertTrue(data.contains("KingdomPhysicalCash.value(cash.container())<firm.cash()"));
        assertTrue(menu.contains("MIGRATE_CASH"));
    }
    @Test void warehouseMenuUsesRealNbtAwareStacksAndSeparateProductionAndRawStocks() throws Exception {
        String menu=source("enterprise/EnterpriseCoreMenu.java");
        String screen=source("enterprise/EnterpriseCoreScreen.java");
        String data=source("npc/KingdomEnterpriseData.java");
        assertTrue(menu.contains("ItemStack.isSameItemSameTags"));
        assertTrue(menu.contains("buf.writeItem"));
        assertTrue(menu.contains("buf.readItem()"));
        assertTrue(menu.contains("stockLines(player,data,firm.id(),EnterpriseBindingRole.RAW_STORAGE)"));
        assertTrue(menu.contains("stockLines(player,data,firm.id(),EnterpriseBindingRole.FINISHED_STORAGE)"));
        assertTrue(screen.contains("menu.rawStocks()"));
        assertTrue(screen.contains("menu.finishedStocks()"));
        assertTrue(screen.contains("g.renderItem(line.sample()"));
        assertTrue(data.contains("private Container finishedInventory"));
        assertTrue(data.contains("private Container receivingInventory"));
    }
}
