package ru.projectkingdom.enterprise;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class EnterpriseAccessServiceTest {
    @Test void exposesThreeDistinctPhysicalRolesAndFailClosedStatuses() {
        assertArrayEquals(new EnterpriseBindingRole[]{EnterpriseBindingRole.CASHBOX,
                EnterpriseBindingRole.RAW_STORAGE,EnterpriseBindingRole.FINISHED_STORAGE},
                EnterpriseBindingRole.values());
        assertNotNull(EnterpriseAccessService.Status.valueOf("BOUND_AVAILABLE"));
        assertNotNull(EnterpriseAccessService.Status.valueOf("BOUND_UNLOADED"));
        assertNotNull(EnterpriseAccessService.Status.valueOf("MISSING"));
        assertNotNull(EnterpriseAccessService.Status.valueOf("FOREIGN_TERRITORY"));
        assertNotNull(EnterpriseAccessService.Status.valueOf("INVALID_CONTAINER"));
    }

    @Test void serviceNeverForceLoadsAChunk() throws Exception {
        String source=Files.readString(Path.of("src/main/java/ru/projectkingdom/enterprise/EnterpriseAccessService.java"));
        assertTrue(source.contains("hasChunkAt"));
        assertFalse(source.contains("getChunk("));
        assertFalse(source.contains("addRegionTicket"));
    }

    @Test void everyBindingAttemptConsumesThePendingSession() throws Exception {
        String source=Files.readString(Path.of("src/main/java/ru/projectkingdom/enterprise/EnterpriseAccessService.java"));
        assertTrue(source.contains("EnterpriseBindingSession session=SESSIONS.remove(player.getUUID())"),
                "an invalid target must not leave the player trapped in binding mode");
        assertFalse(source.contains("EnterpriseBindingSession session=SESSIONS.get(player.getUUID())"));
    }
}
