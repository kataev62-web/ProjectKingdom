package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import ru.projectkingdom.enterprise.EnterpriseKind;

/**
 * Claimed, bounded animal pens. Pen registration alone never grants land ownership.
 * All physical animal interaction still rechecks the claimed territory and employer.
 */
public final class KingdomRanchPenData extends SavedData {
    public static final int MAX_PENS=4,MAX_SIDE=16;
    private static final String KEY="kingdom_livestock_pens_v1";
    public record Pen(UUID id,UUID farm,String dimension,int minX,int maxX,int y,int minZ,int maxZ,boolean enabled){
        public boolean contains(Entity animal){
            BlockPos p=animal.blockPosition();
            return animal.level().dimension().location().toString().equals(dimension)
                && p.getX()>=minX&&p.getX()<=maxX&&p.getZ()>=minZ&&p.getZ()<=maxZ
                && Math.abs(p.getY()-y)<=3;
        }
        public BlockPos center(){return new BlockPos((minX+maxX)/2,y+1,(minZ+maxZ)/2);}
    }
    private final Map<UUID,Pen> pens=new LinkedHashMap<>();
    public static KingdomRanchPenData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(KingdomRanchPenData::load,KingdomRanchPenData::new,KEY);
    }
    public List<Pen> of(UUID farm){return pens.values().stream().filter(p->p.farm().equals(farm)).toList();}
    public boolean remove(ServerPlayer player,UUID farm,UUID pen){
        if(!KingdomFarmFieldData.allowed(player,farm))return false;
        Pen found=pens.get(pen);
        if(found==null||!found.farm().equals(farm))return false;
        pens.remove(pen);setDirty();return true;
    }
    public boolean toggle(ServerPlayer player,UUID farm,UUID pen){
        if(!KingdomFarmFieldData.allowed(player,farm))return false;
        Pen p=pens.get(pen);
        if(p==null||!p.farm().equals(farm))return false;
        pens.put(pen,new Pen(p.id(),p.farm(),p.dimension(),p.minX(),p.maxX(),p.y(),
            p.minZ(),p.maxZ(),!p.enabled()));setDirty();return true;
    }
    /** Modern municipal deeds have priority over legacy NPC territory claims.
     * Only the current owner, lawful tenant or delegated worker may configure a pen.
     */
    public static boolean canRegisterAt(ServerLevel level,BlockPos pos,UUID actor){
        if(level==null||pos==null||actor==null||!level.hasChunkAt(pos))return false;
        var civics=KingdomCivicsData.get(level.getServer());
        if(civics.plot(level,pos)!=null)
            return civics.allowed(actor,level.dimension().location().toString(),
                    pos.getX(),pos.getZ(),KingdomCivicsData.Action.BUILD);
        return KingdomTerritories.get(level.getServer()).canUse(level,pos,actor);
    }
    public boolean register(ServerPlayer player,UUID farm,BlockPos one,BlockPos two){
        if(!KingdomFarmFieldData.allowed(player,farm)||one.getY()!=two.getY()
            ||of(farm).size()>=MAX_PENS)return false;
        ServerLevel level=player.serverLevel();
        var core=KingdomEnterpriseData.get(player.getServer()).core(farm);
        if(core==null||!core.dimension().equals(level.dimension().location().toString())
            ||core.kind()!=EnterpriseKind.FARM)return false;
        int x1=Math.min(one.getX(),two.getX()),x2=Math.max(one.getX(),two.getX());
        int z1=Math.min(one.getZ(),two.getZ()),z2=Math.max(one.getZ(),two.getZ());
        if(x2-x1+1>MAX_SIDE||z2-z1+1>MAX_SIDE)return false;
        String dimension=level.dimension().location().toString();
        for(Pen p:pens.values())if(p.dimension().equals(dimension)&&p.y()==one.getY()
            &&x1<=p.maxX()&&x2>=p.minX()&&z1<=p.maxZ()&&z2>=p.minZ())return false;
        for(int x=x1;x<=x2;x++)for(int z=z1;z<=z2;z++){
            BlockPos soil=new BlockPos(x,one.getY(),z);
            if(!canRegisterAt(level,soil,player.getUUID()))return false;
        }
        Pen p=new Pen(UUID.randomUUID(),farm,dimension,x1,x2,one.getY(),z1,z2,true);
        pens.put(p.id(),p);setDirty();return true;
    }
    /** Trusted settlement AI only: register a previously BUILT pen on existing NPC-owned land. */
    public boolean registerForNpcOwner(ServerLevel level,UUID npcOwner,UUID farm,BlockPos one,BlockPos two){
        if(npcOwner==null||farm==null||one.getY()!=two.getY()||of(farm).size()>=MAX_PENS)
            return false;
        boolean validOwner=KingdomNpcData.get(level.getServer()).all().stream()
            .anyMatch(w->npcOwner.equals(w.entityId)
                && farm.equals(KingdomNpcFarmOwner.ownedFarm(level.getServer(),w)));
        var core=KingdomEnterpriseData.get(level.getServer()).core(farm);
        if(!validOwner||core==null||!core.active()||core.kind()!=EnterpriseKind.FARM
            ||!core.dimension().equals(level.dimension().location().toString()))return false;
        int x1=Math.min(one.getX(),two.getX()),x2=Math.max(one.getX(),two.getX());
        int z1=Math.min(one.getZ(),two.getZ()),z2=Math.max(one.getZ(),two.getZ());
        if(x2-x1+1>MAX_SIDE||z2-z1+1>MAX_SIDE)return false;
        String dimension=level.dimension().location().toString();
        for(Pen p:pens.values())if(p.dimension().equals(dimension)&&p.y()==one.getY()
            &&x1<=p.maxX()&&x2>=p.minX()&&z1<=p.maxZ()&&z2>=p.minZ())return false;
        for(int x=x1;x<=x2;x++)for(int z=z1;z<=z2;z++){
            BlockPos soil=new BlockPos(x,one.getY(),z);
            if(!canRegisterAt(level,soil,npcOwner))return false;
        }
        Pen pen=new Pen(UUID.randomUUID(),farm,dimension,x1,x2,one.getY(),z1,z2,true);
        pens.put(pen.id(),pen);setDirty();return true;
    }

    static KingdomRanchPenData load(CompoundTag nbt){
        KingdomRanchPenData out=new KingdomRanchPenData();
        for(Tag obj:nbt.getList("Pens",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)obj;
            if(!t.hasUUID("Id")||!t.hasUUID("Farm"))continue;
            int x1=t.getInt("X1"),x2=t.getInt("X2"),z1=t.getInt("Z1"),z2=t.getInt("Z2");
            if(x1>x2||z1>z2||x2-x1+1>MAX_SIDE||z2-z1+1>MAX_SIDE)continue;
            var p=new Pen(t.getUUID("Id"),t.getUUID("Farm"),t.getString("Dimension"),
                x1,x2,t.getInt("Y"),z1,z2,t.getBoolean("Enabled"));
            if(!p.dimension().isBlank()&&out.of(p.farm()).size()<MAX_PENS)out.pens.put(p.id(),p);
        }
        return out;
    }
    @Override public CompoundTag save(CompoundTag t){
        ListTag list=new ListTag();
        for(Pen p:pens.values()){
            CompoundTag row=new CompoundTag();
            row.putUUID("Id",p.id());row.putUUID("Farm",p.farm());
            row.putString("Dimension",p.dimension());row.putInt("X1",p.minX());
            row.putInt("X2",p.maxX());row.putInt("Y",p.y());
            row.putInt("Z1",p.minZ());row.putInt("Z2",p.maxZ());
            row.putBoolean("Enabled",p.enabled());list.add(row);
        }
        t.put("Pens",list);return t;
    }
}
