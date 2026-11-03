package ru.projectkingdom.cityarchitect.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CityArchitectBuildService {
    public static final int MAX_CHANGES_PER_TICK = 16;

    private final Store store;
    private final WorldAccess world;

    public CityArchitectBuildService(Store store, WorldAccess world) {
        this.store = Objects.requireNonNull(store, "store");
        this.world = Objects.requireNonNull(world, "world");
    }

    public synchronized void queue(
            UUID planId, UUID stageId, List<CityArchitectRoadDesignFactory.RoadCell> input
    ) {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(stageId, "stageId");
        List<CityArchitectRoadDesignFactory.RoadCell> requested =
                List.copyOf(Objects.requireNonNull(input, "cells"));
        if (requested.isEmpty() || requested.size() > CityArchitectRoadDesignFactory.MAX_CELLS) {
            throw new IllegalArgumentException("Invalid build queue size");
        }
        if (store.get(planId).isPresent()) {
            throw new IllegalStateException("Plan already has a build queue");
        }
        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
        List<BuildCell> cells = new ArrayList<>();
        for (var cell : requested) {
            if (positions.add(cell.position())) {
                cells.add(new BuildCell(cell.position(), cell.stateId(), "", CellStatus.QUEUED));
            }
        }
        store.put(new BuildQueue(planId, stageId, cells));
    }

    public synchronized TickResult tick(int requestedBudget) {
        int budget = Math.min(MAX_CHANGES_PER_TICK, Math.max(0, requestedBudget));
        int attempts = 0;
        int changed = 0;
        int refused = 0;
        int paused = 0;
        for (BuildQueue queue : store.all()) {
            List<BuildCell> cells = new ArrayList<>(queue.cells());
            boolean dirty = false;
            for (int index = 0; index < cells.size() && attempts < budget; index++) {
                BuildCell cell = cells.get(index);
                if (cell.status() != CellStatus.QUEUED) {
                    continue;
                }
                if (!world.loaded(cell.position())) {
                    paused++;
                    break;
                }
                attempts++;
                WorldCell before = world.inspect(cell.position());
                if (!world.safeReplaceable(cell.position(), before)) {
                    cells.set(index, cell.with(before.stateId(), CellStatus.REFUSED));
                    refused++;
                    dirty = true;
                    continue;
                }
                CellStatus status = world.setExact(cell.position(), before.stateId(), cell.targetState())
                        ? CellStatus.APPLIED : CellStatus.CONFLICT;
                cells.set(index, cell.with(before.stateId(), status));
                if (status == CellStatus.APPLIED) {
                    changed++;
                } else {
                    refused++;
                }
                dirty = true;
            }
            if (dirty) {
                store.put(new BuildQueue(queue.planId(), queue.stageId(), cells));
            }
            if (attempts >= budget) {
                break;
            }
        }
        return new TickResult(changed, refused, paused);
    }

    public synchronized RollbackResult rollback(UUID planId) {
        BuildQueue queue = store.get(planId).orElseThrow(() -> new IllegalArgumentException("Unknown build queue"));
        List<BuildCell> cells = new ArrayList<>(queue.cells());
        int restored = 0;
        int conflicts = 0;
        int paused = 0;
        for (int index = cells.size() - 1; index >= 0; index--) {
            BuildCell cell = cells.get(index);
            if (cell.status() != CellStatus.APPLIED) {
                continue;
            }
            if (!world.loaded(cell.position())) {
                paused++;
                continue;
            }
            boolean conflict = !world.inspect(cell.position()).stateId().equals(cell.targetState());
            if (!conflict) {
                conflict = !world.setExact(cell.position(), cell.targetState(), cell.beforeState());
            }
            cells.set(index, cell.with(cell.beforeState(),
                    conflict ? CellStatus.CONFLICT : CellStatus.ROLLED_BACK));
            if (conflict) {
                conflicts++;
            } else {
                restored++;
            }
        }
        store.put(new BuildQueue(queue.planId(), queue.stageId(), cells));
        return new RollbackResult(restored, conflicts, paused);
    }

    public synchronized void resume(UUID planId) {
        if (store.get(planId).isEmpty()) {
            throw new IllegalArgumentException("Unknown build queue");
        }
    }

    public synchronized boolean complete(UUID planId) {
        return store.get(planId)
                .map(queue -> queue.cells().stream().noneMatch(cell -> cell.status() == CellStatus.QUEUED))
                .orElse(false);
    }

    public interface WorldAccess {
        boolean loaded(BlockPos position);

        WorldCell inspect(BlockPos position);

        boolean safeReplaceable(BlockPos position, WorldCell cell);

        boolean setExact(BlockPos position, String expectedState, String replacementState);
    }

    public interface Store {
        Optional<BuildQueue> get(UUID planId);

        List<BuildQueue> all();

        void put(BuildQueue queue);

        void remove(UUID planId);
    }

    public static final class MemoryStore implements Store {
        private final Map<UUID, BuildQueue> queues = new LinkedHashMap<>();

        @Override
        public synchronized Optional<BuildQueue> get(UUID planId) {
            return Optional.ofNullable(queues.get(planId));
        }

        @Override
        public synchronized List<BuildQueue> all() {
            return List.copyOf(queues.values());
        }

        @Override
        public synchronized void put(BuildQueue queue) {
            queues.put(queue.planId(), queue);
        }

        @Override
        public synchronized void remove(UUID planId) {
            queues.remove(planId);
        }
    }

    public static final class SavedStore extends SavedData implements Store {
        private static final String DATA_KEY = "kingdom_city_architect_builds_v1";
        private final Map<UUID, BuildQueue> queues = new LinkedHashMap<>();

        public static SavedStore get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(
                    SavedStore::load, SavedStore::new, DATA_KEY);
        }

        @Override
        public synchronized Optional<BuildQueue> get(UUID planId) {
            return Optional.ofNullable(queues.get(planId));
        }

        @Override
        public synchronized List<BuildQueue> all() {
            return List.copyOf(queues.values());
        }

        @Override
        public synchronized void put(BuildQueue queue) {
            queues.put(queue.planId(), queue);
            setDirty();
        }

        @Override
        public synchronized void remove(UUID planId) {
            if (queues.remove(planId) != null) {
                setDirty();
            }
        }

        @Override
        public synchronized CompoundTag save(CompoundTag root) {
            ListTag rows = new ListTag();
            for (BuildQueue queue : queues.values()) {
                CompoundTag row = new CompoundTag();
                row.putUUID("Plan", queue.planId());
                row.putUUID("Stage", queue.stageId());
                ListTag cellRows = new ListTag();
                for (BuildCell cell : queue.cells()) {
                    CompoundTag saved = new CompoundTag();
                    saved.putLong("Pos", cell.position().asLong());
                    saved.putString("Target", cell.targetState());
                    saved.putString("Before", cell.beforeState());
                    saved.putString("Status", cell.status().name());
                    cellRows.add(saved);
                }
                row.put("Cells", cellRows);
                rows.add(row);
            }
            root.put("Queues", rows);
            return root;
        }

        private static SavedStore load(CompoundTag root) {
            SavedStore store = new SavedStore();
            ListTag rows = root.getList("Queues", Tag.TAG_COMPOUND);
            for (Tag raw : rows) {
                try {
                    CompoundTag row = (CompoundTag) raw;
                    UUID plan = row.getUUID("Plan");
                    UUID stage = row.getUUID("Stage");
                    ListTag cellRows = row.getList("Cells", Tag.TAG_COMPOUND);
                    if (cellRows.isEmpty() || cellRows.size() > CityArchitectRoadDesignFactory.MAX_CELLS) {
                        continue;
                    }
                    List<BuildCell> cells = new ArrayList<>();
                    for (Tag cellRaw : cellRows) {
                        CompoundTag cell = (CompoundTag) cellRaw;
                        cells.add(new BuildCell(BlockPos.of(cell.getLong("Pos")),
                                cell.getString("Target"), cell.getString("Before"),
                                CellStatus.valueOf(cell.getString("Status"))));
                    }
                    store.queues.put(plan, new BuildQueue(plan, stage, cells));
                } catch (RuntimeException ignored) {
                    // Quarantine one corrupt queue and preserve the rest.
                }
            }
            return store;
        }
    }

    public enum CellStatus { QUEUED, APPLIED, REFUSED, ROLLED_BACK, CONFLICT }

    public record WorldCell(String stateId, boolean hasBlockEntity, boolean unbreakable) {
        public WorldCell {
            stateId = requireState(stateId, false);
        }
    }

    public record BuildCell(BlockPos position, String targetState, String beforeState, CellStatus status) {
        public BuildCell {
            position = Objects.requireNonNull(position, "position").immutable();
            targetState = requireState(targetState, false);
            beforeState = requireState(beforeState, true);
            Objects.requireNonNull(status, "status");
        }

        public BuildCell with(String before, CellStatus nextStatus) {
            return new BuildCell(position, targetState, before, nextStatus);
        }
    }

    public record BuildQueue(UUID planId, UUID stageId, List<BuildCell> cells) {
        public BuildQueue {
            Objects.requireNonNull(planId, "planId");
            Objects.requireNonNull(stageId, "stageId");
            cells = List.copyOf(Objects.requireNonNull(cells, "cells"));
        }
    }

    public record TickResult(int changed, int refused, int paused) {
    }

    public record RollbackResult(int restored, int conflicts, int paused) {
    }

    private static String requireState(String state, boolean allowEmpty) {
        String checked = Objects.requireNonNull(state, "state").trim();
        if (!allowEmpty && checked.isEmpty()) {
            throw new IllegalArgumentException("state must not be blank");
        }
        return checked;
    }
}
