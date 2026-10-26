package ru.projectkingdom.social;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** A sovereign's order of merit: distinct from local civic honors and hereditary nobility. */
public final class KingdomNationalHonorsData extends SavedData {
    private static final String KEY="kingdom_national_honors_v1";
    private record Key(UUID nation,UUID person) {}
    public record Award(UUID issuer,long day) {}
    private final Map<Key,Award> awards=new LinkedHashMap<>();
    public static KingdomNationalHonorsData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(
                KingdomNationalHonorsData::load,KingdomNationalHonorsData::new,KEY);
    }
    public Award award(UUID nation,UUID person){
        return nation==null||person==null?null:awards.get(new Key(nation,person));
    }
    /** Caller must prove legal ruler, shared citizenship and actual service record. */
    public boolean grantVerified(UUID nation,UUID person,UUID issuer,int rank,
                                 int realmReputation,long day){
        if(nation==null||person==null||issuer==null||person.equals(issuer)
                ||rank<5||realmReputation<100||day<0)return false;
        Key key=new Key(nation,person);
        if(awards.containsKey(key))return false;
        awards.put(key,new Award(issuer,day));setDirty();return true;
    }
    private static KingdomNationalHonorsData load(CompoundTag root){
        var data=new KingdomNationalHonorsData();
        var list=root.getList("Awards",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++){
            var item=list.getCompound(i);
            if(!item.hasUUID("Nation")||!item.hasUUID("Player")||!item.hasUUID("Issuer"))continue;
            data.awards.putIfAbsent(new Key(item.getUUID("Nation"),item.getUUID("Player")),
                    new Award(item.getUUID("Issuer"),Math.max(0,item.getLong("Day"))));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag out){
        var list=new ListTag();
        for(var item:awards.entrySet()){
            var row=new CompoundTag();
            row.putUUID("Nation",item.getKey().nation());row.putUUID("Player",item.getKey().person());
            row.putUUID("Issuer",item.getValue().issuer());row.putLong("Day",item.getValue().day());
            list.add(row);
        }
        out.put("Awards",list);return out;
    }
}
