package ru.projectkingdom.social;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** A bounded city petition docket. Approval is a public decision, not a budget transfer. */
public final class KingdomCitizenPetitionsData extends SavedData {
    private static final String KEY="kingdom_civic_petitions_v1";
    public enum Topic { ROADS("Дороги"), MARKET("Рынок"), SAFETY("Безопасность");
        private final String russian;Topic(String russian){this.russian=russian;}
        public String russian(){return russian;}
    }
    public enum Status { PENDING, APPROVED, REJECTED }
    public record Petition(UUID id,UUID town,UUID petitioner,Topic topic,Status status,
                           long submittedDay,UUID reviewer,long reviewedDay) {}
    private final Map<UUID,Petition> entries=new LinkedHashMap<>();
    private final Map<String,Long> latestByMember=new LinkedHashMap<>();
    public static final int MAX_ACTIVE_PER_TOWN=32,MAX_HISTORY_PER_TOWN=160;
    public static final long COOLDOWN_DAYS=3L;
    public static KingdomCitizenPetitionsData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(
                KingdomCitizenPetitionsData::load,KingdomCitizenPetitionsData::new,KEY);
    }
    private static String key(UUID town,UUID member){return town+"|"+member;}
    public List<Petition> pending(UUID town,int limit){
        if(town==null||limit<=0)return List.of();
        return entries.values().stream().filter(v->town.equals(v.town())&&v.status()==Status.PENDING)
                .limit(Math.min(limit,MAX_ACTIVE_PER_TOWN)).toList();
    }
    public List<Petition> forMember(UUID town,UUID member,int limit){
        if(town==null||member==null||limit<=0)return List.of();
        var values=entries.values().stream().filter(v->town.equals(v.town())&&member.equals(v.petitioner())).toList();
        return values.subList(Math.max(0,values.size()-Math.min(limit,20)),values.size());
    }
    /** Trusted transaction: rank/reputation and membership must be confirmed by caller. */
    public Petition fileVerified(UUID town,UUID member,Topic topic,int rank,int reputation,long day){
        if(town==null||member==null||topic==null||day<0||rank<3||reputation<30)return null;
        long prev=latestByMember.getOrDefault(key(town,member),Long.MIN_VALUE);
        if(prev!=Long.MIN_VALUE && (day<prev||day-prev<COOLDOWN_DAYS))return null;
        if(pending(town,MAX_ACTIVE_PER_TOWN).size()>=MAX_ACTIVE_PER_TOWN)return null;
        long personal=entries.values().stream().filter(v->town.equals(v.town())&&member.equals(v.petitioner())
                &&v.status()==Status.PENDING).count();
        if(personal>=2)return null;
        var value=new Petition(UUID.randomUUID(),town,member,topic,Status.PENDING,day,null,-1);
        entries.put(value.id(),value);latestByMember.put(key(town,member),day);
        trim(town);
        setDirty();return value;
    }
    public Petition reviewOldest(UUID town,UUID expectedId,UUID reviewer,boolean approve,long day){
        if(town==null||expectedId==null||reviewer==null||day<0)return null;
        var open=pending(town,MAX_ACTIVE_PER_TOWN).stream()
                .filter(p->!reviewer.equals(p.petitioner())).findFirst();
        if(open.isEmpty()||!expectedId.equals(open.get().id())||day<open.get().submittedDay())return null;
        var old=open.get();
        var changed=new Petition(old.id(),town,old.petitioner(),old.topic(),
                approve?Status.APPROVED:Status.REJECTED,old.submittedDay(),reviewer,day);
        entries.put(changed.id(),changed);trim(town);setDirty();return changed;
    }
    private void trim(UUID town){
        var completed=new ArrayList<UUID>();
        for(var p:entries.values())if(town.equals(p.town())&&p.status()!=Status.PENDING)completed.add(p.id());
        for(int i=0;i<completed.size()-MAX_HISTORY_PER_TOWN;i++)entries.remove(completed.get(i));
    }
    private static KingdomCitizenPetitionsData load(CompoundTag root){
        var data=new KingdomCitizenPetitionsData();
        var rows=root.getList("Petitions",Tag.TAG_COMPOUND);
        for(int i=0;i<rows.size();i++){
            var r=rows.getCompound(i);
            if(!r.hasUUID("Id")||!r.hasUUID("Town")||!r.hasUUID("Petitioner"))continue;
            try{
                var item=new Petition(r.getUUID("Id"),r.getUUID("Town"),r.getUUID("Petitioner"),
                        Topic.valueOf(r.getString("Topic")),Status.valueOf(r.getString("Status")),
                        Math.max(0,r.getLong("Day")),r.hasUUID("Reviewer")?r.getUUID("Reviewer"):null,
                        r.getLong("ReviewedDay"));
                data.entries.putIfAbsent(item.id(),item);
            }catch(IllegalArgumentException ignored){}
        }
        for(var p:data.entries.values()){
            String key=key(p.town(),p.petitioner());
            data.latestByMember.merge(key,p.submittedDay(),Math::max);
        }
        var cooldownRows=root.getList("Cooldowns",Tag.TAG_COMPOUND);
        for(int i=0;i<cooldownRows.size();i++){
            var row=cooldownRows.getCompound(i);
            if(!row.hasUUID("Town")||!row.hasUUID("Member"))continue;
            data.latestByMember.merge(key(row.getUUID("Town"),row.getUUID("Member")),
                    Math.max(0,row.getLong("Day")),Math::max);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root){
        var list=new ListTag();
        for(var item:entries.values()){
            var r=new CompoundTag();r.putUUID("Id",item.id());
            r.putUUID("Town",item.town());r.putUUID("Petitioner",item.petitioner());
            r.putString("Topic",item.topic().name());r.putString("Status",item.status().name());
            r.putLong("Day",item.submittedDay());r.putLong("ReviewedDay",item.reviewedDay());
            if(item.reviewer()!=null)r.putUUID("Reviewer",item.reviewer());
            list.add(r);
        }
        root.put("Petitions",list);
        var cooldowns=new ListTag();
        for(var byMember:latestByMember.entrySet()){
            int split=byMember.getKey().indexOf('|');if(split<0)continue;
            try{
                var row=new CompoundTag();
                row.putUUID("Town",UUID.fromString(byMember.getKey().substring(0,split)));
                row.putUUID("Member",UUID.fromString(byMember.getKey().substring(split+1)));
                row.putLong("Day",byMember.getValue());cooldowns.add(row);
            }catch(IllegalArgumentException ignored){}
        }
        root.put("Cooldowns",cooldowns);return root;
    }
}
