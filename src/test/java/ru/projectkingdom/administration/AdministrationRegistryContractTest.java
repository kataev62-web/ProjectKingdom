package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class AdministrationRegistryContractTest {
    @Test void nineExactCoreIdsShareOneRegistryAndEntity() throws Exception {
        List<String> expected=List.of("town_hall_core","treasury_core","bank_core","central_warehouse_core",
                "city_market_core","land_office_core","court_core","prison_core","guard_administration_core");
        assertEquals(expected,java.util.Arrays.stream(AdministrativeKind.values()).map(AdministrativeKind::blockId).toList());
        String source=Files.readString(Path.of("src/main/java/ru/projectkingdom/administration/KingdomAdministrationRegistry.java"));
        for(String id:expected)assertTrue(source.contains("\""+id+"\""),id);
        assertTrue(source.contains("BlockEntityType.Builder.of(AdministrativeCoreBlockEntity::new"));
    }
    @Test void boundItemUsesStableIdentityKeys() {
        assertEquals("KingdomAdministrativeTown",AdministrativeCoreItemData.TOWN_ID);
        assertEquals("KingdomAdministrativeOwner",AdministrativeCoreItemData.OWNER);
        assertEquals("KingdomAdministrativeKind",AdministrativeCoreItemData.KIND);
    }
}
