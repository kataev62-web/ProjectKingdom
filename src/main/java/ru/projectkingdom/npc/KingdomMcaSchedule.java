package ru.projectkingdom.npc;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;

/** Read-only boundary to the individual schedule and player orders of MCA. */
public final class KingdomMcaSchedule {
    private KingdomMcaSchedule() {}

    public record Snapshot(Activity scheduledActivity, Activity currentActivity,
                           boolean controlsAvailable, boolean manualOverride,
                           boolean workAllowed, String moveState, String chore) {}

    /**
     * Inspect the resident's live individual MCA schedule without replacing it. Never ask MCA to choose a schedule:
     * that API rolls its night-owl random chance again.
     */
    public static Snapshot inspect(Villager resident, Schedule authoritativeSchedule) {
        if (resident == null || !KingdomMcaAdapter.isResident(resident))
            return inspect(0, authoritativeSchedule, null, null);
        Activity current = resident.getBrain().getActiveNonCoreActivity().orElse(null);
        Object mcaBrain = null;
        try {
            mcaBrain = resident.getClass().getMethod("getVillagerBrain").invoke(resident);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // An unknown MCA version must keep ownership of the resident.
        }
        return inspect(resident.level().getDayTime(), authoritativeSchedule, current, mcaBrain);
    }

    static Snapshot inspect(long dayTime, Schedule schedule, Activity current, Object mcaBrain) {
        Activity scheduled = schedule == null ? null
                : schedule.getActivityAt((int) Math.floorMod(dayTime, 24000L));
        String movement = null;
        String chore = null;
        boolean readable = false;
        try {
            if (mcaBrain != null) {
                Object moveValue = mcaBrain.getClass().getMethod("getMoveState").invoke(mcaBrain);
                Object choreValue = mcaBrain.getClass().getMethod("getCurrentJob").invoke(mcaBrain);
                if (moveValue instanceof Enum<?> move && choreValue instanceof Enum<?> job) {
                    movement = move.name();
                    chore = job.name();
                    readable = true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Do not overwrite player orders when their state cannot be read.
        }
        boolean manual = readable && (!"MOVE".equals(movement) || !"NONE".equals(chore));
        // Ordinary activities may lag a schedule boundary, or WORK may be
        // unavailable without a vanilla job POI. Custom activities (e.g. MCA
        // mourning/chore) and emergency activities retain their ownership.
        boolean ordinary = current == null || current == Activity.CORE || current == Activity.IDLE
                || current == Activity.WORK || current == Activity.REST || current == Activity.MEET;
        return new Snapshot(scheduled, current, readable, manual,
                scheduled == Activity.WORK && readable && !manual && ordinary, movement, chore);
    }
}
