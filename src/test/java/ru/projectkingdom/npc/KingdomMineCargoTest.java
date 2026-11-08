package ru.projectkingdom.npc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomMineCargoTest {
    @Test void realMiningSuppliesAreNeverFinishedOre() {
        assertTrue(KingdomMineCargo.isSupplyId("minecraft:torch"));
        assertTrue(KingdomMineCargo.isSupplyId("minecraft:ladder"));
        assertTrue(KingdomMineCargo.isSupplyId("minecraft:oak_planks"));
        assertTrue(KingdomMineCargo.isSupplyId("tfc:wood/planks/oak"));
        assertTrue(KingdomMineCargo.isSupplyId("tfc:wood/support/oak"));
        assertTrue(KingdomMineCargo.isSupplyId("tfc:wood/support/ash"));
        assertFalse(KingdomMineCargo.isSupplyId("minecraft:coal"));
        assertFalse(KingdomMineCargo.isSupplyId("tfc:rock/raw/granite"));
        assertFalse(KingdomMineCargo.isSupplyId("minecraft:iron_ore"));
    }
}
