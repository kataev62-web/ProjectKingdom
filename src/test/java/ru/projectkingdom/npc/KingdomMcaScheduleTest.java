package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import org.junit.jupiter.api.Test;

final class KingdomMcaScheduleTest {
    public enum Movement { MOVE, STAY, FOLLOW }
    public enum Chore { NONE, HARVEST }
    public record Controls(Movement movement, Chore chore) {
        public Movement getMoveState() { return movement; }
        public Chore getCurrentJob() { return chore; }
    }

    private static final Controls FREE = new Controls(Movement.MOVE, Chore.NONE);

    private static Schedule shifted(int start, int end) {
        return new ScheduleBuilder(new Schedule()).changeActivityAt(0, Activity.REST)
                .changeActivityAt(start, Activity.WORK).changeActivityAt(end, Activity.MEET).build();
    }

    @Test void individualDayNightAndCustomShiftsFollowTheirOwnBoundaries() {
        for (int start : new int[] { 1400, 13600, 19350 }) {
            Schedule schedule = shifted(start, start + 700);
            assertFalse(KingdomMcaSchedule.inspect(start - 1, schedule, Activity.IDLE, FREE).workAllowed());
            assertTrue(KingdomMcaSchedule.inspect(start, schedule, Activity.IDLE, FREE).workAllowed());
            assertTrue(KingdomMcaSchedule.inspect(start + 699, schedule, Activity.WORK, FREE).workAllowed());
            var off = KingdomMcaSchedule.inspect(start + 700, schedule, Activity.WORK, FREE);
            assertEquals(Activity.MEET, off.scheduledActivity());
            assertFalse(off.workAllowed(), "stale active WORK cannot extend the actual shift");
            assertTrue(KingdomMcaSchedule.inspect(start + 24000L, schedule, Activity.IDLE, FREE).workAllowed());
        }
    }

    @Test void followingStayingAndAssignedChoresKeepMcaOwnership() {
        Schedule schedule = shifted(1400, 2100);
        for (Controls ordered : new Controls[] { new Controls(Movement.FOLLOW, Chore.NONE),
                new Controls(Movement.STAY, Chore.NONE), new Controls(Movement.MOVE, Chore.HARVEST) }) {
            var snapshot = KingdomMcaSchedule.inspect(1500, schedule, Activity.WORK, ordered);
            assertTrue(snapshot.controlsAvailable());
            assertTrue(snapshot.manualOverride());
            assertFalse(snapshot.workAllowed());
        }
    }

    @Test void aNightShiftCanContinueAcrossMidnightAndPauseForMeeting() {
        Schedule schedule = new ScheduleBuilder(new Schedule())
                .changeActivityAt(10, Activity.REST).changeActivityAt(9000, Activity.MEET)
                .changeActivityAt(14000, Activity.WORK).build();
        assertTrue(KingdomMcaSchedule.inspect(23999, schedule, Activity.IDLE, FREE).workAllowed());
        assertTrue(KingdomMcaSchedule.inspect(24000, schedule, Activity.WORK, FREE).workAllowed());
        assertTrue(KingdomMcaSchedule.inspect(24009, schedule, Activity.WORK, FREE).workAllowed());
        assertFalse(KingdomMcaSchedule.inspect(24010, schedule, Activity.WORK, FREE).workAllowed());
        assertFalse(KingdomMcaSchedule.inspect(9000, schedule, Activity.WORK, FREE).workAllowed());
        assertTrue(KingdomMcaSchedule.inspect(-1, schedule, Activity.IDLE, FREE).workAllowed());
    }

    @Test void unknownControlsAndEmergencyActivitiesDeferToMca() {
        Schedule schedule = shifted(1400, 2100);
        assertFalse(KingdomMcaSchedule.inspect(1500, schedule, Activity.WORK, new Object()).workAllowed());
        assertFalse(KingdomMcaSchedule.inspect(1500, schedule, Activity.PANIC, FREE).workAllowed());
        assertFalse(KingdomMcaSchedule.inspect(1500, schedule, Activity.HIDE, FREE).workAllowed());
        assertFalse(KingdomMcaSchedule.inspect(1500, schedule, Activity.RAID, FREE).workAllowed());
        assertFalse(KingdomMcaSchedule.inspect(1500, null, Activity.WORK, FREE).workAllowed());
    }

    @Test void fatigueAndRestNeverOwnNavigationOrPermitProduction() {
        var work = KingdomMcaSchedule.inspect(1500, shifted(1400,2100), Activity.WORK, FREE);
        assertEquals(KingdomResidentSchedule.Mode.KINGDOM_WORK,
            KingdomResidentSchedule.mcaMode(work,true,false,false,false,false,false));
        assertEquals(KingdomResidentSchedule.Mode.MCA_LIFE,
            KingdomResidentSchedule.mcaMode(work,false,false,false,false,false,false));
        var rest = KingdomResidentSchedule.mcaMode(work,true,false,false,false,true,false);
        assertEquals(KingdomResidentSchedule.Mode.KINGDOM_REST, rest);
        assertFalse(KingdomResidentSchedule.kingdomOwns(rest));
        assertFalse(KingdomResidentSchedule.working(rest));
        var exit = KingdomResidentSchedule.mcaMode(work,true,false,false,false,true,true);
        assertEquals(KingdomResidentSchedule.Mode.EVACUATING, exit);
        assertTrue(KingdomResidentSchedule.kingdomOwns(exit));
        assertFalse(KingdomResidentSchedule.working(exit));
        assertEquals(KingdomResidentSchedule.Mode.EMERGENCY,
            KingdomResidentSchedule.mcaMode(work,true,false,true,false,true,true));
        assertEquals(KingdomResidentSchedule.Mode.KINGDOM_REST,
            KingdomResidentSchedule.mcaMode(work,true,false,false,true,false,false));
    }
}
