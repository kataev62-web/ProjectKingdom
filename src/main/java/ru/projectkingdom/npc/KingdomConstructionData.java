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
    /** Up to eight explicitly invited player builders per project. */
    private final HashMap<UUID,LinkedHashSet<UUID>> assistants = new HashMap<>();
    private static final int MAX_ASSISTANTS = 8;

    public Set<UUID> assistants(UUID siteId) {
        return Collections.unmodifiableSet(assistants.getOrDefault(siteId, new LinkedHashSet<>()));
    }

    /** Only the owner can delegate construction. Invitation never transfers ownership. */
    public boolean mayAssist(UUID siteId, UUID player) {
        Project site = projects.get(siteId);
        return site != null && player != null && site.status() != Status.CANCELLED
            && (site.owner().equals(player) || assistants(siteId).contains(player));
    }

    public boolean toggleAssistant(UUID siteId, UUID actor, UUID candidate) {
        Project site = projects.get(siteId);
        if (site == null || actor == null || candidate == null
                || !site.owner().equals(actor) || actor.equals(candidate)
                || site.status() == Status.CANCELLED) return false;
        LinkedHashSet<UUID> invited = assistants.computeIfAbsent(siteId, ignored -> new LinkedHashSet<>());
        if (!invited.remove(candidate)) {
            if (invited.size() >= MAX_ASSISTANTS) return false;
            invited.add(candidate);
        }
        if (invited.isEmpty()) assistants.remove(siteId);
        setDirty();
        return true;
    }

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
    public boolean overlaps(MinecraftServer server, String dimension, int minX, int minY, int minZ,
                            int maxX, int maxY, int maxZ) {
        for (Project existing : projects.values()) {
            if (existing.status() == Status.CANCELLED || !existing.dimension().equals(dimension)) continue;
            var design = KingdomConstructionPlans.resolve(server, existing.design());
            // If a frozen schematic is missing, protect its registered origin;
            // never silently let another project overwrite it.
            if (design == null)
                throw new IllegalStateException("Missing construction plan for project " + existing.id());
            var blocks = design.blocks();
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
                ListTag invited = r.getList("Assistants", Tag.TAG_INT_ARRAY);
                LinkedHashSet<UUID> members = new LinkedHashSet<>();
                for (int j = 0; j < invited.size() && members.size() < MAX_ASSISTANTS; j++) {
                    try {
                        UUID id = net.minecraft.nbt.NbtUtils.loadUUID(invited.get(j));
                        if (!id.equals(p.owner())) members.add(id);
                    } catch (IllegalArgumentException ignored) { }
                }
                if (!members.isEmpty()) data.assistants.put(p.id(), members);
            } catch(IllegalArgumentException ignored) { }
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag out) {
        ListTag rows=new ListTag();
        for(Project p:projects.values()) {
            CompoundTag r=new CompoundTag();r.putUUID("Id",p.id());r.putUUID("Owner",p.owner());r.putString("Dimension",p.dimension());r.putString("Design",p.design());r.putInt("X",p.x());r.putInt("Y",p.y());r.putInt("Z",p.z());r.putString("Status",p.status().name());r.putInt("Progress",progress(p.id()));
            ListTag invited = new ListTag();
            for (UUID assistant : assistants(p.id()))
                invited.add(net.minecraft.nbt.NbtUtils.createUUID(assistant));
            r.put("Assistants", invited);
            rows.add(r);
        }
        out.put("Projects",rows);return out;
    }
}
