package ru.projectkingdom.npc;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Persistent construction orders: no creative spawning, no automatic placement. */
public final class KingdomConstructionData extends SavedData {
    public enum Status { PLANNED, WAITING_MATERIALS, BUILDING, COMPLETED, CANCELLED }
    public record Project(UUID id, UUID owner, String dimension, String design, int x, int y, int z, Status status) {}
    private final LinkedHashMap<UUID,Project> projects=new LinkedHashMap<>();
    private final HashMap<UUID,Integer> progress=new HashMap<>();
    public static KingdomConstructionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(KingdomConstructionData::load,KingdomConstructionData::new,"kingdom_construction_v1");
    }
    public Collection<Project> all(){return Collections.unmodifiableCollection(projects.values());}
    public Project find(UUID id){return projects.get(id);}
    /** Only the project owner can start or pause a construction order. */
    public boolean order(UUID id,UUID actor,boolean start) {
        Project p=projects.get(id);
        if(p==null || !p.owner().equals(actor) || p.status()==Status.COMPLETED || p.status()==Status.CANCELLED)return false;
        Status status=start?Status.WAITING_MATERIALS:Status.PLANNED;
        projects.put(id,new Project(p.id(),p.owner(),p.dimension(),p.design(),p.x(),p.y(),p.z(),status));
        setDirty();return true;
    }
    /** Builder pauses without changing ownership or losing block progress. */
    public void waiting(UUID id) {
        Project p=projects.get(id);
        if(p!=null && p.status()==Status.BUILDING) {
            projects.put(id,new Project(p.id(),p.owner(),p.dimension(),p.design(),p.x(),p.y(),p.z(),Status.WAITING_MATERIALS));setDirty();
        }
    }
    public int progress(UUID id){return progress.getOrDefault(id,0);}
    public void advance(UUID id,int count,int total) {
        Project p=projects.get(id);if(p==null||count<0||total<1)return;
        int next=Math.min(total,progress(id)+count);
        progress.put(id,next);
        Status status=next==total?Status.COMPLETED:Status.BUILDING;
        projects.put(id,new Project(p.id(),p.owner(),p.dimension(),p.design(),p.x(),p.y(),p.z(),status));setDirty();
    }
    public Project atMarker(String dimension,int x,int y,int z) {
        for(Project p:projects.values())if(p.dimension().equals(dimension)&&p.x()==x+2&&p.y()==y&&p.z()==z+2)return p;
        return null;
    }
    /** Reject overlapping building footprints, including paused or completed projects. */
    public boolean overlaps(String dimension, int minX, int minY, int minZ,
                            int maxX, int maxY, int maxZ) {
        for (Project existing : projects.values()) {
            if (existing.status() == Status.CANCELLED || !existing.dimension().equals(dimension)) continue;
            var type = KingdomBuildingTypes.type(existing.design());
            if (type == null) continue;
            var blocks = KingdomBuildingTypes.design(type).blocks();
            if (blocks.isEmpty()) continue;
            int loX=Integer.MAX_VALUE, loY=Integer.MAX_VALUE, loZ=Integer.MAX_VALUE;
            int hiX=Integer.MIN_VALUE, hiY=Integer.MIN_VALUE, hiZ=Integer.MIN_VALUE;
            for (var cell : blocks) {
                loX=Math.min(loX,existing.x()+cell.x()); hiX=Math.max(hiX,existing.x()+cell.x());
                loY=Math.min(loY,existing.y()+cell.y()); hiY=Math.max(hiY,existing.y()+cell.y());
                loZ=Math.min(loZ,existing.z()+cell.z()); hiZ=Math.max(hiZ,existing.z()+cell.z());
            }
            if (minX<=hiX && maxX>=loX && minY<=hiY && maxY>=loY && minZ<=hiZ && maxZ>=loZ) return true;
        }
        return false;
    }

    public Project add(UUID owner,String dimension,String design,int x,int y,int z) {
        Project p=new Project(UUID.randomUUID(),owner,dimension,design,x,y,z,Status.PLANNED);
        projects.put(p.id(),p);setDirty();return p;
    }
    public boolean setStatus(UUID id,UUID actor,Status status) {
        Project p=projects.get(id);
        if(p==null || !p.owner().equals(actor))return false;
        projects.put(id,new Project(p.id(),p.owner(),p.dimension(),p.design(),p.x(),p.y(),p.z(),status));setDirty();return true;
    }
    private static KingdomConstructionData load(CompoundTag tag) {
        KingdomConstructionData data=new KingdomConstructionData();
        ListTag rows=tag.getList("Projects",Tag.TAG_COMPOUND);
        for(int i=0;i<rows.size();i++) {
            CompoundTag r=rows.getCompound(i);
            try {
                Project p=new Project(r.getUUID("Id"),r.getUUID("Owner"),r.getString("Dimension"),r.getString("Design"),r.getInt("X"),r.getInt("Y"),r.getInt("Z"),Status.valueOf(r.getString("Status")));
                data.projects.put(p.id(),p);
                data.progress.put(p.id(),Math.max(0,r.getInt("Progress")));
            } catch(IllegalArgumentException ignored) { }
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag out) {
        ListTag rows=new ListTag();
        for(Project p:projects.values()) {
            CompoundTag r=new CompoundTag();r.putUUID("Id",p.id());r.putUUID("Owner",p.owner());r.putString("Dimension",p.dimension());r.putString("Design",p.design());r.putInt("X",p.x());r.putInt("Y",p.y());r.putInt("Z",p.z());r.putString("Status",p.status().name());r.putInt("Progress",progress(p.id()));rows.add(r);
        }
        out.put("Projects",rows);return out;
    }
}
