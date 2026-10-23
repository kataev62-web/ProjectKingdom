package ru.projectkingdom.npc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class NpcAddressingTest {
    @Test void resolvesNearestDuplicateNameAndExtractsPhrase() {
        var candidates = List.of(
                new NpcAddressing.Candidate(1, "Анна", 30, true),
                new NpcAddressing.Candidate(2, "Анна", 4, true),
                new NpcAddressing.Candidate(3, "Борис", 2, true));
        var match = NpcAddressing.match("Анна, как дела?", candidates).orElseThrow();
        assertEquals(2, match.npcId());
        assertEquals("как дела?", match.phrase());
    }

    @Test void supportsNumericAddressAndRejectsUnavailableOrUnaddressedChat() {
        var candidates = List.of(
                new NpcAddressing.Candidate(7, "Анна", 4, true),
                new NpcAddressing.Candidate(8, "Борис", 3, false));
        assertEquals("привет", NpcAddressing.match("#7 привет", candidates).orElseThrow().phrase());
        assertTrue(NpcAddressing.match("Борис, привет", candidates).isEmpty());
        assertTrue(NpcAddressing.match("Всем привет", candidates).isEmpty());
        assertTrue(NpcAddressing.match("Анна...", candidates).isEmpty());
    }

    @Test void matchingIsCaseInsensitiveAndAllowsColon() {
        var result = NpcAddressing.match("анНА: Кто ты?",
                List.of(new NpcAddressing.Candidate(4, "Анна", 1, true))).orElseThrow();
        assertEquals(4, result.npcId());
        assertEquals("Кто ты?", result.phrase());
    }
}

