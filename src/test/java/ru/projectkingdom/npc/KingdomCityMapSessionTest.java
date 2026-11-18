package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Prevent a stale planning GUI from modifying another city's plan or funds. */
final class KingdomCityMapSessionTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @Test void openingCityAndDimensionRemainBoundToTheMenu() {
        UUID firstTown = UUID.randomUUID();
        UUID secondTown = UUID.randomUUID();
        var session = new KingdomCityMapSession(firstTown, OVERWORLD);

        assertTrue(session.matches(firstTown, OVERWORLD));
        assertFalse(session.matches(secondTown, OVERWORLD));
        assertFalse(session.matches(firstTown, NETHER));
        assertFalse(session.matches(null, OVERWORLD));
        assertFalse(session.matches(firstTown, null));
    }

    @Test void foundingAnotherCityNeverAuthorizesTheFormerCitySession() {
        UUID firstTown = UUID.randomUUID(), secondTown = UUID.randomUUID();
        UUID player = UUID.randomUUID(), anotherPlayer = UUID.randomUUID();
        var session = new KingdomCityMapSession(firstTown, OVERWORLD);

        assertTrue(session.mayPlan(firstTown, OVERWORLD, player, player));
        assertFalse(session.mayPlan(secondTown, OVERWORLD, player, player));
        assertFalse(session.mayPlan(firstTown, NETHER, player, player));
        assertFalse(session.mayPlan(firstTown, OVERWORLD, player, anotherPlayer));
        assertFalse(session.mayPlan(firstTown, OVERWORLD, null, null));
        assertFalse(new KingdomCityMapSession(null, OVERWORLD)
            .mayPlan(firstTown, OVERWORLD, player, player));
    }

    @Test void guiChecksTheBoundSessionBeforeAnyMutation() throws Exception {
        String code = Files.readString(Path.of(
            "src/main/java/ru/projectkingdom/npc/KingdomCityExpansionMapMenu.java"));
        assertTrue(code.contains("session = new KingdomCityMapSession(cityId"));
        assertTrue(code.contains("session.matches(town == null ? null : town.id(), currentDimension())"));
        assertTrue(code.contains("session.mayPlan(town.id(), currentDimension()"));
        assertTrue(code.contains("if (!stillValid(player)) { viewer.closeContainer(); return; }"));
        assertTrue(code.contains("player == viewer && player.isAlive() && sessionValid()"));
    }
}
