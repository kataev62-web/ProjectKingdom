package ru.projectkingdom.npc.voice;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcSpeechTrackingQueueTest {
    private final UUID npc = new UUID(0, 1), other = new UUID(0, 2);

    @Test void speechWaitsForTrackedEntityAndStartsExactlyOnceAfterSpawn() {
        var queue = new NpcSpeechTrackingQueue<String>(4, 40);
        queue.offer(npc, "greeting", 100);
        assertTrue(queue.takeReady(101, id -> false, line -> fail("not expired")).isEmpty());
        assertEquals(List.of("greeting"), queue.takeReady(102, npc::equals, line -> fail("not expired")));
        assertTrue(queue.takeReady(103, npc::equals, line -> fail("already played")).isEmpty());
    }

    @Test void expiredUntrackedSpeechCannotPlayLaterAndReportsExpiryOnce() {
        var queue = new NpcSpeechTrackingQueue<String>(4, 40);
        var expired = new ArrayList<String>();
        queue.offer(npc, "old greeting", 100);
        assertTrue(queue.takeReady(140, id -> true, expired::add).isEmpty());
        assertEquals(List.of("old greeting"), expired);
        assertTrue(queue.takeReady(141, id -> true, expired::add).isEmpty());
        assertEquals(1, expired.size());
    }

    @Test void newerReplyReplacesWaitingSpeechForSameNpc() {
        var queue = new NpcSpeechTrackingQueue<String>(4, 40);
        queue.offer(npc, "old", 100);
        queue.offer(npc, "new", 120);
        assertEquals(List.of("new"), queue.takeReady(145, id -> true, line -> fail("new reply is fresh")));
    }

    @Test void manualReplyCanCancelPendingAutomaticSpeechWithoutLosingOtherManualReplies() {
        var queue = new NpcSpeechTrackingQueue<String>(4, 40);
        queue.offer(npc, "automatic", 100);
        queue.offer(other, "manual", 100);
        assertTrue(queue.anyMatch("manual"::equals));
        queue.removeIf("automatic"::equals);
        assertEquals(List.of("manual"), queue.takeReady(101, id -> true, line -> fail("not expired")));
        assertFalse(queue.anyMatch(id -> true));
    }

    @Test void pendingSpeechIsBoundedAndReplacementKeepsNewestNpcInQueue() {
        var queue = new NpcSpeechTrackingQueue<String>(2, 40);
        queue.offer(npc, "first", 0);
        queue.offer(other, "second", 0);
        queue.offer(npc, "replacement", 1);
        queue.offer(new UUID(0, 3), "third", 1);
        assertEquals(List.of("replacement", "third"), queue.takeReady(2, id -> true, line -> fail("not expired")));
    }

    @Test void replacementLogoutAndWorldChangeDiscardWaitingSpeech() {
        var queue = new NpcSpeechTrackingQueue<String>(4, 40);
        queue.offer(npc, "cancelled", 100);
        queue.offer(other, "previous world", 100);
        queue.remove(npc);
        assertFalse(queue.anyMatch("cancelled"::equals));
        queue.clear();
        assertTrue(queue.takeReady(101, id -> true, line -> fail("discarded")).isEmpty());
    }
}
