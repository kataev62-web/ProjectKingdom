package ru.projectkingdom.npc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.BlockState;
import ru.projectkingdom.npc.compat.TownsteadNpcCompatibility;

/** Observes real rest and hands tired residents back to native MCA bed tasks. */
public final class KingdomResidentRestController {
    private KingdomResidentRestController() {}

    public static final int CRITICAL_ENERGY = 25;
    public static final int RESUME_ENERGY = 40;
    static final int BED_WAIT_TICKS = 600;
    static final int EXIT_LIMIT_TICKS = 1200;

    public enum Status {
        NONE, EXITING_WORKSITE, EXIT_BLOCKED, NO_REGISTERED_BED, OTHER_DIMENSION,
        BED_UNLOADED, INVALID_BED, BED_OCCUPIED, SEEKING_BED, BED_PATH_BLOCKED,
        RESTING, SLEEPING
    }

    private static final class State {
        long observedAt = Long.MIN_VALUE;
        int recovery;
        int waiting;
        BlockPos exit;
        long exitStarted;
        boolean exitConsidered;
        Brain<?> forcedRestBrain;
        Status status = Status.NONE;
    }
    private static final Map<UUID, State> STATES = new HashMap<>();

    /** Hysteresis is saved with the contract; an unload must not cancel recovery. */
    public static boolean requiresRest(KingdomNpcData.Worker worker) {
        return worker != null && fatigueRequired(worker.fatigueRestRequired, worker.energy);
    }

    static boolean fatigueRequired(boolean latched, int energy) {
        return energy <= CRITICAL_ENERGY || latched && energy < RESUME_ENERGY;
    }

    /**
     * Call once per loaded server tick before choosing the ownership mode.
     * nativeRestDesired comes from the resident's actual individual schedule.
     * Returns whether saved worker values changed.
     */
    public static boolean refresh(MinecraftServer server, ServerLevel level, Villager resident,
                                  KingdomNpcData.Worker worker, boolean nativeRestDesired) {
        if (worker == null || resident == null) return false;
        State state = STATES.computeIfAbsent(resident.getUUID(), id -> new State());
        long now = level.getGameTime();
        boolean changed = false;
        boolean required = requiresRest(worker);
        if (required != worker.fatigueRestRequired) {
            worker.fatigueRestRequired = required;
            changed = true;
        }
        boolean danger = physicalDanger(resident) || worker.safety < 25;
        var nativeControl = KingdomMcaSchedule.inspect(resident, resident.getBrain().getSchedule());
        boolean permitsExit = nativeControl.controlsAvailable() && !nativeControl.manualOverride()
                && !TownsteadNpcCompatibility.mustRest(resident);
        boolean resting = resident.isSleeping() || !danger
                && resident.getBrain().isActive(Activity.REST) && resident.onGround()
                && resident.getNavigation().isDone()
                && !resident.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                && resident.getDeltaMovement().horizontalDistanceSqr() < 0.0001;
        // Missing observations (unloaded chunks, restart, time jumps) never earn energy.
        boolean consecutive = state.observedAt != Long.MIN_VALUE && now - state.observedAt == 1;
        state.observedAt = now;
        state.recovery = recoveryProgress(state.recovery, consecutive, resident.isSleeping(), resting);
        if (state.recovery >= 400) {
            int energy = NpcLife.clamp(worker.energy + 1);
            changed |= energy != worker.energy;
            worker.energy = energy;
            state.recovery -= 400;
            boolean remainingFatigue = fatigueRequired(worker.fatigueRestRequired, worker.energy);
            changed |= remainingFatigue != worker.fatigueRestRequired;
            worker.fatigueRestRequired = remainingFatigue;
        }
        boolean rest = nativeRestDesired || requiresRest(worker);
        state.forcedRestBrain = null;
        if (!rest) {
            state.exit = null;
            state.exitConsidered = false;
            state.waiting = 0;
            state.status = Status.NONE;
            return changed;
        }
        if (danger || resident.isSleeping() || !permitsExit) state.exit = null;
        if (danger || !permitsExit) state.exitConsidered = false;
        if (!state.exitConsidered && !danger && permitsExit && !resident.isSleeping()) {
            state.exitConsidered = true;
            // The existing site's entrance and existing navigator are the only
            // evacuation route. No excavation, ladder, inventory or bed changes.
            if (!danger && permitsExit && !resident.isSleeping() && "miner".equals(worker.job)) {
                var site = KingdomMineSite.inspect(server, level, worker);
                if (site.entrance() != null && resident.getY() < site.entrance().getY() - 0.5) {
                    if (site.ready()) {
                        state.exit = site.entrance().immutable();
                        state.exitStarted = now;
                    } else state.status = Status.EXIT_BLOCKED;
                }
            }
        }
        if (state.exit != null) state.status = Status.EXITING_WORKSITE;
        else if (resident.isSleeping()) state.status = Status.SLEEPING;
        else if (state.status != Status.EXIT_BLOCKED) {
            Status bed = inspectBed(level, resident);
            if (bed == Status.SEEKING_BED) {
                state.waiting = consecutive ? state.waiting + 1 : 0;
                state.status = state.waiting >= BED_WAIT_TICKS ? Status.BED_PATH_BLOCKED : bed;
            } else {
                state.waiting = 0;
                state.status = bed;
            }
        }
        return changed;
    }

    static int recoveryProgress(int progress, boolean consecutive, boolean sleeping, boolean resting) {
        if (!consecutive || !resting) return 0;
        // One energy per 100 observed sleeping ticks, or 400 stationary REST ticks.
        return progress + (sleeping ? 4 : 1);
    }

    /** A distinct short evacuation lease is required; KINGDOM_REST owns no movement. */
    public static boolean evacuating(UUID resident) {
        State state = STATES.get(resident);
        return state != null && state.exit != null;
    }

    /** Call only while the arbiter has granted the distinct evacuation lease. */
    public static void evacuate(ServerLevel level, Villager resident) {
        State state = STATES.get(resident.getUUID());
        if (state == null || state.exit == null) return;
        if (level.getGameTime() - state.exitStarted > EXIT_LIMIT_TICKS || physicalDanger(resident)) {
            finishExit(resident, state, Status.EXIT_BLOCKED);
            return;
        }
        var outcome = KingdomActionEngine.navigate(level, resident, state.exit, 0.75);
        if (outcome.done()) finishExit(resident, state, Status.SEEKING_BED);
        else if (outcome.failed()) finishExit(resident, state, Status.EXIT_BLOCKED);
    }

    private static void finishExit(Villager resident, State state, Status status) {
        state.exit = null;
        state.status = status;
        resident.getNavigation().stop();
        KingdomActionEngine.forget(resident.getUUID());
    }

    /** Request the real REST activity after releasing the lease; never start sleep ourselves. */
    public static void requestNativeRest(Villager resident, KingdomNpcData.Worker worker) {
        if (resident == null || !requiresRest(worker) || evacuating(resident.getUUID())
                || worker.safety < 25 || physicalDanger(resident)
                || KingdomMcaShiftArbiter.onShift(resident.getUUID())) return;
        // Respect the player's MCA follow/stay/chore orders and unknown MCA APIs.
        var schedule = KingdomMcaSchedule.inspect(resident, resident.getBrain().getSchedule());
        if (schedule.controlsAvailable() && !schedule.manualOverride()
                && !TownsteadNpcCompatibility.mustRest(resident)) {
            State state = STATES.computeIfAbsent(resident.getUUID(), id -> new State());
            state.forcedRestBrain = resident.getBrain();
            resident.getBrain().setActiveActivityIfPossible(Activity.REST);
        }
    }

    /** Only the forced native REST activity is held; its schedule and movement stay native. */
    public static boolean shouldKeepRestActivity(Brain<?> brain) {
        if (brain == null) return false;
        for (State state : STATES.values()) if (state.forcedRestBrain == brain) return true;
        return false;
    }

    /** Productive optional callbacks yield during critical fatigue, excluding native emergencies. */
    public static boolean requiresRestControl(Villager resident) {
        if (resident == null || physicalDanger(resident) || TownsteadNpcCompatibility.mustRest(resident))
            return false;
        State state = STATES.get(resident.getUUID());
        return state != null && state.forcedRestBrain == resident.getBrain();
    }

    /** HOME belongs to MCA Residency/POI registration; Kingdom coordinates are never a bed claim. */
    public static Status inspectBed(ServerLevel level, Villager resident) {
        if (resident.isSleeping()) return Status.SLEEPING;
        var home = resident.getBrain().getMemory(MemoryModuleType.HOME);
        if (home.isEmpty()) return Status.NO_REGISTERED_BED;
        if (!home.get().dimension().equals(level.dimension())) return Status.OTHER_DIMENSION;
        BlockPos head = home.get().pos();
        if (!level.hasChunkAt(head)) return Status.BED_UNLOADED;
        var bed = level.getBlockState(head);
        if (!validHead(bed)) return Status.INVALID_BED;
        BlockPos foot = head.relative(bed.getValue(BedBlock.FACING).getOpposite());
        if (!level.hasChunkAt(foot)) return Status.BED_UNLOADED;
        var other = level.getBlockState(foot);
        if (!validBedPair(bed, other)) return Status.INVALID_BED;
        return bed.getValue(BedBlock.OCCUPIED) ? Status.BED_OCCUPIED : Status.SEEKING_BED;
    }

    static boolean validHead(BlockState bed) {
        return (bed.getBlock() instanceof BedBlock || bed.is(BlockTags.BEDS)) && bed.hasProperty(BedBlock.PART)
                && bed.hasProperty(BedBlock.OCCUPIED) && bed.hasProperty(BedBlock.FACING)
                && bed.getValue(BedBlock.PART) == BedPart.HEAD;
    }

    static boolean validBedPair(BlockState head, BlockState foot) {
        return validHead(head) && foot.is(head.getBlock()) && foot.hasProperty(BedBlock.PART)
                && foot.hasProperty(BedBlock.FACING) && foot.getValue(BedBlock.PART) == BedPart.FOOT
                && foot.getValue(BedBlock.FACING) == head.getValue(BedBlock.FACING);
    }

    private static boolean physicalDanger(Villager resident) {
        return resident.isOnFire() || resident.isInWaterOrBubble()
                || resident.getLastHurtByMob() != null
                    && resident.tickCount - resident.getLastHurtByMobTimestamp() < 100
                || resident.getBrain().isActive(Activity.PANIC)
                || resident.getBrain().isActive(Activity.HIDE)
                || resident.getBrain().isActive(Activity.RAID);
    }

    public static Status status(UUID resident) {
        State state = STATES.get(resident);
        return state == null ? Status.NONE : state.status;
    }
    public static void forget(UUID resident) { STATES.remove(resident); }
    public static void forgetAll() { STATES.clear(); }
}
