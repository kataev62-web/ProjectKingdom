package ru.projectkingdom.npc.voice;
import org.junit.jupiter.api.Test;
import ru.projectkingdom.npc.NpcDialogueEngine;
import static org.junit.jupiter.api.Assertions.*;
class NpcApproachLinesTest {
    private NpcDialogueEngine.Snapshot state(String job,boolean employed,int hunger,int energy,int safety,boolean home,boolean paused) {
        return new NpcDialogueEngine.Snapshot("Анна",job,hunger,energy,90,safety,employed,paused,home,true,"wander",0);
    }
    @Test void healthyCitizensAskAboutTheDayAndVaryFriendlyLines() {
        var s=state("farmer",false,90,90,90,true,false);
        assertEquals("how_day",NpcApproachLines.choose(s,0).id());
        assertEquals(NpcVoiceLine.GREETING,NpcApproachLines.choose(s,1));
        assertEquals(NpcVoiceLine.RELATIONSHIP,NpcApproachLines.choose(s,2));
        assertEquals(NpcApproachLines.choose(s,2),NpcApproachLines.choose(s,-1));
    }
    @Test void urgentNeedsOverrideSmallTalk() {
        assertEquals(NpcVoiceLine.DANGER,NpcApproachLines.choose(state("free",false,1,1,1,false,false),0));
        assertEquals(NpcVoiceLine.HUNGRY,NpcApproachLines.choose(state("free",false,1,90,90,false,false),0));
        assertEquals(NpcVoiceLine.TIRED,NpcApproachLines.choose(state("free",false,90,1,90,false,false),0));
    }
    @Test void onlyActuallyJoblessCitizensAskForWorkAndNeedsRemainHonest() {
        assertEquals(NpcVoiceLine.JOB_SEEKING,NpcApproachLines.choose(state("free",false,90,90,90,true,false),1));
        assertNotEquals(NpcVoiceLine.JOB_SEEKING,NpcApproachLines.choose(state("farmer",false,90,90,90,true,false),1));
        assertEquals(NpcVoiceLine.WAGES,NpcApproachLines.choose(state("farmer",true,90,90,90,true,true),1));
        assertEquals(NpcVoiceLine.HOME,NpcApproachLines.choose(state("farmer",false,90,90,90,false,false),1));
        assertNotEquals(NpcVoiceLine.WAGES,NpcApproachLines.choose(state("farmer",false,90,90,90,true,true),1));
    }
}
