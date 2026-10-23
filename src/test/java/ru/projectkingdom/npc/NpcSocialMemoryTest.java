package ru.projectkingdom.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class NpcSocialMemoryTest {
    @Test void recordsConversationAndHelpWithoutOverflow() {
        var memory = NpcSocialMemory.neutral().talked("GREETING", 12).helped(13);
        assertEquals(1, memory.conversations());
        assertEquals(1, memory.helped());
        assertEquals(5, memory.reputation());
        assertEquals("HELP", memory.lastTopic());
        assertEquals(13, memory.lastDay());
        assertEquals("незнакомец", memory.label());

        var capped = new NpcSocialMemory(99, Integer.MAX_VALUE, Integer.MAX_VALUE,
                "TASK", 20).helped(21).talked("WORK", 22);
        assertEquals(100, capped.reputation());
        assertEquals(Integer.MAX_VALUE, capped.conversations());
        assertEquals(Integer.MAX_VALUE, capped.helped());
        assertEquals("союзник", capped.label());
    }

    @Test void clampsLoadedValuesAndNormalizesTopic() {
        var memory = new NpcSocialMemory(-500, -4, -2, "", -10);
        assertEquals(-100, memory.reputation());
        assertEquals(0, memory.conversations());
        assertEquals(0, memory.helped());
        assertEquals("NONE", memory.lastTopic());
        assertEquals(0, memory.lastDay());
    }
}

