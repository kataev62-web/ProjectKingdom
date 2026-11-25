package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Schedule;

import org.junit.jupiter.api.Test;

/**
 * The arbitration between the Kingdom shift and the life of MCA.
 *
 * <p>The brain is the contract: the shift must take the navigation away from
 * the daily business of MCA and give it back untouched afterwards.
 */
final class KingdomMcaShiftArbiterTest {

    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static Brain<Villager> brain() {
        return new Brain<Villager>(ImmutableList.of(
                        MemoryModuleType.JOB_SITE,
                        MemoryModuleType.MEETING_POINT,
                        MemoryModuleType.WALK_TARGET,
                        MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM),
                ImmutableList.of(), ImmutableList.of(), () -> null);
    }

    @Test void theShiftTakesTheScheduleAndTheDefaultActivity() {
        Brain<Villager> brain = brain();
        brain.setSchedule(Schedule.VILLAGER_DEFAULT);
        brain.setDefaultActivity(Activity.IDLE);
        UUID resident = UUID.randomUUID();

        var lease = KingdomMcaShiftArbiter.begin(resident, brain);
        assertNotNull(lease, "the shift must take a lease");
        assertEquals(Schedule.EMPTY, brain.getSchedule(), "the day of MCA must not start the work");
        assertTrue(KingdomMcaShiftArbiter.onShift(resident), "the resident is on a shift");

        KingdomMcaShiftArbiter.release(resident, brain, 1000, 1000);
        assertEquals(Schedule.VILLAGER_DEFAULT, brain.getSchedule(), "the schedule must come back");
        assertFalse(KingdomMcaShiftArbiter.onShift(resident), "the shift is over");
    }

    @Test void theShiftClearsTheMovementMemoriesAndRestoresThem() {
        Brain<Villager> brain = brain();
        brain.setSchedule(Schedule.VILLAGER_DEFAULT);
        GlobalPos jobSite = GlobalPos.of(net.minecraft.world.level.Level.OVERWORLD, new BlockPos(10, 64, 10));
        GlobalPos meetingPoint = GlobalPos.of(net.minecraft.world.level.Level.OVERWORLD, new BlockPos(20, 64, 20));
        brain.setMemory(MemoryModuleType.JOB_SITE, jobSite);
        brain.setMemory(MemoryModuleType.MEETING_POINT, meetingPoint);
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPos(5, 65, 5), 1.0F, 5));
        UUID resident = UUID.randomUUID();

        KingdomMcaShiftArbiter.begin(resident, brain);
        assertTrue(brain.hasMemoryValue(MemoryModuleType.JOB_SITE), "MCA must retain its job POI");
        assertTrue(brain.hasMemoryValue(MemoryModuleType.MEETING_POINT), "MCA must retain its meeting POI");
        assertFalse(brain.hasMemoryValue(MemoryModuleType.WALK_TARGET), "the walk target must be cleared");

        KingdomMcaShiftArbiter.release(resident, brain, 1000, 1000);
        assertEquals(jobSite, brain.getMemory(MemoryModuleType.JOB_SITE).orElse(null),
                "the job site of MCA must come back");
        assertEquals(meetingPoint, brain.getMemory(MemoryModuleType.MEETING_POINT).orElse(null),
                "the meeting point of MCA must come back");
    }

    @Test void aSecondShiftDoesNotOverwriteTheLease() {
        Brain<Villager> brain = brain();
        brain.setSchedule(Schedule.VILLAGER_DEFAULT);
        UUID resident = UUID.randomUUID();
        var first = KingdomMcaShiftArbiter.begin(resident, brain);
        var second = KingdomMcaShiftArbiter.begin(resident, brain);
        assertEquals(first, second, "the lease of a resident must be taken once");
        assertEquals(1, KingdomMcaShiftArbiter.activeShifts(), "one resident, one lease");
        KingdomMcaShiftArbiter.release(resident, brain, 1000, 1000);
        assertEquals(0, KingdomMcaShiftArbiter.activeShifts(), "the lease must be returned");
    }

    @Test void theHoldKeepsTheMemoriesClear() {
        Brain<Villager> brain = brain();
        UUID resident = UUID.randomUUID();
        KingdomMcaShiftArbiter.begin(resident, brain);
        // MCA looks for a walk target again and again during the shift.
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPos(1, 2, 3), 1.0F, 3));
        assertTrue(brain.hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "the memory can be set by MCA between the ticks");
        KingdomMcaShiftArbiter.hold(resident, brain);
        assertFalse(brain.hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "the hold must clear the target again");
        KingdomMcaShiftArbiter.release(resident, brain, 1000, 1000);
    }

    @Test void aResidentWithoutMcaIsNeverShifted() {
        Brain<Villager> brain = brain();
        assertNull(KingdomMcaShiftArbiter.begin((Villager) null),
                "without a resident there is no lease");
    }
    @Test void leaseDoesNotOverwriteMcaPoiChangesOrDefaultActivity() {
        Brain<Villager> brain = brain();
        brain.setDefaultActivity(Activity.REST);
        var changed = GlobalPos.of(net.minecraft.world.level.Level.OVERWORLD, new BlockPos(1,2,3));
        UUID id=UUID.randomUUID();
        KingdomMcaShiftArbiter.begin(id,brain);
        brain.setMemory(MemoryModuleType.JOB_SITE,changed);
        KingdomMcaShiftArbiter.release(id,brain,0,0);
        brain.useDefaultActivity();
        assertTrue(brain.isActive(Activity.REST));
        assertEquals(changed,brain.getMemory(MemoryModuleType.JOB_SITE).orElseThrow());
    }
    @Test void emergencyHandoffKeepsMcaPanicActivity() {
        Brain<Villager> brain=brain();
        brain.addActivity(Activity.PANIC,0,ImmutableList.of());
        UUID id=UUID.randomUUID();
        KingdomMcaShiftArbiter.begin(id,brain);
        brain.setActiveActivityIfPossible(Activity.PANIC);
        assertTrue(brain.isActive(Activity.PANIC));
        KingdomMcaShiftArbiter.release(id,brain,1000,1000);
        assertTrue(brain.isActive(Activity.PANIC), "release must preserve MCA escape activity");
        assertFalse(KingdomMcaShiftArbiter.onShift(id));
    }
}
