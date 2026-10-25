package ru.projectkingdom.npc.voice;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class NpcVoiceRateLimiterTest {
    @Test void recordsOnlyAcceptedRequestsAndEnforcesCooldownPerPlayer() {
        var limiter=new NpcVoiceRateLimiter();
        UUID first=UUID.randomUUID(), second=UUID.randomUUID();
        assertTrue(limiter.validateAndRecord(first,"Анна, привет",100).accepted());
        assertFalse(limiter.validateAndRecord(first,"Как дела?",139).accepted());
        assertTrue(limiter.validateAndRecord(first,"Как дела?",140).accepted());
        assertTrue(limiter.validateAndRecord(second,"Как дела?",101).accepted());
    }

    @Test void invalidTextDoesNotConsumeCooldownAndLogoutForgetsPlayer() {
        var limiter=new NpcVoiceRateLimiter();
        UUID player=UUID.randomUUID();
        assertFalse(limiter.validateAndRecord(player," ",100).accepted());
        assertTrue(limiter.validateAndRecord(player,"Привет",101).accepted());
        assertFalse(limiter.validateAndRecord(player,"Ещё раз",102).accepted());
        limiter.forget(player);
        assertTrue(limiter.validateAndRecord(player,"После входа",103).accepted());
    }

    @Test void cacheNeverGrowsPastItsHardLimit() {
        var limiter=new NpcVoiceRateLimiter(4);
        for(int i=0;i<20;i++)
            assertTrue(limiter.validateAndRecord(new UUID(0,i+1),"Привет",i).accepted());
        assertEquals(4,limiter.size());
    }
}
