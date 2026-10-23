package ru.projectkingdom.npc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Durable custody for quern inputs inserted by Kingdom workers.
 * Never grants ownership of preexisting player products in the machine.
 */
public final class KingdomTfcQuernData extends SavedData {
    private static final String KEY = "kingdom_tfc_quern_jobs_v1";
    public record Job(UUID worker, String dimension, long station, String input, String output) {}
    private final Map<UUID, Job> jobs = new LinkedHashMap<>();

    public static KingdomTfcQuernData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            KingdomTfcQuernData::load, KingdomTfcQuernData::new, KEY);
    }
    public Job job(UUID worker) { return jobs.get(worker); }
    public boolean reservedByOther(UUID worker, String dimension, long station) {
        for (Job job : jobs.values()) {
            if (!job.worker().equals(worker) && job.dimension().equals(dimension)
                    && job.station() == station) return true;
        }
        return false;
    }
    public void assign(Job job) { jobs.put(job.worker(), job); setDirty(); }
    public void release(UUID worker) {
        if (jobs.remove(worker) != null) setDirty();
    }
    private static KingdomTfcQuernData load(CompoundTag root) {
        KingdomTfcQuernData data = new KingdomTfcQuernData();
        for (Tag raw : root.getList("Jobs", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw;
            if (!tag.hasUUID("Worker") || tag.getString("Dimension").isBlank()
                    || tag.getString("Input").isBlank() || tag.getString("Output").isBlank()) continue;
            Job job = new Job(tag.getUUID("Worker"), tag.getString("Dimension"),
                tag.getLong("Station"), tag.getString("Input"), tag.getString("Output"));
            data.jobs.put(job.worker(), job);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        for (Job job : jobs.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Worker", job.worker()); tag.putString("Dimension", job.dimension());
            tag.putLong("Station", job.station()); tag.putString("Input", job.input());
            tag.putString("Output", job.output()); list.add(tag);
        }
        root.put("Jobs", list);
        return root;
    }
}
