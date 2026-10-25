package ru.projectkingdom.npc.voice;

/** Shared deterministic visibility duration for NPC speech bubbles. */
public final class NpcSpeechTiming {
    public static final int MIN_TICKS = 100;
    public static final int MAX_TICKS = 160;

    private NpcSpeechTiming() {}

    public static int durationTicks(String text) {
        int codePoints = text == null ? 0 : text.codePointCount(0, text.length());
        return Math.min(MAX_TICKS, MIN_TICKS + codePoints / 2);
    }
}
