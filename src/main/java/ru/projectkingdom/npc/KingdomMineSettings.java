package ru.projectkingdom.npc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** One server-authoritative starting route per mine enterprise, not per NPC. */
public final class KingdomMineSettings extends SavedData {
    private static final String KEY="kingdom_mine_routes_v1";

    public enum Start { FORWARD("Вперёд"), DOWN("Сразу вниз");
        private final String label;
        Start(String label){this.label=label;}
        public String label(){return label;}
    }

    private final Map<UUID,Start> starts=new HashMap<>();
    private final java.util.Set<UUID> started=new java.util.HashSet<>();

    public static KingdomMineSettings get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(
            KingdomMineSettings::load,KingdomMineSettings::new,KEY);
    }

    public Start start(UUID mine){return starts.getOrDefault(mine,Start.FORWARD);}
    public boolean started(UUID mine){return started.contains(mine);}

    /** Locks the route after the first actual mine excavation/placement. */
    public void markStarted(UUID mine){
        if(mine!=null && started.add(mine))setDirty();
    }

    /** Caller must validate owner and enterprise kind; cannot reroute an active mine. */
    public boolean choose(UUID mine,Start next){
        if(mine==null||next==null||started.contains(mine))return false;
        if(start(mine)!=next){starts.put(mine,next);setDirty();}
        return true;
    }

    private static KingdomMineSettings load(CompoundTag root){
        var data=new KingdomMineSettings();
        ListTag entries=root.getList("Mines",Tag.TAG_COMPOUND);
        for(int i=0;i<entries.size();i++){
            CompoundTag t=entries.getCompound(i);
            if(!t.hasUUID("Id"))continue;
            UUID id=t.getUUID("Id");
            if("DOWN".equals(t.getString("Start")))data.starts.put(id,Start.DOWN);
            if(t.getBoolean("Started"))data.started.add(id);
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag root){
        ListTag entries=new ListTag();
        java.util.Set<UUID> ids=new java.util.HashSet<>(starts.keySet());
        ids.addAll(started);
        for(UUID id:ids){
            CompoundTag tag=new CompoundTag();
            tag.putUUID("Id",id);
            tag.putString("Start",start(id).name());
            tag.putBoolean("Started",started.contains(id));
            entries.add(tag);
        }
        root.put("Mines",entries);
        return root;
    }
}
