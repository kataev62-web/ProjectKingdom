package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/** Explicit livestock policy. Culling is DISABLED by default. */
public final class KingdomRanchPolicyData extends SavedData {
    private static final String KEY="kingdom_ranch_policy_v1";
    public record Policy(boolean cullOld,int minHerd,long lastCullDay,boolean collectEggs,boolean autoFeed){
        public Policy(boolean cullOld,int minHerd,long lastCullDay,boolean collectEggs){
            this(cullOld,minHerd,lastCullDay,collectEggs,false);
        }
        /** Backward-compatible with pre-egg-consent policy callers and saves. */
        public Policy(boolean cullOld,int minHerd,long lastCullDay){
            this(cullOld,minHerd,lastCullDay,false);
        }
    }
    private final Map<UUID,Policy> policies=new LinkedHashMap<>();
    public static KingdomRanchPolicyData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(
            KingdomRanchPolicyData::load,KingdomRanchPolicyData::new,KEY);
    }
    public Policy policy(UUID pen){return policies.getOrDefault(pen,new Policy(false,6,-1,false,false));}
    public boolean toggleCull(ServerPlayer player,UUID farm,UUID pen){
        if(!KingdomFarmFieldData.allowed(player,farm)
            ||KingdomRanchPenData.get(player.getServer()).of(farm).stream()
                .noneMatch(p->p.id().equals(pen)))return false;
        Policy old=policy(pen);
        policies.put(pen,new Policy(!old.cullOld(),old.minHerd(),old.lastCullDay(),old.collectEggs(),old.autoFeed()));
        setDirty();return true;
    }
    /** Egg collection is opt-in: pre-existing items in a nest have no ownership provenance. */
    public boolean toggleEggCollection(ServerPlayer player,UUID farm,UUID pen){
        if(!KingdomFarmFieldData.allowed(player,farm)
            ||KingdomRanchPenData.get(player.getServer()).of(farm).stream()
                .noneMatch(p->p.id().equals(pen)))return false;
        Policy old=policy(pen);
        policies.put(pen,new Policy(old.cullOld(),old.minHerd(),old.lastCullDay(),!old.collectEggs(),old.autoFeed()));
        setDirty();return true;
    }
    /** Feeding is opt-in: TFC only accepts feeding once per game day; player must retain the choice. */
    public boolean toggleAutoFeed(ServerPlayer player,UUID farm,UUID pen){
        if(!KingdomFarmFieldData.allowed(player,farm)
            ||KingdomRanchPenData.get(player.getServer()).of(farm).stream()
                .noneMatch(p->p.id().equals(pen)))return false;
        Policy old=policy(pen);
        policies.put(pen,new Policy(old.cullOld(),old.minHerd(),old.lastCullDay(),
                old.collectEggs(),!old.autoFeed()));
        setDirty();return true;
    }
    /** Only a confirmed, synchronous successful real animal death spends a quota. */
    public void markCull(UUID pen,long day){
        Policy old=policy(pen);
        policies.put(pen,new Policy(old.cullOld(),old.minHerd(),day,old.collectEggs(),old.autoFeed()));
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
                Math.max(4,Math.min(64,row.getInt("MinHerd"))),row.getLong("LastCull"),
                row.getBoolean("CollectEggs"),row.getBoolean("AutoFeed")));
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
            row.putBoolean("CollectEggs",entry.getValue().collectEggs());
            row.putBoolean("AutoFeed",entry.getValue().autoFeed());
            list.add(row);
        }
        tag.put("Policies",list);return tag;
    }
}
