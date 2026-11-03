package ru.projectkingdom.assistant;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import ru.projectkingdom.npc.KingdomArchitectDesignRotation;
import ru.projectkingdom.npc.KingdomBlockStateCodec;
import ru.projectkingdom.npc.KingdomConstructionAccess;
import ru.projectkingdom.npc.KingdomConstructionPlans;
import ru.projectkingdom.npc.KingdomConstructionWork;
import ru.projectkingdom.npc.KingdomDesignEngine;

import java.util.Objects;
import java.util.UUID;

/** Incremental creative placement with a durable before-image and safe undo. */
public final class AssistantCreativeBuildService {
    public static final int MAX_CHANGES_PER_TICK = 16;
    private static final int MAX_INSPECTIONS_PER_TICK = 256;

    public enum QueueStatus {
        QUEUED, INVALID_DESIGN, TOO_LARGE, OUT_OF_WORLD, UNAUTHORIZED,
        UNSAFE_TARGET, ALREADY_ACTIVE
    }

    public record QueueResult(QueueStatus status, UUID buildId) {}
    public record TickSummary(int changed, int pausedUnloaded, int failed, int completed) {}
    public record UndoSummary(int restored, int conflicts, int unloaded, int failed) {}

    public record WorldCell(
            String encoded,
            boolean air,
            boolean blockEntity,
            boolean unbreakable,
            boolean clearable
    ) {
        public WorldCell {
            encoded = Objects.requireNonNull(encoded, "encoded");
        }
    }

    public interface WorldAccess {
        int minBuildHeight(String dimension);
        int maxBuildHeight(String dimension);
        boolean isLoaded(String dimension, BlockPos position);
        boolean mayBuild(UUID owner, String dimension, BlockPos position);
        WorldCell inspect(String dimension, BlockPos position);
        boolean set(String dimension, BlockPos position, String encodedState);
        default boolean validDesired(String encodedState) { return true; }
    }

    @FunctionalInterface
    public interface DesignResolver {
        KingdomDesignEngine.Design resolve(String designKey);
    }

    private final KingdomAssistantBuildData data;
    private final WorldAccess world;
    private final DesignResolver designs;

    public AssistantCreativeBuildService(
            KingdomAssistantBuildData data, WorldAccess world, DesignResolver designs) {
        this.data = Objects.requireNonNull(data, "data");
        this.world = Objects.requireNonNull(world, "world");
        this.designs = Objects.requireNonNull(designs, "designs");
    }

    public static AssistantCreativeBuildService forServer(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return new AssistantCreativeBuildService(KingdomAssistantBuildData.get(server),
                new ForgeWorldAccess(server), key -> KingdomConstructionPlans.resolve(server, key));
    }

    public QueueResult queue(UUID owner, UUID assistant, String dimension,
                             String designKey, BlockPos origin) {
        if (owner == null || assistant == null || dimension == null || designKey == null
                || origin == null) return result(QueueStatus.INVALID_DESIGN, null);
        KingdomDesignEngine.Design design = designs.resolve(designKey);
        if (design == null || design.blocks().isEmpty())
            return result(QueueStatus.INVALID_DESIGN, null);
        if (design.blocks().size() > KingdomAssistantBuildData.MAX_CELLS)
            return result(QueueStatus.TOO_LARGE, null);
        if (data.hasActive(owner)) return result(QueueStatus.ALREADY_ACTIVE, null);
        int minY = world.minBuildHeight(dimension);
        int maxY = world.maxBuildHeight(dimension);
        if (minY >= maxY) return result(QueueStatus.OUT_OF_WORLD, null);
        for (var cell : design.blocks()) {
            BlockPos target = target(origin, cell.x(), cell.y(), cell.z());
            if (target == null || target.getY() < minY || target.getY() >= maxY)
                return result(QueueStatus.OUT_OF_WORLD, null);
            if (!world.validDesired(cell.material()))
                return result(QueueStatus.INVALID_DESIGN, null);
            if (!world.mayBuild(owner, dimension, target))
                return result(QueueStatus.UNAUTHORIZED, null);
            if (!world.isLoaded(dimension, target)) continue;
            WorldCell present = world.inspect(dimension, target);
            if (unsafeReplacement(present, cell.material()))
                return result(QueueStatus.UNSAFE_TARGET, null);
        }
        UUID id = data.create(owner, assistant, dimension, designKey, origin, design);
        return result(QueueStatus.QUEUED, id);
    }

    public TickSummary tick() {
        int changed = 0, paused = 0, failed = 0, completed = 0;
        int inspections = 0;
        for (var entry : data.entries()) {
            if (changed >= MAX_CHANGES_PER_TICK || inspections >= MAX_INSPECTIONS_PER_TICK) break;
            TickSummary step;
            if (entry.status == KingdomAssistantBuildData.Status.QUEUED
                    || entry.status == KingdomAssistantBuildData.Status.BUILDING) {
                step = build(entry, MAX_CHANGES_PER_TICK - changed,
                        MAX_INSPECTIONS_PER_TICK - inspections);
            } else if (entry.status == KingdomAssistantBuildData.Status.UNDOING) {
                step = undo(entry, MAX_CHANGES_PER_TICK - changed,
                        MAX_INSPECTIONS_PER_TICK - inspections);
            } else continue;
            changed += step.changed();
            paused += step.pausedUnloaded();
            failed += step.failed();
            completed += step.completed();
            inspections += Math.max(1, step.changed() + step.failed());
        }
        return new TickSummary(changed, paused, failed, completed);
    }

    public UndoSummary undo(UUID owner, UUID buildId) {
        var entry = data.entry(buildId);
        if (entry == null || owner == null || !owner.equals(entry.owner))
            return new UndoSummary(0, 0, 0, 1);
        if (entry.status == KingdomAssistantBuildData.Status.UNDONE)
            return summary(entry, 0);
        if (entry.status != KingdomAssistantBuildData.Status.UNDOING) {
            entry.status = KingdomAssistantBuildData.Status.UNDOING;
            entry.undoCursor = entry.changes.size() - 1;
            data.touch();
        }
        TickSummary pass = undo(entry, MAX_CHANGES_PER_TICK, MAX_INSPECTIONS_PER_TICK);
        return summary(entry, pass.pausedUnloaded());
    }

    public boolean cancel(UUID owner) {
        if (owner == null) return false;
        for (var entry : data.entries()) {
            if (owner.equals(entry.owner) && (entry.status == KingdomAssistantBuildData.Status.QUEUED
                    || entry.status == KingdomAssistantBuildData.Status.BUILDING
                    || entry.status == KingdomAssistantBuildData.Status.UNDOING)) {
                entry.status = KingdomAssistantBuildData.Status.FAILED;
                entry.failed++;
                data.touch();
                return true;
            }
        }
        return false;
    }

    public static TickSummary tick(MinecraftServer server) {
        AssistantCreativeBuildService service = forServer(server);
        TickSummary summary = service.tick();
        KingdomAssistantBuildData builds = KingdomAssistantBuildData.get(server);
        KingdomAssistantData assistants = KingdomAssistantData.get(server);
        for (var entry : builds.entries()) {
            var ownerState = assistants.ownedBy(entry.owner);
            if (ownerState.isEmpty() || ownerState.get().activeIntent() == null) continue;
            if (entry.status == KingdomAssistantBuildData.Status.COMPLETE
                    && ownerState.get().activeIntent() == AssistantIntent.BUILD)
                assistants.finishTask(entry.owner, "Творческая стройка завершена: " + entry.id);
            else if (entry.status == KingdomAssistantBuildData.Status.UNDONE
                    && ownerState.get().activeIntent() == AssistantIntent.UNDO)
                assistants.finishTask(entry.owner, "Отмена стройки завершена: " + entry.id);
            else if (entry.status == KingdomAssistantBuildData.Status.FAILED
                    && (ownerState.get().activeIntent() == AssistantIntent.BUILD
                    || ownerState.get().activeIntent() == AssistantIntent.UNDO))
                assistants.finishTask(entry.owner, "Творческая операция остановлена: " + entry.id);
        }
        return summary;
    }

    private TickSummary build(KingdomAssistantBuildData.Entry entry,
                              int changeBudget, int inspectionBudget) {
        int changed = 0, failed = 0, inspected = 0;
        entry.status = KingdomAssistantBuildData.Status.BUILDING;
        data.touch();
        while (entry.cursor < entry.plan.size() && changed < changeBudget
                && inspected < inspectionBudget) {
            int planIndex = entry.cursor;
            var cell = entry.plan.get(planIndex);
            BlockPos position = target(entry.origin, cell.x(), cell.y(), cell.z());
            if (position == null) return fail(entry);
            if (!world.isLoaded(entry.dimension, position))
                return new TickSummary(changed, 1, failed, 0);
            if (!world.mayBuild(entry.owner, entry.dimension, position)) return fail(entry);
            WorldCell present = world.inspect(entry.dimension, position);
            inspected++;
            var journal = data.changeFor(entry, planIndex);
            if (present.encoded().equals(cell.placed())) {
                if (journal != null) journal.applied = true;
                entry.cursor++;
                data.touch();
                continue;
            }
            if (journal != null && (journal.applied
                    || !present.encoded().equals(journal.original))) return fail(entry);
            if (unsafeReplacement(present, cell.placed())) return fail(entry);
            if (journal == null) journal = data.journal(entry, planIndex, cell, present.encoded());
            if (!world.set(entry.dimension, position, cell.placed())) return fail(entry);
            journal.applied = true;
            entry.cursor++;
            changed++;
            data.touch();
        }
        if (entry.cursor >= entry.plan.size()) {
            entry.status = KingdomAssistantBuildData.Status.COMPLETE;
            data.touch();
            return new TickSummary(changed, 0, failed, 1);
        }
        return new TickSummary(changed, 0, failed, 0);
    }

    private TickSummary undo(KingdomAssistantBuildData.Entry entry,
                             int changeBudget, int inspectionBudget) {
        int changed = 0, failed = 0, inspected = 0;
        while (entry.undoCursor >= 0 && changed < changeBudget
                && inspected < inspectionBudget) {
            var change = entry.changes.get(entry.undoCursor);
            if (change.resolved || !change.applied) {
                change.resolved = true;
                entry.undoCursor--;
                data.touch();
                continue;
            }
            BlockPos position = target(entry.origin, change.x, change.y, change.z);
            if (position == null) {
                change.resolved = true; entry.failed++; failed++; entry.undoCursor--; data.touch();
                continue;
            }
            if (!world.isLoaded(entry.dimension, position))
                return new TickSummary(changed, 1, failed, 0);
            WorldCell present = world.inspect(entry.dimension, position);
            inspected++;
            if (!present.encoded().equals(change.placed)) {
                change.resolved = true;
                entry.conflicts++;
                entry.undoCursor--;
                data.touch();
                continue;
            }
            if (world.set(entry.dimension, position, change.original)) {
                entry.restored++;
                changed++;
            } else {
                entry.failed++;
                failed++;
            }
            change.resolved = true;
            entry.undoCursor--;
            data.touch();
        }
        if (entry.undoCursor < 0) {
            entry.status = KingdomAssistantBuildData.Status.UNDONE;
            data.touch();
            return new TickSummary(changed, 0, failed, 1);
        }
        return new TickSummary(changed, 0, failed, 0);
    }

    private TickSummary fail(KingdomAssistantBuildData.Entry entry) {
        entry.status = KingdomAssistantBuildData.Status.FAILED;
        entry.failed++;
        data.touch();
        return new TickSummary(0, 0, 1, 0);
    }

    private static boolean unsafeReplacement(WorldCell present, String desired) {
        if (present.encoded().equals(desired) || present.air()) return false;
        return present.blockEntity() || present.unbreakable() || !present.clearable();
    }

    private static BlockPos target(BlockPos origin, int x, int y, int z) {
        long targetX = (long) origin.getX() + x;
        long targetY = (long) origin.getY() + y;
        long targetZ = (long) origin.getZ() + z;
        if (targetX < Integer.MIN_VALUE || targetX > Integer.MAX_VALUE
                || targetY < Integer.MIN_VALUE || targetY > Integer.MAX_VALUE
                || targetZ < Integer.MIN_VALUE || targetZ > Integer.MAX_VALUE) return null;
        return new BlockPos((int) targetX, (int) targetY, (int) targetZ);
    }

    private static QueueResult result(QueueStatus status, UUID buildId) {
        return new QueueResult(status, buildId);
    }

    private static UndoSummary summary(KingdomAssistantBuildData.Entry entry, int unloaded) {
        return new UndoSummary(entry.restored, entry.conflicts, unloaded, entry.failed);
    }

    private static final class ForgeWorldAccess implements WorldAccess {
        private final MinecraftServer server;
        private ForgeWorldAccess(MinecraftServer server) { this.server = server; }

        @Override public int minBuildHeight(String dimension) {
            ServerLevel level = level(dimension);
            return level == null ? 0 : level.getMinBuildHeight();
        }

        @Override public int maxBuildHeight(String dimension) {
            ServerLevel level = level(dimension);
            return level == null ? 0 : level.getMaxBuildHeight();
        }

        @Override public boolean isLoaded(String dimension, BlockPos position) {
            ServerLevel level = level(dimension);
            return level != null && level.getChunkSource().getChunkNow(
                    position.getX() >> 4, position.getZ() >> 4) != null;
        }

        @Override public boolean mayBuild(UUID owner, String dimension, BlockPos position) {
            ServerLevel level = level(dimension);
            return level != null && KingdomConstructionAccess.canBuild(server, level, position, owner);
        }

        @Override public WorldCell inspect(String dimension, BlockPos position) {
            ServerLevel level = requireLoaded(dimension, position);
            BlockState state = level.getBlockState(position);
            return new WorldCell(KingdomArchitectDesignRotation.encode(state), state.isAir(),
                    state.hasBlockEntity(), state.getDestroySpeed(level, position) < 0,
                    state.isAir() || KingdomConstructionWork.canClearObstacle(level, position, state));
        }

        @Override public boolean set(String dimension, BlockPos position, String encodedState) {
            ServerLevel level = requireLoaded(dimension, position);
            BlockState state;
            try { state = KingdomBlockStateCodec.parse(encodedState); }
            catch (IllegalArgumentException invalid) { return false; }
            return level.setBlock(position, state, 3) && level.getBlockState(position).equals(state);
        }

        @Override public boolean validDesired(String encodedState) {
            try {
                BlockState state = KingdomBlockStateCodec.parse(encodedState);
                return KingdomBlockStateCodec.buildable(state) && !state.hasBlockEntity();
            } catch (IllegalArgumentException invalid) {
                return false;
            }
        }

        private ServerLevel requireLoaded(String dimension, BlockPos position) {
            ServerLevel level = level(dimension);
            if (level == null || level.getChunkSource().getChunkNow(
                    position.getX() >> 4, position.getZ() >> 4) == null)
                throw new IllegalStateException("unloaded build chunk");
            return level;
        }

        private ServerLevel level(String dimension) {
            ResourceLocation id = ResourceLocation.tryParse(dimension);
            return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        }
    }
}
