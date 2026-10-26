package ru.projectkingdom.npc.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class NpcSpeechTimingTest {
    @Test void clampsEmptyAndNullTextToFiveSeconds() {
        assertEquals(100,NpcSpeechTiming.durationTicks(null));
        assertEquals(100,NpcSpeechTiming.durationTicks(""));
    }

    @Test void addsOneTickPerTwoUnicodeCharacters() {
        assertEquals(110,NpcSpeechTiming.durationTicks("я".repeat(20)));
    }

    @Test void clampsLongTextToEightSeconds() {
        assertEquals(160,NpcSpeechTiming.durationTicks("я".repeat(200)));
    }
}
