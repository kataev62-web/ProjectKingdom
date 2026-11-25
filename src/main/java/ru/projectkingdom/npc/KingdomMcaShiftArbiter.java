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

/** Exclusive, temporary movement lease. Native schedules, POIs and default activities stay live. */
public final class KingdomMcaShiftArbiter {
    private KingdomMcaShiftArbiter() {}

    /** The state that the arbiter took from the brain of a resident. */
    public static final class Lease {
        private final Schedule schedule;
        private net.minecraft.world.entity.ai.Brain<?> brain;
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
     * <p>Only the active movement activity and WALK_TARGET are leased. The
     * individual schedule and native default/POI policy remain untouched.
     */
    public static Lease begin(UUID id, net.minecraft.world.entity.ai.Brain<? super Villager> brain) {
        if (id == null || brain == null) return null;
        Lease existing = LEASES.get(id);
        if (existing != null && !existing.released && existing.brain == brain) return existing;
        if (existing != null) existing.released = true;
        Lease lease = new Lease(brain.getSchedule(),
                brain.getMemory(MemoryModuleType.JOB_SITE),
                brain.getMemory(MemoryModuleType.MEETING_POINT));
        lease.brain = brain;
        // Never rewrite MCA's default activity. Its brain owns that policy.
        if (!brain.isActive(Activity.PANIC) && !brain.isActive(Activity.HIDE)
                && !brain.isActive(Activity.RAID)) brain.setActiveActivityIfPossible(Activity.CORE);
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
        if (lease == null || lease.released || lease.brain != brain) {
            lease = begin(id, brain);
            if (lease == null) return;
        }
        if (!brain.isActive(Activity.PANIC) && !brain.isActive(Activity.HIDE)
                && !brain.isActive(Activity.RAID)) brain.setActiveActivityIfPossible(Activity.CORE);
        clearMovements(brain);
    }

    /**
     * Returns the navigation to MCA.
     *
     * <p>The activity is recomputed from the current live MCA schedule; changes
     * made by MCA during the lease are retained.
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
        // MCA player commands may rebuild the brain. It already owns its new
        // activity; releasing an obsolete lease must not reset that activity.
        if (lease.brain != brain) return;
        // MCA may replace its schedule while leased; never restore an obsolete snapshot.
        boolean emergency = brain.isActive(Activity.PANIC) || brain.isActive(Activity.HIDE)
                || brain.isActive(Activity.RAID);
        // No stale POIs are written back over changes made by MCA mid-shift.
        if (!emergency) {
            brain.useDefaultActivity();
            brain.updateActivityFromSchedule(dayTime, time);
        }
    }

    /** Whether the resident is currently on a Kingdom shift. */
    public static boolean onShift(UUID resident) {
        Lease lease = LEASES.get(resident);
        return lease != null && !lease.released;
    }

    /** Called only for leased brains; other villagers keep their normal schedule updates. */
    public static boolean shouldDeferScheduleUpdate(net.minecraft.world.entity.ai.Brain<?> brain) {
        for (Lease lease : LEASES.values())
            if (!lease.released && lease.brain == brain) return true;
        return false;
    }

    /** The number of residents that are currently on a Kingdom shift. */
    public static int activeShifts() { return LEASES.size(); }

    /** Forgets every lease, for example when the server stops. */
    public static void forgetAll() { LEASES.clear(); }

    /** The memories that hold a movement target of MCA. */
    private static void clearMovements(net.minecraft.world.entity.ai.Brain<? super Villager> brain) {
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        // Job/home/meeting POIs and threat sensors belong to MCA. Erasing
        // them can orphan claims and disable panic between Kingdom ticks.
    }
}
