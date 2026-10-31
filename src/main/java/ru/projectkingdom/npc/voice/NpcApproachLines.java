package ru.projectkingdom.npc.voice;
import ru.projectkingdom.npc.NpcDialogueEngine;
public final class NpcApproachLines {
    private NpcApproachLines() {}
    public static NpcVoiceLine choose(NpcDialogueEngine.Snapshot s,int variation) {
        if (s.safety()<25) return NpcVoiceLine.DANGER;
        if (s.hunger()<30) return NpcVoiceLine.HUNGRY;
        if (s.energy()<25) return NpcVoiceLine.TIRED;
        int topic=Math.floorMod(variation,3);
        if (topic==0) return NpcVoiceLine.HOW_DAY;
        if (topic==2) return NpcVoiceLine.RELATIONSHIP;
        if (!s.employed() && "free".equals(s.job())) return NpcVoiceLine.JOB_SEEKING;
        if (s.employed() && s.contractPaused()) return NpcVoiceLine.WAGES;
        if (!s.hasHome()) return NpcVoiceLine.HOME;
        if (s.hunger()<60) return NpcVoiceLine.FOOD_HELP;
        if (s.employed() && !s.hasWorkstation()) return NpcVoiceLine.WORKSTATION;
        return NpcVoiceLine.GREETING;
    }
}
