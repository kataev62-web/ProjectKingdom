package ru.projectkingdom.npc.voice;

/** Pure validation for client-originated speech before it can reach NPC logic. */
public final class NpcVoiceRequestPolicy {
    public static final int MAX_TEXT_LENGTH = 256;
    public static final long MIN_INTERVAL_TICKS = 40L;

    private NpcVoiceRequestPolicy() {}

    public enum Reason { ACCEPTED, BLANK, TOO_LONG, COOLDOWN }

    public record Validation(boolean accepted, String text, Reason reason) {}

    public static Validation validate(String rawText, long nowTick, long lastAcceptedTick) {
        String text = rawText == null ? "" : rawText.strip();
        if (text.isEmpty()) return new Validation(false, "", Reason.BLANK);
        if (text.codePointCount(0, text.length()) > MAX_TEXT_LENGTH)
            return new Validation(false, "", Reason.TOO_LONG);
        if (lastAcceptedTick >= 0L && (nowTick < lastAcceptedTick
                || nowTick - lastAcceptedTick < MIN_INTERVAL_TICKS))
            return new Validation(false, "", Reason.COOLDOWN);
        return new Validation(true, text, Reason.ACCEPTED);
    }
}
