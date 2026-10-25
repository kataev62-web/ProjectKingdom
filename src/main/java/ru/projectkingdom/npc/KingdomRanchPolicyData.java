package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/** Explicit livestock policy. Culling is DISABLED by default. */
public final class KingdomRanchPolicyData extends SavedData {
    private static final String KEY="kingdom_ranch_policy_v1";
    public record Policy(boolean cullOld,int minHerd,long lastCullDay){}
    private final Map<UUID,Policy> policies=new LinkedHashMap<>();
    public static KingdomRanchPolicyData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(
            KingdomRanchPolicyData::load,KingdomRanchPolicyData::new,KEY);
    }
    public Policy policy(UUID pen){return policies.getOrDefault(pen,new Policy(false,6,-1));}
    public boolean toggleCull(ServerPlayer player,UUID farm,UUID pen){
        if(!KingdomFarmFieldData.allowed(player,farm)
            ||KingdomRanchPenData.get(player.getServer()).of(farm).stream()
                .noneMatch(p->p.id().equals(pen)))return false;
        Policy old=policy(pen);
        policies.put(pen,new Policy(!old.cullOld(),old.minHerd(),old.lastCullDay()));
        setDirty();return true;
    }
    /** Only a confirmed, synchronous successful real animal death spends a quota. */
    public void markCull(UUID pen,long day){
        Policy old=policy(pen);
        policies.put(pen,new Policy(old.cullOld(),old.minHerd(),day));
        setDirty();
    }
    public static boolean mayCull(Policy policy,long day,int herdSize,boolean oldAnimal,boolean pregnant){
        return policy.cullOld()&&policy.lastCullDay()!=day
            &&herdSize>policy.minHerd() &&oldAnimal&&!pregnant;
    }
    static KingdomRanchPolicyData load(CompoundTag root){
        KingdomRanchPolicyData data=new KingdomRanchPolicyData();
        for(Tag t:root.getList("Policies",Tag.TAG_COMPOUND)){
            CompoundTag row=(CompoundTag)t;
            if(!row.hasUUID("Pen"))continue;
            data.policies.put(row.getUUID("Pen"),new Policy(row.getBoolean("CullOld"),
                Math.max(4,Math.min(64,row.getInt("MinHerd"))),row.getLong("LastCull")));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag){
        ListTag list=new ListTag();
        for(var entry:policies.entrySet()){
            var row=new CompoundTag();row.putUUID("Pen",entry.getKey());
            row.putBoolean("CullOld",entry.getValue().cullOld());
            row.putInt("MinHerd",entry.getValue().minHerd());
            row.putLong("LastCull",entry.getValue().lastCullDay());
            list.add(row);
        }
        tag.put("Policies",list);return tag;
    }
}
