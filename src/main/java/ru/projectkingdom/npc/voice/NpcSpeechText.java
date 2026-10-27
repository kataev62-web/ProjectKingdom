package ru.projectkingdom.npc.voice;
/** Server presentation bounds; packet decoding still rejects malformed wire input. */
public final class NpcSpeechText {
    private NpcSpeechText() {}
    public static String limit(String text,int maximum,String fallback) {
        String value=text==null?"":text.strip();
        if (value.isEmpty()) value=fallback;
        if (value.length()<=maximum) return value;
        int end=maximum;
        if (end>0 && Character.isHighSurrogate(value.charAt(end-1))) end--;
        return value.substring(0,end);
    }
}
