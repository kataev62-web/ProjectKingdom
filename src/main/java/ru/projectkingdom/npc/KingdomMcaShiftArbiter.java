package ru.projectkingdom.npc;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Schedule;

/**
 * The arbitration of the navigation between the Kingdom and MCA.
 *
 * <p>One server owner of the navigation per resident at any moment. While the
 * Kingdom shift runs, the resident must not walk away to its own business;
 * when the shift ends, the ordinary life of MCA must continue exactly as
 * before.
 *
 * <p>The arbitration uses the vanilla brain only:
 *
 * <ul>
 *   <li>the schedule of the resident is replaced with the empty schedule, so
 *       the work, meeting and rest activities of the day do not start;</li>
 *   <li>the default activity becomes the core activity, so the idle business
 *       of MCA (the favourite building, the walk target) does not start;</li>
 *   <li>the memories that hold a movement target (the walk target, the job
 *       site, the meeting point) are cleared, so no MCA task walks away;</li>
 *   <li>the core tasks stay: the resident still looks around, greets, hides
 *       in a bell and reacts to danger. The panic of MCA overrides the core
 *       activity on its own and is never blocked.</li>
 * </ul>
 *
 * <p>Nothing of MCA is changed: the shift is a lease that is taken and
 * returned, and the original schedule and memories are restored.
 */
public final class KingdomMcaShiftArbiter {
    private KingdomMcaShiftArbiter() {}

    /** The state that the arbiter took from the brain of a resident. */
    public static final class Lease {
        private final Schedule schedule;
        private final Optional<GlobalPos> jobSite;
        private final Optional<GlobalPos> meetingPoint;
        private boolean released;

        Lease(Schedule schedule, Optional<GlobalPos> jobSite, Optional<GlobalPos> meetingPoint) {
            this.schedule = schedule;
            this.jobSite = jobSite;
            this.meetingPoint = meetingPoint;
        }

        /** The schedule that the resident had before the shift. */
        public Schedule schedule() { return schedule; }
        /** Whether the lease was already returned. */
        public boolean released() { return released; }
    }

    private static final Map<UUID, Lease> LEASES = new HashMap<>();

    /**
     * Takes the navigation of a resident for the Kingdom shift.
     *
     * @return the lease, or null when the resident is not an MCA resident.
     */
    public static Lease begin(Villager resident) {
        if (!KingdomMcaAdapter.isResident(resident)) return null;
        return begin(resident.getUUID(), resident.getBrain());
    }

    /**
     * Takes the navigation of a brain for the Kingdom shift.
     *
     * <p>The brain is the contract with MCA: the shift changes the schedule,
     * the default activity and the movement memories, and nothing else.
     */
    public static Lease begin(UUID id, net.minecraft.world.entity.ai.Brain<? super Villager> brain) {
        if (id == null || brain == null) return null;
        Lease existing = LEASES.get(id);
        if (existing != null && !existing.released) return existing;
        Lease lease = new Lease(brain.getSchedule(),
                brain.getMemory(MemoryModuleType.JOB_SITE),
                brain.getMemory(MemoryModuleType.MEETING_POINT));
        brain.setSchedule(Schedule.EMPTY);
        brain.setDefaultActivity(Activity.CORE);
        clearMovements(brain);
        LEASES.put(id, lease);
        return lease;
    }

    /**
     * Keeps the lease during the shift.
     *
     * <p>The MCA tasks look for a walk target again and again. The memory is
     * cleared on every tick, so the resident stays where the Kingdom wants it.
     */
    public static void hold(Villager resident) {
        if (resident == null) return;
        hold(resident.getUUID(), resident.getBrain());
    }

    /** Keeps the lease of a resident during the shift. */
    public static void hold(UUID id, net.minecraft.world.entity.ai.Brain<? super Villager> brain) {
        if (id == null || brain == null) return;
        Lease lease = LEASES.get(id);
        if (lease == null || lease.released) {
            lease = begin(id, brain);
            if (lease == null) return;
        }
        clearMovements(brain);
    }

    /**
     * Returns the navigation to MCA.
     *
     * <p>The original schedule and memories come back, and the activity of the
     * resident is recomputed from the time of the day.
     */
    public static void release(Villager resident) {
        if (resident == null) return;
        release(resident.getUUID(), resident.getBrain(), resident.level() == null ? 0 : resident.level().getDayTime(),
                resident.tickCount);
    }

    /** Returns the navigation to MCA. */
    public static void release(UUID id, net.minecraft.world.entity.ai.Brain<? super Villager> brain,
                                long dayTime, long time) {
        if (id == null || brain == null) return;
        Lease lease = LEASES.remove(id);
        if (lease == null) return;
        lease.released = true;
        brain.setSchedule(lease.schedule);
        brain.setDefaultActivity(Activity.IDLE);
        lease.jobSite.ifPresent(pos -> brain.setMemory(MemoryModuleType.JOB_SITE, pos));
        lease.meetingPoint.ifPresent(pos -> brain.setMemory(MemoryModuleType.MEETING_POINT, pos));
        brain.updateActivityFromSchedule(dayTime, time);
    }

    /** Whether the resident is currently on a Kingdom shift. */
    public static boolean onShift(UUID resident) {
        Lease lease = LEASES.get(resident);
        return lease != null && !lease.released;
    }

    /** The number of residents that are currently on a Kingdom shift. */
    public static int activeShifts() { return LEASES.size(); }

    /** Forgets every lease, for example when the server stops. */
    public static void forgetAll() { LEASES.clear(); }

    /** The memories that hold a movement target of MCA. */
    private static void clearMovements(net.minecraft.world.entity.ai.Brain<? super Villager> brain) {
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.JOB_SITE);
        brain.eraseMemory(MemoryModuleType.MEETING_POINT);
        brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
        brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
    }
}
