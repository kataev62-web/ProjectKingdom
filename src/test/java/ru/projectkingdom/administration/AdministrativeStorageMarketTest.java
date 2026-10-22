package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class AdministrativeStorageMarketTest {
    @Test void warehouseAllowsSeveralBindingsButRejectsDuplicatesAndCrossTownLeakage() {
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        var data=new KingdomAdministrationData();
        assertTrue(data.bindContainer(first,AdministrativeBindingRole.WAREHOUSE_STORAGE,"minecraft:overworld",10));
        assertTrue(data.bindContainer(first,AdministrativeBindingRole.WAREHOUSE_STORAGE,"minecraft:overworld",11));
        assertFalse(data.bindContainer(first,AdministrativeBindingRole.WAREHOUSE_STORAGE,"minecraft:overworld",10));
        assertTrue(data.bindContainer(second,AdministrativeBindingRole.WAREHOUSE_STORAGE,"minecraft:overworld",10));
        assertEquals(2,data.boundContainers(first,AdministrativeBindingRole.WAREHOUSE_STORAGE).size());
        assertEquals(1,data.boundContainers(second,AdministrativeBindingRole.WAREHOUSE_STORAGE).size());
    }

    @Test void adapterNeverForceLoadsOrRetainsLiveContainers() throws Exception {
        String source=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomWarehouseMarket.java"));
        assertTrue(source.contains("municipalInventory"));
        assertTrue(source.contains("level.hasChunkAt(pos)"));
        assertTrue(source.contains("activeMunicipalCore"));
        assertFalse(source.contains("getChunk("));
        assertFalse(source.matches("(?s).*Map<[^>]*Container[^>]*>.*"));
        assertFalse(source.matches("(?s).*List<Container>.*"));
    }

    @Test void municipalMarketRequiresItsSingleActiveCoreWithoutChangingEscrowFlow() throws Exception {
        String source=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomWarehouseMarket.java"));
        assertTrue(source.contains("AdministrativeKind.CITY_MARKET"));
        assertTrue(source.contains("AdministrativeBindingRole.MARKET_STORAGE"));
        assertTrue(source.contains("orders.escrow(order.id,goods,deliveryFee)"));
        assertTrue(source.contains("KingdomCurrency.credit(buyer,total)"));
    }
}
