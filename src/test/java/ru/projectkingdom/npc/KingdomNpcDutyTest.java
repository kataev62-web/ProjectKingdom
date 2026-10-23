package ru.projectkingdom.npc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomNpcDutyTest {
    @Test void everyRegisteredProfessionBelongsToASingleSharedDutySystem() {
        for (var profession : KingdomProfessionRegistry.all()) {
            var kind = KingdomNpcDuty.kind(profession.id());
            assertNotNull(kind, profession.id());
            if (!"free".equals(profession.id()))
                assertNotEquals(KingdomNpcDuty.Kind.NONE, kind, profession.id());
        }
        assertEquals(KingdomNpcDuty.Kind.NONE, KingdomNpcDuty.kind("unregistered"));
    }

    @Test void realExecutorsAreNotConfusedWithPlannedProfessions() {
        for (String job : new String[]{"farmer", "miner", "lumberjack"}) {
            assertEquals(KingdomNpcDuty.Kind.FIELD, KingdomNpcDuty.kind(job));
            assertTrue(KingdomNpcDuty.hasPhysicalExecutor(job));
        }
        assertEquals(KingdomNpcDuty.Kind.WORKSHOP, KingdomNpcDuty.kind("smith"));
        assertEquals(KingdomNpcDuty.Kind.CONSTRUCTION, KingdomNpcDuty.kind("builder"));
        assertEquals(KingdomNpcDuty.Kind.DELIVERY, KingdomNpcDuty.kind("courier"));
        for (String job : new String[]{"baker", "weaver", "guard", "priest", "potter"}) {
            assertEquals(KingdomNpcDuty.Kind.SERVICE, KingdomNpcDuty.kind(job));
            assertFalse(KingdomNpcDuty.hasPhysicalExecutor(job));
        }
    }

    @Test void stoppedOrOffShiftWorkersCannotExecutePhysicalActions() {
        for (String job : new String[]{"smith", "farmer", "miner", "lumberjack", "builder", "courier"}) {
            assertTrue(KingdomNpcDuty.mayExecute(job, "wander", "CONTINUE_ASSIGNED_JOB", true), job);
            assertTrue(KingdomNpcDuty.mayExecute(job, "work", "CONTINUE_ASSIGNED_JOB", true), job);
            assertFalse(KingdomNpcDuty.mayExecute(job, "stay", "CONTINUE_ASSIGNED_JOB", true), job);
            assertFalse(KingdomNpcDuty.mayExecute(job, "home", "CONTINUE_ASSIGNED_JOB", true), job);
            assertFalse(KingdomNpcDuty.mayExecute(job, "follow", "CONTINUE_ASSIGNED_JOB", true), job);
            assertFalse(KingdomNpcDuty.mayExecute(job, "wander", "REST", true), job);
            assertFalse(KingdomNpcDuty.mayExecute(job, "wander", "CONTINUE_ASSIGNED_JOB", false), job);
        }
        assertFalse(KingdomNpcDuty.mayExecute("baker", "work", "CONTINUE_ASSIGNED_JOB", true));
    }

    @Test void allProfessionsFollowTheSameBasicGoalPriority() {
        for (var profession : KingdomProfessionRegistry.all()) {
            if ("free".equals(profession.id())) continue;
            var ready = new KingdomNpcGoalSelector.Context(true, true, true, true, true, 90, 90);
            assertEquals(KingdomNpcGoalSelector.Goal.WORK, KingdomNpcGoalSelector.choose(ready), profession.id());
            assertEquals(KingdomNpcGoalSelector.Goal.RETURN_HOME,
                KingdomNpcGoalSelector.choose(new KingdomNpcGoalSelector.Context(true,true,true,false,true,90,90)),
                profession.id());
            assertEquals(KingdomNpcGoalSelector.Goal.EAT,
                KingdomNpcGoalSelector.choose(new KingdomNpcGoalSelector.Context(true,true,true,true,true,10,90)),
                profession.id());
        }
    }
}
