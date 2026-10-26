package ru.projectkingdom.npc.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class NpcVoiceRequestPolicyTest {
    @Test void rejectsBlankAndNullText() {
        assertFalse(NpcVoiceRequestPolicy.validate(null,100,-1).accepted());
        assertFalse(NpcVoiceRequestPolicy.validate("   ",100,-1).accepted());
    }

    @Test void countsUnicodeCodePointsAtThe256CharacterBoundary() {
        String accepted="я".repeat(256);
        String rejected=accepted+"я";
        assertTrue(NpcVoiceRequestPolicy.validate(accepted,100,-1).accepted());
        assertFalse(NpcVoiceRequestPolicy.validate(rejected,100,-1).accepted());
        assertEquals(NpcVoiceRequestPolicy.Reason.TOO_LONG,
                NpcVoiceRequestPolicy.validate(rejected,100,-1).reason());
    }

    @Test void enforcesFortyTickCooldownUsingServerTimeOnly() {
        assertFalse(NpcVoiceRequestPolicy.validate("Анна, привет",139,100).accepted());
        assertEquals(NpcVoiceRequestPolicy.Reason.COOLDOWN,
                NpcVoiceRequestPolicy.validate("Анна, привет",139,100).reason());
        assertTrue(NpcVoiceRequestPolicy.validate("Анна, привет",140,100).accepted());
    }

    @Test void trimsAcceptedRussianText() {
        var result=NpcVoiceRequestPolicy.validate("  Как дела?  ",100,-1);
        assertTrue(result.accepted());
        assertEquals("Как дела?",result.text());
        assertEquals(NpcVoiceRequestPolicy.Reason.ACCEPTED,result.reason());
    }
}
