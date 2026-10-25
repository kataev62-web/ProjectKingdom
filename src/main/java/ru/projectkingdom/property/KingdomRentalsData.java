package ru.projectkingdom.property;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import ru.projectkingdom.npc.KingdomCivicsData;

/**
 * Stage 6: rentable properties with independent 3D rooms.
 * This class records lawful occupation, not breaking-and-entering or crime.
 * No rent is minted, debited, collected or transferred here; the physical
 * bank settlement commits a lease only after real currency reaches a vault.
 */
public final class KingdomRentalsData extends SavedData {
    private static final String STORAGE = "kingdom_rentals_v1";
    private static final String SELECTED = "KingdomSelectedRoom";
    public static final int MAX_BUILDINGS_PER_OWNER=24;
    public static final int MAX_ROOMS_PER_BUILDING=32;
    public static final long DAY=24L*60*60*1000;
    public enum Purpose { HOME, WORKSHOP, SHOP, STORAGE }
    public enum Access { OUTSIDE, ALLOWED, DENIED }

    /** Inclusive cuboid, max 64x48x64, never a whole-world region. */
    public record Bounds(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
        public Bounds {
            if(minX>maxX||minY>maxY||minZ>maxZ
                    ||(long)maxX-minX>=64||(long)maxY-minY>=48||(long)maxZ-minZ>=64
                    ||(long)(maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1)>32768)
                throw new IllegalArgumentException("Invalid property dimensions");
        }
        public static Bounds between(BlockPos a,BlockPos b) {
            return new Bounds(Math.min(a.getX(),b.getX()),
                    Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()),
                    Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),
                    Math.max(a.getZ(),b.getZ()));
        }
        public boolean contains(BlockPos p){
            return p.getX()>=minX&&p.getX()<=maxX&&p.getY()>=minY&&p.getY()<=maxY
                    &&p.getZ()>=minZ&&p.getZ()<=maxZ;
        }
        public boolean contains(Bounds b){
            return b.minX>=minX&&b.maxX<=maxX&&b.minY>=minY&&b.maxY<=maxY
                    &&b.minZ>=minZ&&b.maxZ<=maxZ;
        }
        public boolean overlaps(Bounds b){
            return minX<=b.maxX&&maxX>=b.minX&&minY<=b.maxY&&maxY>=b.minY
                    &&minZ<=b.maxZ&&maxZ>=b.minZ;
        }
        /** The floor, ceiling and boundary walls remain structural. */
        public boolean structural(BlockPos p){
            return p.getX()==minX||p.getX()==maxX||p.getY()==minY||p.getY()==maxY
                    ||p.getZ()==minZ||p.getZ()==maxZ;
        }
    }
    public record Building(UUID id,String dimension,UUID owner,String name,Bounds bounds) {}
    public record Room(UUID id,UUID buildingId,String name,Purpose purpose,Bounds bounds,
                       long priceCopper,int days,boolean listed,UUID tenant,long expiresAt) {
        public boolean occupied(long now) {return tenant!=null&&now<expiresAt;}
        public boolean reserved(){return tenant!=null;} // expired terms require explicit turnover
    }

    private final Map<UUID,Building> buildings = new LinkedHashMap<>();
    private final Map<UUID,Room> rooms = new LinkedHashMap<>();
    /** The hospitality enterprise that physically operates each building. */
    private final Map<UUID,UUID> receptions = new LinkedHashMap<>();

    public static KingdomRentalsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                KingdomRentalsData::load,KingdomRentalsData::new,STORAGE);
    }
    public Collection<Building> buildings(){return List.copyOf(buildings.values());}
    public Collection<Room> rooms(){return List.copyOf(rooms.values());}
    public Building building(UUID id){return buildings.get(id);}
    public Room room(UUID id){return rooms.get(id);}
    public Building atBuilding(String dimension,BlockPos pos){
        for(var b:buildings.values())
            if(b.dimension().equals(dimension)&&b.bounds().contains(pos))return b;
        return null;
    }
    public Room atRoom(String dimension,BlockPos pos){
        for(var b:buildings.values()){
            if(!b.dimension().equals(dimension)||!b.bounds().contains(pos))continue;
            for(var room:rooms.values())
                if(room.buildingId().equals(b.id())&&room.bounds().contains(pos))return room;
        }
        return null;
    }

    /** A hotel or tavern core inside the titled building becomes its reception. */
    public boolean bindReception(KingdomCivicsData deeds, UUID owner, UUID enterprise,
                                 String dimension, BlockPos corePos) {
        if(enterprise==null || corePos==null || owner==null)return false;
        Building b=atBuilding(dimension,corePos);
        // Public reception must stand in common space, never inside a tenant's
        // private room, including a room that is vacant when first bound.
        if(atRoom(dimension,corePos)!=null || !validOwner(deeds,owner,b))return false;
        UUID prior=receptions.get(b.id());
        if(prior!=null)return prior.equals(enterprise);
        if(receptions.containsValue(enterprise))return false;
        receptions.put(b.id(),enterprise);setDirty();return true;
    }
    public Building receptionBuilding(UUID enterprise) {
        if(enterprise==null)return null;
        for(var e:receptions.entrySet())if(enterprise.equals(e.getValue()))
            return buildings.get(e.getKey());
        return null;
    }
    public UUID receptionForBuilding(UUID buildingId) {
        return receptions.get(buildingId);
    }
    public UUID receptionForRoom(UUID roomId) {
        Room r=rooms.get(roomId);
        return r==null?null:receptions.get(r.buildingId());
    }
    public List<Room> roomsAtReception(UUID enterprise) {
        Building b=receptionBuilding(enterprise);
        if(b==null)return List.of();
        List<Room> result=new ArrayList<>();
        for(Room room:rooms.values())if(room.buildingId().equals(b.id()))
            result.add(room);
        return List.copyOf(result);
    }
    /** Company closure must not invalidate a guest's prepaid stay. */
    public boolean receptionHasObligations(UUID enterprise) {
        for(Room room:roomsAtReception(enterprise))
            if(room.reserved()||room.listed())return true;
        return false;
    }
    /** Only after the enterprise closure checks pass; keep all rooms intact. */
    public boolean releaseVacantReception(UUID enterprise) {
        if(enterprise==null||receptionHasObligations(enterprise))return false;
        boolean removed=receptions.entrySet().removeIf(e->enterprise.equals(e.getValue()));
        if(removed)setDirty();
        return removed;
    }

    /** Every chunk under the building must have an unencumbered deed title. */
    public static boolean ownsFullFootprint(KingdomCivicsData deeds,UUID actor,
                                             String dimension,Bounds bounds) {
        if(deeds==null||actor==null||dimension==null)return false;
        var plots=deeds.mapPlots(dimension);
        for(int x=bounds.minX()>>4;x<=bounds.maxX()>>4;x++)
            for(int z=bounds.minZ()>>4;z<=bounds.maxZ()>>4;z++){
                if(!actor.equals(deeds.plotOwner(dimension,x,z)))return false;
                boolean found=false;
                for(var plot:plots)if(plot.x()==x&&plot.z()==z) {
                    if(plot.tenant()!=null) return false;
                    found=true;break;
                }
                if(!found||deeds.leaseContract(dimension,x,z)!=null)return false;
            }
        return true;
    }

    public Building registerBuilding(KingdomCivicsData deeds,UUID actor,String dimension,
                                     String name,Bounds area){
        if(actor==null||dimension==null||dimension.length()>128||name==null
                ||name.isBlank()||name.length()>48||area==null
                ||!ownsFullFootprint(deeds,actor,dimension,area))return null;
        int count=0;
        for(var b:buildings.values()) {
            if(b.owner().equals(actor))count++;
            if(b.dimension().equals(dimension)&&b.bounds().overlaps(area))return null;
        }
        if(count>=MAX_BUILDINGS_PER_OWNER)return null;
        Building b=new Building(UUID.randomUUID(),dimension,actor,name,area);
        buildings.put(b.id(),b);setDirty();return b;
    }

    private boolean validOwner(KingdomCivicsData deeds,UUID actor,Building b){
        return b!=null&&actor!=null&&b.owner().equals(actor)
                &&ownsFullFootprint(deeds,actor,b.dimension(),b.bounds());
    }

    public Room createRoom(KingdomCivicsData deeds,UUID actor,UUID buildingId,
                           String name,Purpose purpose,Bounds area){
        var b=buildings.get(buildingId);
        if(!validOwner(deeds,actor,b)||purpose==null||area==null
                ||name==null||name.isBlank()||name.length()>48
                ||!b.bounds().contains(area))return null;
        int count=0;
        for(var room:rooms.values()) {
            if(!room.buildingId().equals(buildingId))continue;
            count++;
            if(room.bounds().overlaps(area))return null;
        }
        if(count>=MAX_ROOMS_PER_BUILDING)return null;
        var room=new Room(UUID.randomUUID(),buildingId,name,purpose,area,
                20,7,false,null,0);
        rooms.put(room.id(),room);setDirty();return room;
    }

    public boolean configure(KingdomCivicsData deeds,UUID actor,UUID roomId,
                             long priceCopper,int days,boolean listed){
        var old=rooms.get(roomId);
        if(old==null||old.reserved()||priceCopper<1||priceCopper>1_000_000
                ||(days!=1&&days!=7&&days!=30)
                ||!validOwner(deeds,actor,buildings.get(old.buildingId())))return false;
        rooms.put(roomId,new Room(old.id(),old.buildingId(),old.name(),old.purpose(),
                old.bounds(),priceCopper,days,listed,null,0));
        setDirty();return true;
    }

    public boolean canRent(KingdomCivicsData deeds,UUID tenant,UUID roomId,long now){
        Room room=rooms.get(roomId);
        Building b=room==null?null:buildings.get(room.buildingId());
        return tenant!=null&&room!=null&&room.listed()&&!room.reserved()
                &&b!=null&&now>=0&&now<=Long.MAX_VALUE-room.days()*DAY
                &&!tenant.equals(b.owner())&&validOwner(deeds,b.owner(),b);
    }
    /** Called ONLY after physical payment into the bound hotel/tavern cashbox. */
    public boolean rentPaid(KingdomCivicsData deeds,UUID tenant,UUID roomId,long now){
        if(!canRent(deeds,tenant,roomId,now))return false;
        var r=rooms.get(roomId);
        rooms.put(roomId,new Room(r.id(),r.buildingId(),r.name(),r.purpose(),
                r.bounds(),r.priceCopper(),r.days(),false,tenant,
                now+r.days()*DAY));
        setDirty();return true;
    }

    public boolean canRenew(KingdomCivicsData deeds,UUID tenant,UUID id,long now){
        var r=rooms.get(id);
        if(r==null||tenant==null||!tenant.equals(r.tenant())||now>=r.expiresAt()
                ||r.expiresAt()>Long.MAX_VALUE-r.days()*DAY)return false;
        var b=buildings.get(r.buildingId());
        return validOwner(deeds,b.owner(),b);
    }
    /** Only after an additional real, physical rental payment. */
    public boolean renewPaid(KingdomCivicsData deeds,UUID tenant,UUID id,long now){
        if(!canRenew(deeds,tenant,id,now))return false;
        var r=rooms.get(id);
        rooms.put(id,new Room(r.id(),r.buildingId(),r.name(),r.purpose(),
                r.bounds(),r.priceCopper(),r.days(),false,tenant,
                r.expiresAt()+r.days()*DAY));
        setDirty();return true;
    }

    /** Tenant surrenders; no automatic debt, refunds or confiscation of possessions. */
    public boolean surrender(UUID actor,UUID roomId) {
        var r=rooms.get(roomId);
        if(actor==null||r==null||!actor.equals(r.tenant()))return false;
        rooms.put(roomId,new Room(r.id(),r.buildingId(),r.name(),r.purpose(),
                r.bounds(),r.priceCopper(),r.days(),false,null,0));
        setDirty();return true;
    }
    /** Offline expiration only removes access; it never mints a rent debt. */
    public boolean releaseExpired(KingdomCivicsData deeds,UUID actor,UUID roomId,long now){
        var r=rooms.get(roomId);
        if(r==null||r.tenant()==null||now<r.expiresAt()
                ||!validOwner(deeds,actor,buildings.get(r.buildingId())))return false;
        rooms.put(roomId,new Room(r.id(),r.buildingId(),r.name(),r.purpose(),
                r.bounds(),r.priceCopper(),r.days(),false,null,0));
        setDirty();return true;
    }
    public void select(ServerPlayer player,UUID roomId){
        if(roomId!=null&&rooms.containsKey(roomId))
            player.getPersistentData().putUUID(SELECTED,roomId);
    }
    public Room selected(ServerPlayer player){
        var nbt=player.getPersistentData();
        return nbt.hasUUID(SELECTED)?rooms.get(nbt.getUUID(SELECTED)):null;
    }

    /** Shared passages are reachable by occupants, but storage stays private. */
    public boolean residentOfBuilding(UUID actor,String dimension,BlockPos position,long now){
        if(actor==null)return false;
        var b=atBuilding(dimension,position);
        if(b==null)return false;
        for(var r:rooms.values())
            if(r.buildingId().equals(b.id())&&actor.equals(r.tenant())&&r.occupied(now))
                return true;
        return false;
    }

    /**
     * Exact permission only for rental cuboids. OUTSIDE delegates to the
     * existing chunk-claim system. This is *lawful* access only; future
     * stage 9 may add a separate lock-picking/crime path, never ownership.
     */
    public Access roomAccess(UUID actor,String dimension,BlockPos pos,
                             KingdomCivicsData.Action action,long now){
        Room r=atRoom(dimension,pos);
        if(r==null)return Access.OUTSIDE;
        var b=buildings.get(r.buildingId());
        if(b==null||actor==null)return Access.DENIED;
        boolean tenant=r.occupied(now), permitted=tenant?actor.equals(r.tenant())
                :actor.equals(b.owner());
        if(!permitted)return Access.DENIED;
        if((action==KingdomCivicsData.Action.BUILD
                ||action==KingdomCivicsData.Action.BREAK)
                &&tenant&&r.bounds().structural(pos))return Access.DENIED;
        return Access.ALLOWED;
    }

    private static void saveBounds(CompoundTag t,Bounds b){
        t.putInt("X1",b.minX());t.putInt("Y1",b.minY());t.putInt("Z1",b.minZ());
        t.putInt("X2",b.maxX());t.putInt("Y2",b.maxY());t.putInt("Z2",b.maxZ());
    }
    private static Bounds readBounds(CompoundTag t){
        return new Bounds(t.getInt("X1"),t.getInt("Y1"),t.getInt("Z1"),
                t.getInt("X2"),t.getInt("Y2"),t.getInt("Z2"));
    }
    public static KingdomRentalsData load(CompoundTag root){
        KingdomRentalsData db=new KingdomRentalsData();
        var bs=root.getList("Buildings",Tag.TAG_COMPOUND);
        for(int i=0;i<bs.size()&&i<10000;i++){
            CompoundTag t=bs.getCompound(i);
            if(!t.hasUUID("Id")||!t.hasUUID("Owner"))continue;
            try {
                var b=new Building(t.getUUID("Id"),t.getString("Dimension"),
                        t.getUUID("Owner"),t.getString("Name"),readBounds(t));
                if(!b.dimension().isBlank())db.buildings.put(b.id(),b);
            }catch(IllegalArgumentException ignored){}
        }
        var rs=root.getList("Rooms",Tag.TAG_COMPOUND);
        for(int i=0;i<rs.size()&&i<50000;i++){
            CompoundTag t=rs.getCompound(i);
            if(!t.hasUUID("Id")||!t.hasUUID("Building")
                    ||!db.buildings.containsKey(t.getUUID("Building")))continue;
            try {
                int kind=t.getInt("Purpose");
                if(kind<0||kind>=Purpose.values().length)continue;
                var b=db.buildings.get(t.getUUID("Building"));
                var bounds=readBounds(t);
                if(!b.bounds().contains(bounds))continue;
                long price=t.getLong("Price");int days=t.getInt("Days");
                if(price<1||price>1_000_000||(days!=1&&days!=7&&days!=30))continue;
                UUID tenant=t.hasUUID("Tenant")?t.getUUID("Tenant"):null;
                var room=new Room(t.getUUID("Id"),b.id(),t.getString("Name"),
                        Purpose.values()[kind],bounds,price,days,t.getBoolean("Listed"),
                        tenant,tenant==null?0:t.getLong("Expires"));
                boolean clash=db.rooms.values().stream().anyMatch(r->
                        r.buildingId().equals(room.buildingId())&&r.bounds().overlaps(bounds));
                if(!clash)db.rooms.put(room.id(),room);
            }catch(IllegalArgumentException ignored){}
        }
        for(Tag value:root.getList("Receptions",Tag.TAG_COMPOUND)){
            CompoundTag row=(CompoundTag)value;
            if(!row.hasUUID("Building")||!row.hasUUID("Enterprise"))continue;
            UUID building=row.getUUID("Building"),enterprise=row.getUUID("Enterprise");
            if(db.buildings.containsKey(building)&&!db.receptions.containsValue(enterprise))
                db.receptions.put(building,enterprise);
        }
        return db;
    }
    @Override public CompoundTag save(CompoundTag root) {
        var bs=new ListTag();
        for(var b:buildings.values()){
            var t=new CompoundTag();t.putUUID("Id",b.id());t.putString("Dimension",b.dimension());
            t.putUUID("Owner",b.owner());t.putString("Name",b.name());
            saveBounds(t,b.bounds());bs.add(t);
        }
        root.put("Buildings",bs);
        var rs=new ListTag();
        for(var r:rooms.values()){
            var t=new CompoundTag();t.putUUID("Id",r.id());t.putUUID("Building",r.buildingId());
            t.putString("Name",r.name());t.putInt("Purpose",r.purpose().ordinal());
            saveBounds(t,r.bounds());t.putLong("Price",r.priceCopper());t.putInt("Days",r.days());
            t.putBoolean("Listed",r.listed());
            if(r.tenant()!=null){t.putUUID("Tenant",r.tenant());t.putLong("Expires",r.expiresAt());}
            rs.add(t);
        }
        root.put("Rooms",rs);
        var rec=new ListTag();
        for(var entry:receptions.entrySet()){
            var row=new CompoundTag();row.putUUID("Building",entry.getKey());
            row.putUUID("Enterprise",entry.getValue());rec.add(row);
        }
        root.put("Receptions",rec);return root;
    }
}
