package ru.projectkingdom.assistant;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Persistent owner binding and task state for the personal server assistant. */
public final class KingdomAssistantData extends SavedData {
    public static final String DATA_KEY = "kingdom_assistant_v1";
    public static final int MAX_HISTORY = 20;

    public enum Mode { FOLLOW, STAY }

    public record OwnerState(
            UUID ownerId,
            UUID entityId,
            String dimension,
            Mode mode,
            UUID activeTaskId,
            AssistantIntent activeIntent,
            List<String> history
    ) {
        public OwnerState {
            history = List.copyOf(history);
        }
    }

    private static final class Entry {
        private final UUID ownerId;
        private final UUID entityId;
        private final String dimension;
        private Mode mode = Mode.FOLLOW;
        private UUID activeTaskId;
        private AssistantIntent activeIntent;
        private final ArrayDeque<String> history = new ArrayDeque<>();

        private Entry(UUID ownerId, UUID entityId, String dimension) {
            this.ownerId = ownerId;
            this.entityId = entityId;
            this.dimension = dimension;
        }

        private OwnerState snapshot() {
            return new OwnerState(ownerId, entityId, dimension, mode,
                    activeTaskId, activeIntent, new ArrayList<>(history));
        }
    }

    private final Map<UUID, Entry> owners = new LinkedHashMap<>();

    public static KingdomAssistantData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(
                KingdomAssistantData::load, KingdomAssistantData::new, DATA_KEY);
    }

    /** Idempotently binds one entity to an owner; a different second entity is rejected. */
    public boolean bind(UUID owner, UUID entity, String dimension) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(entity, "entity");
        String normalizedDimension = Objects.requireNonNull(dimension, "dimension").trim();
        if (normalizedDimension.isEmpty()) {
            throw new IllegalArgumentException("dimension must not be blank");
        }
        Entry current = owners.get(owner);
        if (current != null) {
            return current.entityId.equals(entity) && current.dimension.equals(normalizedDimension);
        }
        owners.put(owner, new Entry(owner, entity, normalizedDimension));
        setDirty();
        return true;
    }

    public Optional<OwnerState> ownedBy(UUID owner) {
        if (owner == null) return Optional.empty();
        Entry entry = owners.get(owner);
        return entry == null ? Optional.empty() : Optional.of(entry.snapshot());
    }

    public boolean setMode(UUID owner, Mode mode) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(mode, "mode");
        Entry entry = owners.get(owner);
        if (entry == null) return false;
        if (entry.mode != mode) {
            entry.mode = mode;
            setDirty();
        }
        return true;
    }

    public boolean beginTask(UUID owner, UUID taskId, AssistantIntent intent) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(intent, "intent");
        Entry entry = owners.get(owner);
        if (entry == null || entry.activeTaskId != null) return false;
        entry.activeTaskId = taskId;
        entry.activeIntent = intent;
        setDirty();
        return true;
    }

    public boolean finishTask(UUID owner, String summary) {
        Objects.requireNonNull(owner, "owner");
        Entry entry = owners.get(owner);
        if (entry == null || entry.activeTaskId == null) return false;
        String normalizedSummary = Objects.requireNonNull(summary, "summary").trim();
        if (normalizedSummary.isEmpty()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        entry.activeTaskId = null;
        entry.activeIntent = null;
        entry.history.addLast(normalizedSummary);
        trimHistory(entry.history);
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag rows = new ListTag();
        for (Entry entry : owners.values()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Owner", entry.ownerId);
            row.putUUID("Entity", entry.entityId);
            row.putString("Dimension", entry.dimension);
            row.putString("Mode", entry.mode.name());
            if (entry.activeTaskId != null && entry.activeIntent != null) {
                row.putUUID("Task", entry.activeTaskId);
                row.putString("Intent", entry.activeIntent.name());
            }
            ListTag history = new ListTag();
            for (String summary : entry.history) {
                CompoundTag item = new CompoundTag();
                item.putString("Summary", summary);
                history.add(item);
            }
            row.put("History", history);
            rows.add(row);
        }
        root.put("Owners", rows);
        return root;
    }

    public static KingdomAssistantData load(CompoundTag root) {
        KingdomAssistantData data = new KingdomAssistantData();
        ListTag rows = root.getList("Owners", Tag.TAG_COMPOUND);
        for (int index = 0; index < rows.size(); index++) {
            CompoundTag row = rows.getCompound(index);
            try {
                UUID owner = row.getUUID("Owner");
                UUID entity = row.getUUID("Entity");
                String dimension = row.getString("Dimension").trim();
                if (dimension.isEmpty() || data.owners.containsKey(owner)) continue;
                Entry entry = new Entry(owner, entity, dimension);
                entry.mode = parseMode(row.getString("Mode"));
                if (row.hasUUID("Task")) {
                    entry.activeTaskId = row.getUUID("Task");
                    entry.activeIntent = AssistantIntent.valueOf(row.getString("Intent"));
                }
                ListTag history = row.getList("History", Tag.TAG_COMPOUND);
                int start = Math.max(0, history.size() - MAX_HISTORY);
                for (int historyIndex = start; historyIndex < history.size(); historyIndex++) {
                    String summary = history.getCompound(historyIndex).getString("Summary").trim();
                    if (!summary.isEmpty()) entry.history.addLast(summary);
                }
                trimHistory(entry.history);
                data.owners.put(owner, entry);
            } catch (RuntimeException ignored) {
                // Quarantine a corrupt owner row while preserving the rest of the world data.
            }
        }
        return data;
    }

    private static Mode parseMode(String raw) {
        try {
            return Mode.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return Mode.FOLLOW;
        }
    }

    private static void trimHistory(ArrayDeque<String> history) {
        while (history.size() > MAX_HISTORY) history.removeFirst();
    }
}
