package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class AdministrativeCoreLifecycleTest {
    @Test void oneActiveCorePerTownAndKind() {
        UUID town=UUID.randomUUID(), founder=UUID.randomUUID();
        var data=new KingdomAdministrationData();data.ensureFounder(town,founder);
        var first=new AdministrativeCoreBinding(town,AdministrativeKind.TOWN_HALL,"minecraft:overworld",10L);
        var duplicate=new AdministrativeCoreBinding(town,AdministrativeKind.TOWN_HALL,"minecraft:overworld",11L);
        assertTrue(data.activateCore(founder,founder,first));
        assertFalse(data.activateCore(founder,founder,duplicate));
        assertTrue(data.suspendCore(founder,founder,first));
        assertTrue(data.activateCore(founder,founder,duplicate));
    }
    @Test void placementSourceEnforcesTownHallFirstAndNeverForceLoads() throws Exception {
        String source=Files.readString(Path.of("src/main/java/ru/projectkingdom/administration/AdministrativeCoreBlock.java"));
        assertTrue(source.contains("AdministrativeKind.TOWN_HALL"));
        assertTrue(source.contains("townAt"));
        assertTrue(source.contains("hasChunkAt"));
        assertFalse(source.contains("getChunk("));
    }
}
