package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Native TFC tannery barrels: ownership of a real in-progress item, never of an arbitrary barrel. */
public final class KingdomTfcTanneryData extends SavedData {
    private static final String KEY="kingdom_tfc_tannery_jobs_v1";
    public record Batch(UUID worker,String dimension,long station,
                        String inputId,String outputId,String recipeId) {
        public Batch {
            if(worker==null || dimension==null||dimension.isBlank()||station==0
                ||inputId==null||inputId.isBlank()||outputId==null||outputId.isBlank()
                ||recipeId==null||!KingdomTfcTanneryWork.supportedRecipe(recipeId))
                throw new IllegalArgumentException("Invalid native hide batch");
        }
    }
    private final Map<UUID,Batch> jobs=new LinkedHashMap<>();
    public static KingdomTfcTanneryData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(
            KingdomTfcTanneryData::load,KingdomTfcTanneryData::new,KEY);
    }
    public Batch get(UUID worker){return jobs.get(worker);}
    public boolean occupied(UUID worker,String dimension,long station){
        return jobs.values().stream().anyMatch(x->!x.worker().equals(worker)
            &&x.dimension().equals(dimension)&&x.station()==station);
    }
    public boolean claim(Batch batch) {
        if(occupied(batch.worker(),batch.dimension(),batch.station()))return false;
        jobs.put(batch.worker(),batch);setDirty();return true;
    }
    public void release(UUID worker){if(jobs.remove(worker)!=null)setDirty();}
    static KingdomTfcTanneryData load(CompoundTag nbt){
        var data=new KingdomTfcTanneryData();
        for(Tag tag:nbt.getList("Jobs",Tag.TAG_COMPOUND)){
            var row=(CompoundTag)tag;
            if(!row.hasUUID("Worker"))continue;
            try{
                var batch=new Batch(row.getUUID("Worker"),row.getString("Dimension"),
                    row.getLong("Station"),row.getString("Input"),
                    row.getString("Output"),row.getString("Recipe"));
                if(!data.occupied(batch.worker(),batch.dimension(),batch.station()))
                    data.jobs.put(batch.worker(),batch);
            }catch(IllegalArgumentException ignored){}
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag nbt) {
        var jobsTag=new ListTag();
        for(Batch batch:jobs.values()){
            var row=new CompoundTag();
            row.putUUID("Worker",batch.worker());
            row.putString("Dimension",batch.dimension());row.putLong("Station",batch.station());
            row.putString("Input",batch.inputId());row.putString("Output",batch.outputId());
            row.putString("Recipe",batch.recipeId());jobsTag.add(row);
        }
        nbt.put("Jobs",jobsTag);
        return nbt;
    }
}
