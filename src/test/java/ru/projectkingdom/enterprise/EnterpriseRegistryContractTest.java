package ru.projectkingdom.enterprise;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class EnterpriseRegistryContractTest {
    @Test void allPrivateCoreIdsHaveOneSharedRegistryAndEntity() throws Exception {
        List<String> expected=List.of("farm_core","mine_core","lumber_core","smithy_core",
                "bakery_core","pottery_core","construction_core","courier_core",
                "hotel_core","tavern_core");
        assertEquals(expected,EnterpriseKind.placeableKinds().stream().map(EnterpriseKind::blockId).toList());
        String source=Files.readString(Path.of("src/main/java/ru/projectkingdom/enterprise/KingdomEnterpriseRegistry.java"));
        for(String id:expected)assertTrue(source.contains("\""+id+"\""),id);
        assertFalse(source.contains("\"warehouse_core\""));
        assertFalse(source.contains("\"market_core\""));
        assertTrue(source.contains("BlockEntityType.Builder.of(EnterpriseCoreBlockEntity::new"));
    }

    @Test void boundItemUsesStableIdentityKeys() {
        assertEquals("KingdomEnterpriseId",EnterpriseCoreItemData.ENTERPRISE_ID);
        assertEquals("KingdomEnterpriseOwner",EnterpriseCoreItemData.OWNER);
        assertEquals("KingdomEnterpriseKind",EnterpriseCoreItemData.KIND);
    }
}
