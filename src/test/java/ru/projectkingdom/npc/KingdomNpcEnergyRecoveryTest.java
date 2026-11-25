package ru.projectkingdom.npc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomNpcEnergyRecoveryTest {
    private static KingdomNpcGoalSelector.Goal goal(boolean shift, int energy) {
        return KingdomNpcGoalSelector.choose(new KingdomNpcGoalSelector.Context(
                true,true,true,shift,true,100,energy));
    }
    @Test void standingWorkOrderCanRecoverAtDawnAndResumeTheNextShift() {
        int energy=20;
        var nightGoal=goal(false,energy);
        assertFalse(KingdomNpcWorkday.expendsEnergy("work",nightGoal));
        energy=NpcLife.stepEnergy(energy,1,KingdomNpcWorkday.expendsEnergy("work",nightGoal),true);
        assertTrue(energy>25, "sleep must overcome the work-stop threshold");
        assertEquals(KingdomNpcGoalSelector.Goal.WORK,goal(true,energy));
        assertTrue(KingdomNpcWorkday.expendsEnergy("work",goal(true,energy)));
    }
    @Test void ExhaustedWorkerRestingDuringTheShiftDoesNotKeepSpendingEnergy() {
        int energy=20;
        var rest=goal(true,energy);
        assertEquals(KingdomNpcGoalSelector.Goal.REST,rest);
        energy=NpcLife.stepEnergy(energy,1,KingdomNpcWorkday.expendsEnergy("work",rest),true);
        assertEquals(KingdomNpcGoalSelector.Goal.WORK,goal(true,energy));
        assertFalse(KingdomNpcWorkday.expendsEnergy("follow",goal(true,energy)));
        assertFalse(KingdomNpcWorkday.expendsEnergy("stay",goal(true,energy)));
    }
}
