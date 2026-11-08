package ru.projectkingdom.npc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    public enum Heading {
        NORTH("Север",Direction.NORTH), EAST("Восток",Direction.EAST),
        SOUTH("Юг",Direction.SOUTH), WEST("Запад",Direction.WEST);
        private final String label;
        private final Direction direction;
        Heading(String label,Direction direction){this.label=label;this.direction=direction;}
        public String label(){return label;}
        public Direction direction(){return direction;}
        public Heading next(){return values()[(ordinal()+1)%values().length];}
    }

    public static final int LEVEL_INTERVAL=5;
    public static final int MAX_LEVELS=4;
    private final Map<UUID,Integer> levelCounts=new HashMap<>();
    private final Map<UUID,Heading> headings=new HashMap<>();
    public int levels(UUID mine){return levelCounts.getOrDefault(mine,3);}
    public Heading heading(UUID mine){return headings.getOrDefault(mine,Heading.NORTH);}
    public boolean chooseLevels(UUID mine,int value){
        if(mine==null||started(mine)||value<1||value>MAX_LEVELS)return false;
        levelCounts.put(mine,value);setDirty();return true;
    }
    public boolean chooseHeading(UUID mine,Heading value){
        if(mine==null||started(mine)||value==null)return false;
        headings.put(mine,value);setDirty();return true;
    }

    private final Map<UUID,Shaft> shafts=new HashMap<>();
    public Shaft shaft(UUID mine){return shafts.get(mine);}
    /** Min 7x7 for a walkable access lane; max 12x12 for bounded tick cost. */
    public boolean setShaft(UUID mine,Shaft area) {
        if(mine==null||area==null||started(mine)
                ||area.width()<7||area.length()<7
                ||area.width()>12||area.length()>12)return false;
        shafts.put(mine,area);setDirty();return true;
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
            int levels=t.contains("Levels",Tag.TAG_INT)?t.getInt("Levels"):3;
            if(levels>=1&&levels<=MAX_LEVELS)data.levelCounts.put(id,levels);
            try{data.headings.put(id,Heading.valueOf(t.getString("Heading")));}
            catch(IllegalArgumentException ignored){data.headings.put(id,Heading.NORTH);}
            if(t.getBoolean("Started"))data.started.add(id);
            if(t.contains("Shaft",Tag.TAG_COMPOUND)){
                CompoundTag s=t.getCompound("Shaft");
                var area=new Shaft(s.getInt("MinX"),s.getInt("MaxX"),
                    s.getInt("MinZ"),s.getInt("MaxZ"),
                    s.getInt("SurfaceY"),s.getString("Dimension"));
                if(area.width()>=7&&area.length()>=7&&area.width()<=12&&area.length()<=12)
                    data.shafts.put(id,area);
            }
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag root){
        ListTag entries=new ListTag();
        java.util.Set<UUID> ids=new java.util.HashSet<>(starts.keySet());
        ids.addAll(started);
        ids.addAll(shafts.keySet());
        ids.addAll(levelCounts.keySet());
        ids.addAll(headings.keySet());
        for(UUID id:ids){
            CompoundTag tag=new CompoundTag();
            tag.putUUID("Id",id);
            tag.putString("Start",start(id).name());
            tag.putInt("Levels",levels(id));
            tag.putString("Heading",heading(id).name());
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
