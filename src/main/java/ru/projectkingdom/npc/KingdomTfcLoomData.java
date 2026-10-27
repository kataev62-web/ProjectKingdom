package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent custody for real TFC looms. It never claims pre-existing player cloth. */
public final class KingdomTfcLoomData extends SavedData {
    private static final String KEY="kingdom_tfc_loom_jobs_v1";
    public record Job(UUID worker,String dimension,long station,String input,String output,
                      int needed) {
        public Job {
            if(worker==null||dimension==null||dimension.isBlank()||input==null||input.isBlank()
                ||output==null||output.isBlank()||needed<1||needed>64)
                throw new IllegalArgumentException("Invalid loom job");
        }
    }
    private final Map<UUID,Job> jobs=new LinkedHashMap<>();
    public static KingdomTfcLoomData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(
            KingdomTfcLoomData::load,KingdomTfcLoomData::new,KEY);
    }
    public Job job(UUID worker){return jobs.get(worker);}
    public boolean claimedByOther(UUID worker,String dimension,long position){
        return jobs.values().stream().anyMatch(job->!worker.equals(job.worker())
            && dimension.equals(job.dimension())&&position==job.station());
    }
    public boolean claim(Job job){
        if(claimedByOther(job.worker(),job.dimension(),job.station()))return false;
        jobs.put(job.worker(),job);setDirty();return true;
    }
    public void release(UUID worker){if(jobs.remove(worker)!=null)setDirty();}
    static KingdomTfcLoomData load(CompoundTag tag){
        var out=new KingdomTfcLoomData();
        for(Tag entry:tag.getList("Jobs",Tag.TAG_COMPOUND)){
            CompoundTag row=(CompoundTag)entry;
            if(!row.hasUUID("Worker"))continue;
            try{
                var job=new Job(row.getUUID("Worker"),row.getString("Dimension"),
                    row.getLong("Station"),row.getString("Input"),
                    row.getString("Output"),row.getInt("Needed"));
                if(!out.claimedByOther(job.worker(),job.dimension(),job.station()))
                    out.jobs.put(job.worker(),job);
            }catch(IllegalArgumentException ignored){}
        }
        return out;
    }
    @Override public CompoundTag save(CompoundTag tag){
        ListTag entries=new ListTag();
        for(Job job:jobs.values()){
            CompoundTag row=new CompoundTag();
            row.putUUID("Worker",job.worker());row.putString("Dimension",job.dimension());
            row.putLong("Station",job.station());row.putString("Input",job.input());
            row.putString("Output",job.output());row.putInt("Needed",job.needed());
            entries.add(row);
        }
        tag.put("Jobs",entries);return tag;
    }
}
