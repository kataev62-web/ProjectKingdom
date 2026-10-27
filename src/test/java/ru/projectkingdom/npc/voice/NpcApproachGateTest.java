package ru.projectkingdom.npc.voice;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcApproachGateTest {
    private final UUID npc = new UUID(0,1), other = new UUID(0,2);
    private final UUID player = new UUID(1,1), observer = new UUID(1,2);
    private NpcApproachGate.Encounter at(UUID n, double distance, boolean visible, UUID... listeners) {
        return new NpcApproachGate.Encounter(n,player,distance,visible,List.of(listeners));
    }
    @Test void greetsOnEntryButNotWhileStandingEvenAfterCooldown() {
        var gate = new NpcApproachGate(); var encounter=at(npc,25,true,player);
        assertEquals(List.of(encounter),gate.select(0,List.of(encounter)));
        assertTrue(gate.select(2400,List.of(encounter)).isEmpty());
    }
    @Test void jitterDoesNotRearmButLeavingEightBlocksDoes() {
        var gate=new NpcApproachGate();var e=at(npc,16,true,player);
        gate.select(0,List.of(e));gate.select(1500,List.of(at(npc,36,true,player)));
        assertTrue(gate.select(1501,List.of(e)).isEmpty());
        gate.select(1502,List.of(at(npc,65,true,player)));
        assertEquals(List.of(e),gate.select(1503,List.of(e)));
    }
    @Test void returningTooSoonDoesNotCreateDelayedGreeting() {
        var gate=new NpcApproachGate();var e=at(npc,4,true,player);
        gate.select(0,List.of(e));gate.select(20,List.of());
        assertTrue(gate.select(40,List.of(e)).isEmpty());
        assertTrue(gate.select(1300,List.of(e)).isEmpty());
        gate.select(1301,List.of());assertEquals(1,gate.select(1302,List.of(e)).size());
    }
    @Test void nearestWinsAndCrowdDoesNotBecomeAQueue() {
        var gate=new NpcApproachGate();var near=at(npc,4,true,player);var far=at(other,9,true,player);
        assertEquals(List.of(near),gate.select(0,List.of(far,near)));
        assertTrue(gate.select(300,List.of(near,far)).isEmpty());
    }
    @Test void overlappingAudiencesStayQuietAcrossDifferentTargetPlayers() {
        var gate=new NpcApproachGate();var first=at(npc,4,true,player,observer);
        var second=new NpcApproachGate.Encounter(other,observer,9,true,List.of(player,observer));
        assertEquals(List.of(first),gate.select(0,List.of(first,second)));
    }
    @Test void independentAudiencesCanHearDifferentCitizensAndNpcCannotAddressTwoAtOnce() {
        var gate=new NpcApproachGate();var a=at(npc,4,true,player);
        var b=new NpcApproachGate.Encounter(other,observer,4,true,List.of(observer));
        assertEquals(2,gate.select(0,List.of(a,b)).size());
        gate.clear();var same=new NpcApproachGate.Encounter(npc,observer,9,true,List.of(observer));
        assertEquals(1,gate.select(0,List.of(a,same)).size());
    }
    @Test void wallDoesNotGreetUntilThereIsLineOfSight() {
        var gate=new NpcApproachGate();assertTrue(gate.select(0,List.of(at(npc,4,false,player))).isEmpty());
        assertEquals(1,gate.select(20,List.of(at(npc,4,true,player))).size());
    }
    @Test void manualDialogueSuppressesApproachAndImmediateClickHello() {
        var gate=new NpcApproachGate();gate.spoken(0,npc,List.of(player));
        assertTrue(gate.select(20,List.of(at(npc,4,true,player))).isEmpty());
        assertTrue(gate.select(20,List.of(at(other,4,true,player))).isEmpty());
        assertTrue(gate.recentlySpoke(199,npc,200));assertFalse(gate.recentlySpoke(200,npc,200));
    }
    @Test void clearAndClockRollbackResetStateAndInvalidDistancesCannotSpeak() {
        var gate=new NpcApproachGate();var e=at(npc,4,true,player);gate.select(100,List.of(e));
        assertEquals(1,gate.select(0,List.of(e)).size());gate.clear();
        assertTrue(gate.select(1,List.of(at(npc,Double.NaN,true,player),at(other,-1,true,player))).isEmpty());
        assertEquals(1,gate.select(2,List.of(e)).size());
    }
    @Test void menusAndSleepingConsumeEntryWithoutCreatingAWakeOrCloseGreeting() {
        var gate=new NpcApproachGate();
        var suppressed=new NpcApproachGate.Encounter(npc,player,4,true,false,List.of(player));
        assertTrue(gate.select(0,List.of(suppressed)).isEmpty());
        assertTrue(gate.select(1500,List.of(at(npc,4,true,player))).isEmpty());
        gate.select(1501,List.of());
        assertEquals(1,gate.select(1502,List.of(at(npc,4,true,player))).size());
        assertTrue(gate.select(2800,List.of(suppressed)).isEmpty());
        assertTrue(gate.select(2820,List.of(at(npc,4,true,player))).isEmpty());
    }
}
