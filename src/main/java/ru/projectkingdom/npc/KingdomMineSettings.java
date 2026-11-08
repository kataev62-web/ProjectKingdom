package ru.projectkingdom.npc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
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

    public record Shaft(int minX,int maxX,int minZ,int maxZ,int surfaceY,String dimension) {
        public int width(){return maxX-minX+1;}
        public int length(){return maxZ-minZ+1;}
        public boolean contains(BlockPos pos){
            return pos.getX()>=minX&&pos.getX()<=maxX
                &&pos.getZ()>=minZ&&pos.getZ()<=maxZ;
        }
    }

    private final Map<UUID,Shaft> shafts=new HashMap<>();
    public Shaft shaft(UUID mine){return shafts.get(mine);}
    /** Min 5x5 for a walkable access lane; max 12x12 for bounded tick cost. */
    public boolean setShaft(UUID mine,Shaft area) {
        if(mine==null||area==null||started(mine)
                ||area.width()<5||area.length()<5
                ||area.width()>12||area.length()>12)return false;
        shafts.put(mine,area);
        // Selecting a central shaft is the explicit DOWN mode choice.
        starts.put(mine,Start.DOWN);
        explicitDirections.remove(mine);
        setDirty();return true;
    }

    private final Map<UUID,Start> starts=new HashMap<>();
    /** Distinguish an explicitly selected FORWARD route from the old default. */
    private final java.util.Set<UUID> explicitDirections=new java.util.HashSet<>();
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
        // Record explicit route choices even when switching route; a short-
        // circuited || used to skip the explicit marker on actual changes.
        boolean changed=start(mine)!=next;
        boolean explicit=explicitDirections.add(mine);
        if(changed || explicit){starts.put(mine,next);setDirty();}
        return true;
    }

    private static KingdomMineSettings load(CompoundTag root){
        var data=new KingdomMineSettings();
        ListTag entries=root.getList("Mines",Tag.TAG_COMPOUND);
        for(int i=0;i<entries.size();i++){
            CompoundTag t=entries.getCompound(i);
            if(!t.hasUUID("Id"))continue;
            UUID id=t.getUUID("Id");
            String route=t.getString("Start");
            if("DOWN".equals(route))data.starts.put(id,Start.DOWN);
            if("FORWARD".equals(route))data.starts.put(id,Start.FORWARD);
            if(t.getBoolean("RouteExplicit"))data.explicitDirections.add(id);
            if(t.getBoolean("Started"))data.started.add(id);
            if(t.contains("Shaft",Tag.TAG_COMPOUND)){
                CompoundTag s=t.getCompound("Shaft");
                var area=new Shaft(s.getInt("MinX"),s.getInt("MaxX"),
                    s.getInt("MinZ"),s.getInt("MaxZ"),
                    s.getInt("SurfaceY"),s.getString("Dimension"));
                if(area.width()>=5&&area.length()>=5&&area.width()<=12&&area.length()<=12)
                    data.shafts.put(id,area);
            }
        }
        // Old saved worlds recorded FORWARD even after a central shaft was
        // selected because the menu forgot to switch direction. Migrate only
        // mines that never dug a block. Explicit new-version choices survive.
        for(UUID id:data.shafts.keySet())
            if(!data.started.contains(id)&&!data.explicitDirections.contains(id))
                data.starts.put(id,Start.DOWN);
        return data;
    }

    @Override public CompoundTag save(CompoundTag root){
        ListTag entries=new ListTag();
        java.util.Set<UUID> ids=new java.util.HashSet<>(starts.keySet());
        ids.addAll(started);
        ids.addAll(shafts.keySet());
        for(UUID id:ids){
            CompoundTag tag=new CompoundTag();
            tag.putUUID("Id",id);
            tag.putString("Start",start(id).name());
            tag.putBoolean("RouteExplicit",explicitDirections.contains(id));
            tag.putBoolean("Started",started.contains(id));
            Shaft area=shafts.get(id);
            if(area!=null){
                CompoundTag s=new CompoundTag();
                s.putInt("MinX",area.minX());s.putInt("MaxX",area.maxX());
                s.putInt("MinZ",area.minZ());s.putInt("MaxZ",area.maxZ());
                s.putInt("SurfaceY",area.surfaceY());
                s.putString("Dimension",area.dimension());
                tag.put("Shaft",s);
            }
            entries.add(tag);
        }
        root.put("Mines",entries);
        return root;
    }
}
