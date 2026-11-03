package ru.projectkingdom.assistant;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import ru.projectkingdom.npc.KingdomDesignEngine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Durable creative build plans and before-images for conflict-safe recovery. */
public final class KingdomAssistantBuildData extends SavedData {
    public static final String DATA_KEY = "kingdom_assistant_builds_v1";
    public static final int MAX_CELLS = 8_192;

    public enum Status { QUEUED, BUILDING, COMPLETE, UNDOING, UNDONE, FAILED }

    public record PlanCell(int x, int y, int z, String placed) {
        public PlanCell {
            placed = requireState(placed);
        }
    }

    static final class Change {
        final int planIndex;
        final int x;
        final int y;
        final int z;
        final String original;
        final String placed;
        boolean applied;
        boolean resolved;

        Change(int planIndex, int x, int y, int z, String original, String placed,
               boolean applied, boolean resolved) {
            this.planIndex = planIndex;
            this.x = x;
            this.y = y;
            this.z = z;
            this.original = requireState(original);
            this.placed = requireState(placed);
            this.applied = applied;
            this.resolved = resolved;
        }
    }

    static final class Entry {
        final UUID id;
        final UUID owner;
        final UUID assistant;
        final String dimension;
        final String designKey;
        final BlockPos origin;
        final List<PlanCell> plan;
        final List<Change> changes = new ArrayList<>();
        Status status = Status.QUEUED;
        int cursor;
        int undoCursor = -1;
        int restored;
        int conflicts;
        int failed;

        Entry(UUID id, UUID owner, UUID assistant, String dimension, String designKey,
              BlockPos origin, List<PlanCell> plan) {
            this.id = Objects.requireNonNull(id, "id");
            this.owner = Objects.requireNonNull(owner, "owner");
            this.assistant = Objects.requireNonNull(assistant, "assistant");
            this.dimension = requireText(dimension, "dimension");
            this.designKey = requireText(designKey, "designKey");
            this.origin = Objects.requireNonNull(origin, "origin").immutable();
            this.plan = List.copyOf(plan);
        }
    }

    public record BuildView(
            UUID id,
            UUID owner,
            UUID assistant,
            String dimension,
            String designKey,
            BlockPos origin,
            int cells,
            int cursor,
            Status status,
            int restored,
            int conflicts,
            int failed
    ) {}

    private final Map<UUID, Entry> builds = new LinkedHashMap<>();

    public static KingdomAssistantBuildData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(
                KingdomAssistantBuildData::load, KingdomAssistantBuildData::new, DATA_KEY);
    }

    UUID create(UUID owner, UUID assistant, String dimension, String designKey,
                BlockPos origin, KingdomDesignEngine.Design design) {
        Objects.requireNonNull(design, "design");
        if (design.blocks().isEmpty() || design.blocks().size() > MAX_CELLS)
            throw new IllegalArgumentException("invalid plan size");
        List<PlanCell> plan = design.blocks().stream()
                .map(cell -> new PlanCell(cell.x(), cell.y(), cell.z(), cell.material())).toList();
        UUID id = UUID.randomUUID();
        builds.put(id, new Entry(id, owner, assistant, dimension, designKey, origin, plan));
        setDirty();
        return id;
    }

    public Optional<BuildView> build(UUID id) {
        Entry entry = id == null ? null : builds.get(id);
        return entry == null ? Optional.empty() : Optional.of(view(entry));
    }

    public Optional<BuildView> latest(UUID owner) {
        if (owner == null) return Optional.empty();
        Entry latest = null;
        for (Entry entry : builds.values()) if (owner.equals(entry.owner)) latest = entry;
        return latest == null ? Optional.empty() : Optional.of(view(latest));
    }

    boolean hasActive(UUID owner) {
        return builds.values().stream().anyMatch(entry -> entry.owner.equals(owner)
                && (entry.status == Status.QUEUED || entry.status == Status.BUILDING
                || entry.status == Status.UNDOING));
    }

    List<Entry> entries() { return new ArrayList<>(builds.values()); }
    Entry entry(UUID id) { return builds.get(id); }
    void touch() { setDirty(); }

    Change changeFor(Entry entry, int planIndex) {
        for (Change change : entry.changes) if (change.planIndex == planIndex) return change;
        return null;
    }

    Change journal(Entry entry, int planIndex, PlanCell cell, String original) {
        Change existing = changeFor(entry, planIndex);
        if (existing != null) return existing;
        Change change = new Change(planIndex, cell.x(), cell.y(), cell.z(),
                original, cell.placed(), false, false);
        entry.changes.add(change);
        setDirty();
        return change;
    }

    @Override public CompoundTag save(CompoundTag root) {
        ListTag rows = new ListTag();
        for (Entry entry : builds.values()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Id", entry.id);
            row.putUUID("Owner", entry.owner);
            row.putUUID("Assistant", entry.assistant);
            row.putString("Dimension", entry.dimension);
            row.putString("Design", entry.designKey);
            row.putLong("Origin", entry.origin.asLong());
            row.putString("Status", entry.status.name());
            row.putInt("Cursor", entry.cursor);
            row.putInt("UndoCursor", entry.undoCursor);
            row.putInt("Restored", entry.restored);
            row.putInt("Conflicts", entry.conflicts);
            row.putInt("Failed", entry.failed);
            ListTag plan = new ListTag();
            for (PlanCell cell : entry.plan) {
                CompoundTag item = new CompoundTag();
                item.putInt("X", cell.x()); item.putInt("Y", cell.y()); item.putInt("Z", cell.z());
                item.putString("Placed", cell.placed());
                plan.add(item);
            }
            row.put("Plan", plan);
            ListTag changes = new ListTag();
            for (Change change : entry.changes) {
                CompoundTag item = new CompoundTag();
                item.putInt("Index", change.planIndex);
                item.putInt("X", change.x); item.putInt("Y", change.y); item.putInt("Z", change.z);
                item.putString("Original", change.original);
                item.putString("Placed", change.placed);
                item.putBoolean("Applied", change.applied);
                item.putBoolean("Resolved", change.resolved);
                changes.add(item);
            }
            row.put("Changes", changes);
            rows.add(row);
        }
        root.put("Builds", rows);
        return root;
    }

    public static KingdomAssistantBuildData load(CompoundTag root) {
        KingdomAssistantBuildData data = new KingdomAssistantBuildData();
        ListTag rows = root.getList("Builds", Tag.TAG_COMPOUND);
        for (int index = 0; index < rows.size(); index++) {
            CompoundTag row = rows.getCompound(index);
            try {
                ListTag savedPlan = row.getList("Plan", Tag.TAG_COMPOUND);
                if (savedPlan.isEmpty() || savedPlan.size() > MAX_CELLS) continue;
                List<PlanCell> plan = new ArrayList<>(savedPlan.size());
                for (int cellIndex = 0; cellIndex < savedPlan.size(); cellIndex++) {
                    CompoundTag cell = savedPlan.getCompound(cellIndex);
                    plan.add(new PlanCell(cell.getInt("X"), cell.getInt("Y"), cell.getInt("Z"),
                            cell.getString("Placed")));
                }
                Entry entry = new Entry(row.getUUID("Id"), row.getUUID("Owner"),
                        row.getUUID("Assistant"), row.getString("Dimension"),
                        row.getString("Design"), BlockPos.of(row.getLong("Origin")), plan);
                entry.status = Status.valueOf(row.getString("Status"));
                entry.cursor = bounded(row.getInt("Cursor"), 0, plan.size());
                entry.restored = Math.max(0, row.getInt("Restored"));
                entry.conflicts = Math.max(0, row.getInt("Conflicts"));
                entry.failed = Math.max(0, row.getInt("Failed"));
                ListTag savedChanges = row.getList("Changes", Tag.TAG_COMPOUND);
                if (savedChanges.size() > MAX_CELLS) continue;
                for (int changeIndex = 0; changeIndex < savedChanges.size(); changeIndex++) {
                    CompoundTag change = savedChanges.getCompound(changeIndex);
                    int planIndex = change.getInt("Index");
                    if (planIndex < 0 || planIndex >= plan.size()) throw new IllegalArgumentException();
                    entry.changes.add(new Change(planIndex, change.getInt("X"), change.getInt("Y"),
                            change.getInt("Z"), change.getString("Original"),
                            change.getString("Placed"), change.getBoolean("Applied"),
                            change.getBoolean("Resolved")));
                }
                entry.undoCursor = bounded(row.getInt("UndoCursor"), -1,
                        entry.changes.size() - 1);
                data.builds.put(entry.id, entry);
            } catch (RuntimeException ignored) {
                // Quarantine only the corrupt build; other recovery journals remain available.
            }
        }
        return data;
    }

    private static BuildView view(Entry entry) {
        return new BuildView(entry.id, entry.owner, entry.assistant, entry.dimension,
                entry.designKey, entry.origin, entry.plan.size(), entry.cursor, entry.status,
                entry.restored, entry.conflicts, entry.failed);
    }

    private static int bounded(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty() || normalized.length() > 512)
            throw new IllegalArgumentException(field);
        return normalized;
    }

    private static String requireState(String value) {
        String normalized = Objects.requireNonNull(value, "state").trim();
        if (normalized.isEmpty() || normalized.length() > 240)
            throw new IllegalArgumentException("state");
        return normalized;
    }
}
