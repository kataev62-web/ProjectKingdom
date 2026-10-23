package ru.projectkingdom.network;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guards book entry point against accidentally trusting client coordinates or rights. */
class OpenLandMapFromBookSourceTest {
    @Test void openingIsLimitedToActiveBookAndUsesExistingAuthoritativeMenu() throws Exception {
        String source = Files.readString(Path.of("src/main/java/ru/projectkingdom/network/OpenLandMapFromBookPacket.java"));
        assertTrue(source.contains("player.containerMenu instanceof KingdomBookMenu"));
        assertTrue(source.contains("KingdomLandMapMenu.open(player)"));
        assertFalse(source.contains("readInt("));
        assertFalse(source.contains("readUUID("));
    }
}
