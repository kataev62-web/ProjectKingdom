package ru.projectkingdom.npc.voice;

/** Main-thread capture lifecycle; late async results cannot finish a newer session. */
public final class NpcVoiceSession {
    private enum State { IDLE, RECORDING, RECOGNIZING }
    private State state = State.IDLE;
    private long generation;

    public long begin() {
        if (state != State.IDLE) return -1;
        state = State.RECORDING;
        return ++generation;
    }

    public boolean finish(long token) {
        if (token != generation || state != State.RECORDING) return false;
        state = State.RECOGNIZING;
        return true;
    }

    public boolean complete(long token) {
        if (token != generation || state != State.RECOGNIZING) return false;
        state = State.IDLE;
        return true;
    }

    public void cancel() { ++generation; state = State.IDLE; }
    public boolean isRecording() { return state == State.RECORDING; }
}
