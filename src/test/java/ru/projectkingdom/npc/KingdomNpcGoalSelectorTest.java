package ru.projectkingdom.npc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ru.projectkingdom.npc.KingdomNpcGoalSelector.*;

class KingdomNpcGoalSelectorTest {
    @Test void urgentNeedsPreemptWork() {
        assertEquals(Goal.EAT, choose(new Context(true,true,true,true,true,20,90)));
        assertEquals(Goal.REST, choose(new Context(true,true,true,true,true,90,20)));
    }

    @Test void workRequiresDaytimeActiveContractAndStation() {
        assertEquals(Goal.WORK, choose(new Context(true,true,true,true,true,80,80)));
        assertEquals(Goal.RETURN_HOME, choose(new Context(true,true,true,false,true,80,80)));
        assertEquals(Goal.IDLE, choose(new Context(true,false,true,true,false,80,80)));
        assertEquals(Goal.IDLE, choose(new Context(true,true,false,true,false,80,80)));
    }

    @Test void evaluationIsThrottledAndRecoversFromTimeReset() {
        assertTrue(due(100, -1, 20));
        assertFalse(due(110, 100, 20));
        assertTrue(due(120, 100, 20));
        assertTrue(due(10, 100, 20));
        assertThrows(IllegalArgumentException.class, () -> due(10, 0, 0));
    }

    @Test void invalidNeedsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Context(false,false,false,true,false,-1,80));
    }
}
