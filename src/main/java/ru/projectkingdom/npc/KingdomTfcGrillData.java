package ru.projectkingdom.npc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent custody of INPUT already placed in an authentic TFC grill slot.
 * Never grants ownership of preexisting, player-supplied machine inventory.
 */
public final class KingdomTfcGrillData extends SavedData {
    private static final String KEY = "kingdom_tfc_grill_v1";
    public record Job(UUID worker, String dimension, long position, int slot,
                      String input, String output) {}
    private final Map<UUID, Job> jobs = new LinkedHashMap<>();
    public static KingdomTfcGrillData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            KingdomTfcGrillData::load, KingdomTfcGrillData::new, KEY);
    }
    public Job get(UUID worker) { return jobs.get(worker); }
    public void put(Job job) { jobs.put(job.worker(), job); setDirty(); }
    public void remove(UUID worker) { if (jobs.remove(worker) != null) setDirty(); }
    static KingdomTfcGrillData load(CompoundTag root) {
        var data = new KingdomTfcGrillData();
        for (Tag raw : root.getList("Jobs", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            if (!t.hasUUID("Worker") || t.getString("Dimension").isBlank()) continue;
            int slot = t.getInt("Slot");
            if (slot < 4 || slot > 8 || t.getString("Input").isBlank()
                || t.getString("Output").isBlank()) continue;
            Job job = new Job(t.getUUID("Worker"), t.getString("Dimension"),
                t.getLong("Position"), slot, t.getString("Input"), t.getString("Output"));
            data.jobs.put(job.worker(), job);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        ListTag rows = new ListTag();
        for (Job job : jobs.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Worker",job.worker()); t.putString("Dimension",job.dimension());
            t.putLong("Position",job.position()); t.putInt("Slot",job.slot());
            t.putString("Input",job.input()); t.putString("Output",job.output());
            rows.add(t);
        }
        tag.put("Jobs",rows);
        return tag;
    }
}
