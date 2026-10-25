package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Saved custody of a real TFC pot batch. Never collect an unclaimed player's food. */
public final class KingdomTfcPotData extends SavedData {
    private static final String NAME="kingdom_tfc_food_pot_v1";
    public record Batch(UUID worker,String dimension,long position,List<String> inputs,List<String> outputs){
        public Batch{
            inputs=List.copyOf(inputs);
            outputs=List.copyOf(outputs);
            if(inputs.isEmpty()||inputs.size()>5||outputs.size()!=inputs.size())
                throw new IllegalArgumentException("pot batch");
        }
    }
    private final Map<UUID,Batch> batches=new LinkedHashMap<>();
    public static KingdomTfcPotData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(
            KingdomTfcPotData::load,KingdomTfcPotData::new,NAME);
    }
    public Batch get(UUID id){return batches.get(id);}
    public boolean reserved(UUID worker,String dimension,long station){
        return batches.values().stream().anyMatch(j->!j.worker().equals(worker)
            && j.dimension().equals(dimension) && j.position()==station);
    }
    public void put(Batch batch){batches.put(batch.worker(),batch);setDirty();}
    public void remove(UUID worker){if(batches.remove(worker)!=null)setDirty();}
    static KingdomTfcPotData load(CompoundTag nbt){
        var data=new KingdomTfcPotData();
        for(Tag raw:nbt.getList("Batches",Tag.TAG_COMPOUND)){
            var t=(CompoundTag)raw;
            if(!t.hasUUID("Worker")||t.getString("Dimension").isBlank())continue;
            ListTag ins=t.getList("Inputs",Tag.TAG_STRING),outs=t.getList("Outputs",Tag.TAG_STRING);
            List<String> ii=new ArrayList<>(),oo=new ArrayList<>();
            for(Tag tag:ins)ii.add(tag.getAsString());
            for(Tag tag:outs)oo.add(tag.getAsString());
            if(ii.isEmpty()||ii.size()>5||ii.size()!=oo.size()
                ||ii.stream().anyMatch(String::isBlank)
                ||oo.stream().anyMatch(String::isBlank))continue;
            try {
                var b=new Batch(t.getUUID("Worker"),t.getString("Dimension"),
                    t.getLong("Position"),ii,oo);
                data.batches.put(b.worker(),b);
            }catch(IllegalArgumentException ignored){}
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root){
        ListTag entries=new ListTag();
        for(Batch batch:batches.values()){
            CompoundTag t=new CompoundTag();t.putUUID("Worker",batch.worker());
            t.putString("Dimension",batch.dimension());t.putLong("Position",batch.position());
            ListTag inputs=new ListTag(),outputs=new ListTag();
            for(String id:batch.inputs())inputs.add(StringTag.valueOf(id));
            for(String id:batch.outputs())outputs.add(StringTag.valueOf(id));
            t.put("Inputs",inputs);t.put("Outputs",outputs);entries.add(t);
        }
        root.put("Batches",entries);return root;
    }
}
