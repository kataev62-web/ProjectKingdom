package ru.projectkingdom.npc.voice;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcSpeechJobsTest {
    private static final UUID NPC = new UUID(0, 1);
    @Test void onlyNewestPendingReplyPerNpcIsSynthesized() {
        var jobs = new NpcSpeechJobs(8);
        var old = jobs.offer(NPC, "Старая", "male");
        var current = jobs.offer(NPC, "Новая", "male");
        assertFalse(jobs.isCurrent(old));
        assertTrue(jobs.isCurrent(current));
        assertSame(current, jobs.poll());
        assertNull(jobs.poll());
    }
    @Test void replacementInvalidatesEvenTheCurrentlySynthesizingReply() {
        var jobs = new NpcSpeechJobs(8);
        var old = jobs.offer(NPC, "Старая", "male");
        assertSame(old, jobs.poll());
        var next = jobs.offer(NPC, "Новая", "female");
        assertFalse(jobs.isCurrent(old));
        assertSame(next, jobs.poll());
    }
    @Test void pendingAndRetainedStateAreBoundedAcrossManyNpcs() {
        var jobs = new NpcSpeechJobs(2);
        var first = jobs.offer(NPC, "1", "male");
        var second = jobs.offer(new UUID(0, 2), "2", "male");
        jobs.offer(new UUID(0, 3), "3", "male");
        assertFalse(jobs.isCurrent(first));
        assertSame(second, jobs.poll());
        assertNotNull(jobs.poll());
        assertNull(jobs.poll());
    }
    @Test void logoutDiscardsPendingAndRunningRepliesEvenAfterReconnection() {
        var jobs = new NpcSpeechJobs(8);
        var old = jobs.offer(NPC, "До выхода", "male");
        jobs.poll();
        jobs.clear();
        assertNull(jobs.poll());
        var current = jobs.offer(NPC, "После входа", "male");
        assertFalse(jobs.isCurrent(old));
        assertTrue(jobs.isCurrent(current));
    }
    @Test void expiredPendingAndSynthesizingLinesCannotStartPlayback() {
        var clock=new java.util.concurrent.atomic.AtomicLong();
        var jobs=new NpcSpeechJobs(8,clock::get);
        var running=jobs.offer(NPC,"Долго готовится","male",100);
        jobs.poll();
        var pending=jobs.offer(new UUID(0,2),"Ждёт","male",100);
        clock.set(5_000_000_000L);
        assertFalse(jobs.isCurrent(running));
        assertFalse(jobs.isCurrent(pending));
        assertNull(jobs.poll());
        // A voice that already started can finish its sentence until replaced/logout.
        assertTrue(jobs.isLatest(running));
    }
}
