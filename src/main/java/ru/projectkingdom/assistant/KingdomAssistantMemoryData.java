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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Bounded owner-scoped summaries; raw snapshots, chats and credentials are never accepted. */
public final class KingdomAssistantMemoryData extends SavedData {
    public static final String DATA_KEY = "kingdom_assistant_memory_v1";
    public static final int MAX_RECORDS = 100;
    public static final int MAX_SUMMARY_CHARS = 4_096;

    public enum RecordType { APPROVED_PLAN, BUILD_SUMMARY, FINDING, MOD_DECISION }

    public record MemoryRecord(UUID ownerId, RecordType type, String summary, long sequence) {}

    private final Map<UUID, ArrayDeque<MemoryRecord>> owners = new LinkedHashMap<>();
    private long nextSequence = 1L;

    public static KingdomAssistantMemoryData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(
                KingdomAssistantMemoryData::load, KingdomAssistantMemoryData::new, DATA_KEY);
    }

    public boolean rememberApprovedPlan(UUID owner, String summary) {
        return remember(owner, RecordType.APPROVED_PLAN, summary);
    }

    public boolean rememberBuildSummary(UUID owner, String summary) {
        return remember(owner, RecordType.BUILD_SUMMARY, summary);
    }

    public boolean rememberFinding(UUID owner, String summary) {
        return remember(owner, RecordType.FINDING, summary);
    }

    public boolean rememberModDecision(UUID owner, String summary) {
        return remember(owner, RecordType.MOD_DECISION, summary);
    }

    public List<MemoryRecord> records(UUID owner) {
        if (owner == null) return List.of();
        ArrayDeque<MemoryRecord> values = owners.get(owner);
        return values == null ? List.of() : List.copyOf(values);
    }

    public int clear(UUID owner) {
        if (owner == null) return 0;
        ArrayDeque<MemoryRecord> removed = owners.remove(owner);
        if (removed == null) return 0;
        setDirty();
        return removed.size();
    }

    @Override public CompoundTag save(CompoundTag root) {
        ListTag rows = new ListTag();
        for (ArrayDeque<MemoryRecord> records : owners.values()) {
            for (MemoryRecord record : records) {
                CompoundTag row = new CompoundTag();
                row.putUUID("Owner", record.ownerId());
                row.putString("Type", record.type().name());
                row.putString("Summary", record.summary());
                row.putLong("Sequence", record.sequence());
                rows.add(row);
            }
        }
        root.put("Records", rows);
        root.putLong("NextSequence", nextSequence);
        return root;
    }

    public static KingdomAssistantMemoryData load(CompoundTag root) {
        KingdomAssistantMemoryData data = new KingdomAssistantMemoryData();
        ListTag rows = root.getList("Records", Tag.TAG_COMPOUND);
        for (int index = 0; index < rows.size(); index++) {
            CompoundTag row = rows.getCompound(index);
            try {
                UUID owner = row.getUUID("Owner");
                RecordType type = RecordType.valueOf(row.getString("Type"));
                String summary = normalizedSafeSummary(row.getString("Summary"));
                if (summary == null) continue;
                long sequence = Math.max(1L, row.getLong("Sequence"));
                ArrayDeque<MemoryRecord> records = data.owners.computeIfAbsent(
                        owner, ignored -> new ArrayDeque<>());
                records.addLast(new MemoryRecord(owner, type, summary, sequence));
                trim(records);
                data.nextSequence = Math.max(data.nextSequence, sequence + 1L);
            } catch (RuntimeException ignored) {
                // Quarantine a corrupt or unsafe row while preserving other owners' summaries.
            }
        }
        data.nextSequence = Math.max(data.nextSequence, Math.max(1L, root.getLong("NextSequence")));
        return data;
    }

    private boolean remember(UUID owner, RecordType type, String rawSummary) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
        String summary = normalizedSafeSummary(rawSummary);
        if (summary == null) return false;
        ArrayDeque<MemoryRecord> records = owners.computeIfAbsent(
                owner, ignored -> new ArrayDeque<>());
        records.addLast(new MemoryRecord(owner, type, summary, nextSequence++));
        trim(records);
        setDirty();
        return true;
    }

    private static String normalizedSafeSummary(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_SUMMARY_CHARS) return null;
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("api_key") || lower.contains("api-key")
                || lower.contains("apikey") || lower.contains("authorization:")
                || lower.contains("bearer ") || lower.contains("sk-")
                || lower.contains("world snapshot") || lower.contains("снимок мира")
                || lower.contains("level.dat") || lower.contains("chunk nbt")
                || lower.contains("\"chunks\"")) return null;
        return normalized;
    }

    private static void trim(ArrayDeque<MemoryRecord> records) {
        while (records.size() > MAX_RECORDS) records.removeFirst();
    }
}
