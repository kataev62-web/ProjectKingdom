package ru.projectkingdom.npc.voice;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class NpcSpeechPlaybackTest {
    @Test void attenuationAndPanAreBounded() {
        assertEquals(Double.valueOf(1.0D),Double.valueOf(NpcSpeechSpatialAudio.volume(0.0D)));
        assertEquals(Double.valueOf(0.5D),Double.valueOf(NpcSpeechSpatialAudio.volume(8.0D)));
        assertEquals(Double.valueOf(0.0D),Double.valueOf(NpcSpeechSpatialAudio.volume(16.0D)));
        assertEquals(Double.valueOf(0.0D),Double.valueOf(NpcSpeechSpatialAudio.volume(40.0D)));
        assertEquals(Double.valueOf(1.0D),Double.valueOf(NpcSpeechSpatialAudio.pan(2.0D,2.0D)));
        assertEquals(Double.valueOf(-1.0D),Double.valueOf(NpcSpeechSpatialAudio.pan(-2.0D,2.0D)));
        assertEquals(Double.valueOf(0.0D),Double.valueOf(NpcSpeechSpatialAudio.pan(1.0D,0.0D)));
    }

    @Test void newerSpeechClosesAndReplacesTheSameNpc() {
        var queue=new NpcSpeechPlaybackQueue<Probe>(2);
        UUID npc=UUID.randomUUID();
        Probe oldSpeech=new Probe(),newSpeech=new Probe();
        queue.put(npc,oldSpeech);
        queue.put(npc,newSpeech);
        assertTrue(oldSpeech.closed);
        assertFalse(newSpeech.closed);
        assertEquals(1,queue.size());
    }

    @Test void queueEvictsOldestAtItsHardLimitAndClearClosesEverything() {
        var queue=new NpcSpeechPlaybackQueue<Probe>(2);
        Probe first=new Probe(),second=new Probe(),third=new Probe();
        queue.put(new UUID(0,1),first);
        queue.put(new UUID(0,2),second);
        queue.put(new UUID(0,3),third);
        assertTrue(first.closed);
        assertEquals(2,queue.size());
        queue.clear();
        assertTrue(second.closed);
        assertTrue(third.closed);
        assertEquals(0,queue.size());
    }

    @Test void lookingDownDoesNotMoveNpcVoiceTowardCenter() {
        assertEquals(-1.0D,NpcSpeechSpatialAudio.pan(1,0,0,1),0.0001);
        assertEquals(-1.0D,NpcSpeechSpatialAudio.pan(1,0,0,0.1),0.0001);
        assertEquals(1.0D,NpcSpeechSpatialAudio.pan(-1,0,0,1),0.0001);
        assertEquals(0.0D,NpcSpeechSpatialAudio.pan(0,0,0,0),0.0001);
    }

    @Test void stoppingOneNpcClosesOnlyThatPlayback() {
        var queue=new NpcSpeechPlaybackQueue<Probe>(2);
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        Probe one=new Probe(),two=new Probe();
        queue.put(first,one);queue.put(second,two);
        var snapshot=queue.entries();
        queue.stop(first);
        assertTrue(one.closed);
        assertFalse(two.closed);
        assertEquals(1,queue.size());
        assertEquals(2,snapshot.size());
    }

    private static final class Probe implements AutoCloseable {
        private boolean closed;
        @Override public void close(){closed=true;}
    }
}
