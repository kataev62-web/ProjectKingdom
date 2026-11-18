package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Independent checks for Issue #82. No world, live client or TFC inventory
 * is constructed here; in-game acceptance remains separate.
 */
final class KingdomCityCashIntegrationRegressionTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @Test void townChangeDimensionChangeAndFounderRevocationAreFailClosed() {
        UUID original = UUID.randomUUID(), other = UUID.randomUUID();
        UUID founder = UUID.randomUUID(), successor = UUID.randomUUID();
        var session = new KingdomCityMapSession(original, OVERWORLD);
        assertTrue(session.mayPlan(original, OVERWORLD, founder, founder));
        assertFalse(session.mayPlan(other, OVERWORLD, founder, founder));
        assertFalse(session.mayPlan(original, NETHER, founder, founder));
        assertFalse(session.mayPlan(original, OVERWORLD, founder, successor));
        assertFalse(session.mayPlan(original, OVERWORLD, successor, founder));
        assertFalse(session.mayPlan(null, OVERWORLD, founder, founder));
        assertFalse(session.mayPlan(original, null, founder, founder));
        assertFalse(session.mayPlan(original, OVERWORLD, founder, null));
        assertFalse(new KingdomCityMapSession(null, OVERWORLD)
                .mayPlan(original, OVERWORLD, founder, founder));
    }

    @Test void exactSelectionKeepsPhysicalCountsAndDoesNotMintChange() {
        assertEquals(new PhysicalCoinSelection.Coins(0, 0, 100),
                PhysicalCoinSelection.select(100, 0, 0, 100));
        assertEquals(new PhysicalCoinSelection.Coins(0, 1, 0),
                PhysicalCoinSelection.select(100, 0, 1, 100));
        assertEquals(new PhysicalCoinSelection.Coins(1, 2, 1),
                PhysicalCoinSelection.select(10_201, 2, 3, 5));
        assertNull(PhysicalCoinSelection.select(100, 1, 0, 0));
        assertNull(PhysicalCoinSelection.select(99, 0, 1, 0));
        assertNull(PhysicalCoinSelection.select(0, 1, 1, 1));
        assertNull(PhysicalCoinSelection.select(1_000_001, 100, 100, 100));
        assertEquals(new PhysicalCoinSelection.Coins(100, 0, 0),
                PhysicalCoinSelection.select(1_000_000, 100, 0, 0));
    }

    @Test void exactSelectionAgreesWithIndependentExhaustiveEnumeration() {
        Random random = new Random(0x82BEEFL);
        for (int caseNo = 0; caseNo < 700; caseNo++) {
            long gold = random.nextInt(5);
            long silver = random.nextInt(15);
            long copper = random.nextInt(220);
            long amount = 1 + random.nextInt(1700);
            boolean possible = false;
            for (long g = 0; g <= gold; g++) {
                for (long s = 0; s <= silver; s++) {
                    long rest = amount - 10_000 * g - 100 * s;
                    if (rest >= 0 && rest <= copper) possible = true;
                }
            }
            var chosen = PhysicalCoinSelection.select(amount, gold, silver, copper);
            assertEquals(possible, chosen != null, "selection case " + caseNo);
            if (chosen != null) {
                assertTrue(chosen.gold() <= gold);
                assertTrue(chosen.silver() <= silver);
                assertTrue(chosen.copper() <= copper);
                assertEquals(amount, chosen.copperValue());
            }
        }
    }

    @Test void authoritativeMenuAndCashSourceContractsRemainPresent() throws Exception {
        String menu = Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/npc/KingdomCityExpansionMapMenu.java"));
        String cash = Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/npc/KingdomPhysicalCash.java"));
        assertTrue(menu.contains("session = new KingdomCityMapSession(cityId"));
        assertTrue(menu.contains("session.matches(town == null ? null : town.id(), currentDimension())"));
        assertTrue(menu.contains("session.mayPlan(town.id(), currentDimension()"));
        assertTrue(menu.contains("if (!stillValid(player)) { viewer.closeContainer(); return; }"));
        assertTrue(menu.contains("player == viewer && player.isAlive() && sessionValid()"));
        assertTrue(menu.contains("KingdomPhysicalCash.townVault("));
        assertTrue(cash.contains("planExactMoveDeposit(source,taken,target)"));
        assertTrue(cash.contains("if(!present.is(coin) || present.hasTag())continue;"));
        assertTrue(cash.contains("!vault.getItem(i).hasTag()"));
        assertTrue(cash.contains("if(!container.canPlaceItem(i,candidate))continue;"));
        assertTrue(cash.contains("if(!container.canPlaceItem(i,single))continue;"));
        assertTrue(cash.contains("apply(source,taken);"));
        assertTrue(cash.contains("apply(target,added);"));
    }
}
