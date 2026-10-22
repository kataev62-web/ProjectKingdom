package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class AdministrativeMenuContractTest {
    @Test void menuValidityRequiresExactLoadedActiveCore() throws Exception {
        String source=Files.readString(Path.of("src/main/java/ru/projectkingdom/administration/AdministrativeCoreMenu.java"));
        assertTrue(source.contains("hasChunkAt(corePos)"));
        assertTrue(source.contains("distSqr(player.blockPosition())>64"));
        assertTrue(source.contains("data.core(townId,kind)"));
        assertTrue(source.contains("AdministrativeCoreBlockEntity"));
    }
    @Test void screenUsesScopedReadableFont() throws Exception {
        String source=Files.readString(Path.of("src/main/java/ru/projectkingdom/administration/AdministrativeCoreScreen.java"));
        assertTrue(source.contains("KingdomFonts.readable"));
        assertFalse(source.contains("minecraft:default"));
    }
}
