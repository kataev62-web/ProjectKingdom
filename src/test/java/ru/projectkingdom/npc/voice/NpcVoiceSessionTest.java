package ru.projectkingdom.npc.voice;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcVoiceSessionTest {
    @Test void automaticFinishAndKeyReleaseOnlyRecognizeOnce() {
        var session = new NpcVoiceSession();
        long token = session.begin();
        assertTrue(session.finish(token));
        assertFalse(session.finish(token));
        assertTrue(session.complete(token));
        assertFalse(session.complete(token));
    }
    @Test void cannotStartAnotherCaptureUntilRecognitionCompletes() {
        var session = new NpcVoiceSession();
        long token = session.begin();
        assertEquals(-1, session.begin());
        session.finish(token);
        assertEquals(-1, session.begin());
        session.complete(token);
        assertTrue(session.begin() > token);
    }
    @Test void worldChangeDiscardsOldRecognitionWithoutTouchingNewSession() {
        var session = new NpcVoiceSession();
        long old = session.begin();
        session.finish(old);
        session.cancel();
        long current = session.begin();
        assertFalse(session.complete(old));
        assertTrue(session.isRecording());
        assertTrue(session.finish(current));
        assertTrue(session.complete(current));
    }
}
