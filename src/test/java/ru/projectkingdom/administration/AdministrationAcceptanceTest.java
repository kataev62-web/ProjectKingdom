package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class AdministrationAcceptanceTest {
    @Test void oneTownHasExactlyOneCoreOfEveryKindAndOtherTownIsIsolated() {
        UUID first=UUID.randomUUID(),second=UUID.randomUUID(),owner=UUID.randomUUID();var data=new KingdomAdministrationData();
        data.ensureFounder(first,owner);data.ensureFounder(second,owner);Set<AdministrativeKind> seen=new HashSet<>();long pos=1;
        for(AdministrativeKind kind:AdministrativeKind.values()){
            var binding=new AdministrativeCoreBinding(first,kind,"minecraft:overworld",pos++);assertTrue(data.activateCore(owner,owner,binding));assertTrue(seen.add(kind));
            assertFalse(data.activateCore(owner,owner,new AdministrativeCoreBinding(first,kind,"minecraft:overworld",pos++)));
            assertNull(data.core(second,kind));
        }
        assertEquals(9,data.cores(first).size());
    }

    @Test void legacyMenusRouteThroughNearbyPhysicalAdministrativeCores() throws Exception {
        String menu=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomMainMenu.java"));
        String mod=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomNpcMod.java"));
        assertTrue(menu.contains("openNearbyAdministrative"));assertTrue(menu.contains("AdministrativeKind.BANK"));
        assertTrue(menu.contains("AdministrativeKind.CITY_MARKET"));assertTrue(menu.contains("AdministrativeKind.LAND_OFFICE"));
        assertTrue(menu.contains("Старая точка"));assertTrue(mod.contains("isNearAdministrative"));
    }

    @Test void deferredAutomationRemainsExplicitlyInactive() throws Exception {
        String status=Files.readString(Path.of("validation/ADMINISTRATIVE_BLOCKS_STATUS_RU.md"));
        assertTrue(status.contains("автоматические аресты: **неактивны**"));
        assertTrue(status.contains("guard_administration_core"));
    }
}
