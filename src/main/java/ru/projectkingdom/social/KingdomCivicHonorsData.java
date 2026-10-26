package ru.projectkingdom.social;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * One award per kind and recipient in the granting municipality.
 * Awards never grant money, citizenship, office or another settlement.
 */
public final class KingdomCivicHonorsData extends SavedData {
    private static final String KEY="kingdom_civic_honors_v1";
    private record Key(UUID town, UUID player) {}
    public record Award(KingdomCivicHonor honor,UUID by,long day) {}
    private final Map<Key,EnumMap<KingdomCivicHonor,Award>> awards=new LinkedHashMap<>();
    public static KingdomCivicHonorsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                KingdomCivicHonorsData::load,KingdomCivicHonorsData::new,KEY);
    }
    public Map<KingdomCivicHonor,Award> awards(UUID town,UUID person) {
        if(town==null||person==null)return Map.of();
        Map<KingdomCivicHonor,Award> stored=awards.get(new Key(town,person));
        return stored==null?Map.of():Map.copyOf(stored);
    }
    public KingdomCivicHonor best(UUID town,UUID person) {
        var owned=awards(town,person);
        for(var tier:new KingdomCivicHonor[]{KingdomCivicHonor.DISTINCTION,
                KingdomCivicHonor.MERIT,KingdomCivicHonor.SERVICE})
            if(owned.containsKey(tier))return tier;
        return null;
    }
    /** Preconditions: authorized mayor/founder and verified current resident, rank, reputation. */
    public boolean awardVerified(UUID town,UUID person,KingdomCivicHonor type,UUID issuer,
                                 int socialRank,int reputation,long day){
        if(town==null||person==null||issuer==null||issuer.equals(person)||type==null||day<0
                ||!type.eligible(socialRank,reputation))return false;
        var city=awards.computeIfAbsent(new Key(town,person),
                ignored->new EnumMap<>(KingdomCivicHonor.class));
        if(city.containsKey(type))return false;
        city.put(type,new Award(type,issuer,day));setDirty();return true;
    }
    private static KingdomCivicHonorsData load(CompoundTag root){
        var data=new KingdomCivicHonorsData();
        var list=root.getList("Awards",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++){
            var item=list.getCompound(i);
            if(!item.hasUUID("Town")||!item.hasUUID("Player")||!item.hasUUID("Issuer"))continue;
            try{
                var type=KingdomCivicHonor.valueOf(item.getString("Type"));
                var key=new Key(item.getUUID("Town"),item.getUUID("Player"));
                data.awards.computeIfAbsent(key,unused->new EnumMap<>(KingdomCivicHonor.class))
                        .putIfAbsent(type,new Award(type,item.getUUID("Issuer"),
                                Math.max(0,item.getLong("Day"))));
            }catch(IllegalArgumentException ignored){}
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root){
        var list=new ListTag();
        for(var person:awards.entrySet())for(var item:person.getValue().entrySet()){
            var row=new CompoundTag();
            row.putUUID("Town",person.getKey().town());
            row.putUUID("Player",person.getKey().player());
            row.putUUID("Issuer",item.getValue().by());
            row.putString("Type",item.getKey().name());
            row.putLong("Day",item.getValue().day());list.add(row);
        }
        root.put("Awards",list);return root;
    }
}
