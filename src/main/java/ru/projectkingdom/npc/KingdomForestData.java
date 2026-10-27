package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import ru.projectkingdom.enterprise.EnterpriseKind;

/** Real forestry plans, not claims. Every operation rechecks actual land rights. */
public final class KingdomForestData extends SavedData {
    private static final String KEY="kingdom_forests_v1";
    public static final int MAX_AREAS=8,MAX_SIDE=16;
    public record Area(UUID id,UUID lumber,String dimension,int minX,int maxX,int y,
                       int minZ,int maxZ,String sapling,boolean enabled,
                       int minLogs,int maxLogs,boolean replant) {
        public Area{
            if(id==null||lumber==null||dimension==null||dimension.isBlank()
               ||minX>maxX||minZ>maxZ||maxX-minX+1>MAX_SIDE
               ||maxZ-minZ+1>MAX_SIDE||minLogs<0||maxLogs<minLogs
               ||maxLogs>4096)throw new IllegalArgumentException("forest area");
        }
        public boolean inside(BlockPos pos){
            return pos.getX()>=minX&&pos.getX()<=maxX
                &&pos.getZ()>=minZ&&pos.getZ()<=maxZ;
        }
        public BlockPos center(){return new BlockPos((minX+maxX)/2,y+1,(minZ+maxZ)/2);}
    }
    private final Map<UUID,Area> areas=new LinkedHashMap<>();
    private final Set<UUID> activelyCutting=new HashSet<>();
    /** Persistent min/max hysteresis based on real warehouse stock. */
    public boolean harvestAllowed(UUID areaId,EnterpriseStockPolicy.Thresholds limits,int physical){
        if(!areas.containsKey(areaId)||physical<0)return false;
        boolean old=activelyCutting.contains(areaId);
        boolean next=EnterpriseStockPolicy.nextProduction(limits,physical,old);
        if(next!=old){
            if(next)activelyCutting.add(areaId);
            else activelyCutting.remove(areaId);
            setDirty();
        }
        return next;
    }
    public static KingdomForestData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            KingdomForestData::load,KingdomForestData::new,KEY);
    }
    public List<Area> of(UUID business){
        return areas.values().stream().filter(a->a.lumber().equals(business)).toList();
    }
    public static boolean allowed(ServerPlayer actor,UUID business){
        var firms=KingdomEnterpriseData.get(actor.getServer());
        var core=firms.core(business);
        return core!=null&&core.active()&&core.kind()==EnterpriseKind.LUMBER
            &&firms.kind(business)==EnterpriseKind.LUMBER
            &&firms.mayManage(actor.getUUID(),business)
            &&actor.serverLevel().dimension().location().toString().equals(core.dimension());
    }
    public boolean add(ServerPlayer actor,UUID business,BlockPos first,BlockPos second){
        if(!allowed(actor,business)||first.getY()!=second.getY()
            ||of(business).size()>=MAX_AREAS)return false;
        int x1=Math.min(first.getX(),second.getX()),x2=Math.max(first.getX(),second.getX());
        int z1=Math.min(first.getZ(),second.getZ()),z2=Math.max(first.getZ(),second.getZ());
        if(x2-x1+1>MAX_SIDE||z2-z1+1>MAX_SIDE)return false;
        var level=actor.serverLevel();String dim=level.dimension().location().toString();
        var territory=KingdomTerritories.get(actor.getServer());
        for(Area area:areas.values())if(area.dimension().equals(dim)
            &&x1<=area.maxX()&&x2>=area.minX()&&z1<=area.maxZ()&&z2>=area.minZ())
            return false;
        for(int x=x1;x<=x2;x++)for(int z=z1;z<=z2;z++){
            var pos=new BlockPos(x,first.getY(),z);
            if(!level.hasChunkAt(pos)||!territory.canUse(level,pos,actor.getUUID()))return false;
        }
        Area area=new Area(UUID.randomUUID(),business,dim,x1,x2,first.getY(),
            z1,z2,"",true,32,128,true);
        areas.put(area.id(),area);setDirty();return true;
    }
    private boolean change(ServerPlayer actor,UUID business,UUID id,
                           java.util.function.UnaryOperator<Area> edit){
        if(!allowed(actor,business))return false;
        Area old=areas.get(id);
        if(old==null||!old.lumber().equals(business))return false;
        areas.put(id,edit.apply(old));setDirty();return true;
    }
    private static Area copy(Area old,String sapling,boolean enabled,int min,int max,boolean replant){
        return new Area(old.id(),old.lumber(),old.dimension(),old.minX(),old.maxX(),old.y(),
            old.minZ(),old.maxZ(),sapling,enabled,min,max,replant);
    }
    public boolean setSapling(ServerPlayer actor,UUID business,UUID id,String sapling){
        if(!KingdomTfcForestry.validSapling(sapling))return false;
        return change(actor,business,id,a->copy(a,sapling,a.enabled(),a.minLogs(),a.maxLogs(),a.replant()));
    }
    public boolean toggle(ServerPlayer actor,UUID business,UUID id){
        return change(actor,business,id,a->copy(a,a.sapling(),!a.enabled(),
            a.minLogs(),a.maxLogs(),a.replant()));
    }
    public boolean toggleReplant(ServerPlayer actor,UUID business,UUID id){
        return change(actor,business,id,a->copy(a,a.sapling(),a.enabled(),
            a.minLogs(),a.maxLogs(),!a.replant()));
    }
    public boolean cycleStock(ServerPlayer actor,UUID business,UUID id){
        return change(actor,business,id,a->{
            int next=a.maxLogs()==128?256:a.maxLogs()==256?512:128;
            return copy(a,a.sapling(),a.enabled(),next/4,next,a.replant());
        });
    }
    public boolean delete(ServerPlayer actor,UUID business,UUID id){
        if(!allowed(actor,business))return false;
        Area a=areas.get(id);
        if(a==null||!a.lumber().equals(business))return false;
        areas.remove(id);activelyCutting.remove(id);setDirty();return true;
    }
    static KingdomForestData load(CompoundTag root){
        var data=new KingdomForestData();
        for(Tag t:root.getList("Areas",Tag.TAG_COMPOUND)){
            var row=(CompoundTag)t;
            if(!row.hasUUID("Id")||!row.hasUUID("Business"))continue;
            try{
                var a=new Area(row.getUUID("Id"),row.getUUID("Business"),
                    row.getString("Dimension"),row.getInt("MinX"),row.getInt("MaxX"),
                    row.getInt("Y"),row.getInt("MinZ"),row.getInt("MaxZ"),
                    row.getString("Sapling"),row.getBoolean("Enabled"),
                    row.getInt("Minimum"),row.getInt("Maximum"),row.getBoolean("Replant"));
                if(data.of(a.lumber()).size()<MAX_AREAS)data.areas.put(a.id(),a);
            }catch(IllegalArgumentException ignored){}
        }
        for(Tag row:root.getList("Running",Tag.TAG_COMPOUND)){
            var record=(CompoundTag)row;
            if(record.hasUUID("Area")&&data.areas.containsKey(record.getUUID("Area")))
                data.activelyCutting.add(record.getUUID("Area"));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root){
        var list=new ListTag();
        for(Area a:areas.values()){
            var tag=new CompoundTag();
            tag.putUUID("Id",a.id());tag.putUUID("Business",a.lumber());
            tag.putString("Dimension",a.dimension());
            tag.putInt("MinX",a.minX());tag.putInt("MaxX",a.maxX());tag.putInt("Y",a.y());
            tag.putInt("MinZ",a.minZ());tag.putInt("MaxZ",a.maxZ());
            tag.putString("Sapling",a.sapling());tag.putBoolean("Enabled",a.enabled());
            tag.putInt("Minimum",a.minLogs());tag.putInt("Maximum",a.maxLogs());
            tag.putBoolean("Replant",a.replant());list.add(tag);
        }
        root.put("Areas",list);
        var active=new ListTag();
        for(UUID id:activelyCutting){
            var t=new CompoundTag();t.putUUID("Area",id);active.add(t);
        }
        root.put("Running",active);return root;
    }
}
