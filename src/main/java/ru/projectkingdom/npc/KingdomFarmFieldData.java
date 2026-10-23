package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import ru.projectkingdom.enterprise.EnterpriseKind;

/**
 * Eight bounded, explicit farmland plots per farm. Nothing is planted outside a
 * configured plot. This is not a claim system; independent land checks are mandatory.
 */
public final class KingdomFarmFieldData extends SavedData {
    public static final int MAX_FIELDS=8, MAX_SIDE=16;
    private static final String KEY="kingdom_farm_fields_v1";
    public record Field(UUID id, UUID farm, String dimension, int minX,int maxX,int y,
                        int minZ,int maxZ,String seedId,boolean enabled) {
        public boolean contains(BlockPos crop) {
            return crop.getY()==y+1 && crop.getX()>=minX && crop.getX()<=maxX
                && crop.getZ()>=minZ && crop.getZ()<=maxZ;
        }
        public BlockPos centerCrop() {
            return new BlockPos((minX+maxX)/2,y+1,(minZ+maxZ)/2);
        }
        public int area() { return (maxX-minX+1)*(maxZ-minZ+1); }
    }
    private final Map<UUID,Field> fields=new LinkedHashMap<>();
    public static KingdomFarmFieldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            KingdomFarmFieldData::load,KingdomFarmFieldData::new,KEY);
    }
    public List<Field> of(UUID enterprise) {
        return fields.values().stream().filter(f->f.farm().equals(enterprise)).toList();
    }
    public Field byId(UUID id) {return fields.get(id);}
    public boolean remove(ServerPlayer player,UUID farm,UUID id) {
        if (!allowed(player,farm))return false;
        Field found=fields.get(id);
        if(found==null||!found.farm().equals(farm))return false;
        fields.remove(id);setDirty();return true;
    }
    public boolean setSeed(ServerPlayer player,UUID farm,UUID fieldId,String seedId) {
        if(!allowed(player,farm) || !validSeed(seedId))return false;
        Field old=fields.get(fieldId);
        if(old==null || !old.farm().equals(farm))return false;
        fields.put(fieldId,new Field(old.id(),old.farm(),old.dimension(),old.minX(),old.maxX(),
            old.y(),old.minZ(),old.maxZ(),seedId,old.enabled()));setDirty();return true;
    }
    /** Trusted server-only NPC farm planning, never callable by a client packet. */
    public boolean chooseForOwnerNpc(ServerLevel level,UUID ownerNpc,UUID farm,UUID fieldId,String seed){
        if(!validSeed(seed)||!npcOwns(level,ownerNpc,farm))return false;
        Field old=fields.get(fieldId);
        if(old==null||!old.farm().equals(farm)||!old.enabled()
                ||!old.dimension().equals(level.dimension().location().toString()))return false;
        var land=KingdomTerritories.get(level.getServer());
        for(int x=old.minX();x<=old.maxX();x++)for(int z=old.minZ();z<=old.maxZ();z++){
            BlockPos soil=new BlockPos(x,old.y(),z);
            if(!level.hasChunkAt(soil)||!land.canUse(level,soil,ownerNpc))return false;
        }
        if(old.seedId().equals(seed))return true;
        fields.put(fieldId,new Field(old.id(),old.farm(),old.dimension(),old.minX(),
            old.maxX(),old.y(),old.minZ(),old.maxZ(),seed,old.enabled()));
        setDirty();
        return true;
    }

    /** Called by the autonomous settlement builder only AFTER a real owned farm and tillage exist. */
    public boolean registerForOwnerNpc(ServerLevel level,UUID ownerNpc,UUID farm,BlockPos first,BlockPos second){
        if(!npcOwns(level,ownerNpc,farm)||first.getY()!=second.getY())return false;
        int x1=Math.min(first.getX(),second.getX()),x2=Math.max(first.getX(),second.getX());
        int z1=Math.min(first.getZ(),second.getZ()),z2=Math.max(first.getZ(),second.getZ());
        if(x2-x1+1>MAX_SIDE||z2-z1+1>MAX_SIDE||of(farm).size()>=MAX_FIELDS)return false;
        String dimension=level.dimension().location().toString();
        for(Field f:fields.values())if(f.dimension().equals(dimension)&&f.y()==first.getY()
                && x1<=f.maxX()&&x2>=f.minX()&&z1<=f.maxZ()&&z2>=f.minZ())return false;
        var land=KingdomTerritories.get(level.getServer());
        for(int x=x1;x<=x2;x++)for(int z=z1;z<=z2;z++){
            BlockPos soil=new BlockPos(x,first.getY(),z);
            if(!level.hasChunkAt(soil)||!land.canUse(level,soil,ownerNpc)
                ||!level.getBlockState(soil).getBlock().getClass().getName()
                    .startsWith("net.dries007.tfc.common.blocks.soil.FarmlandBlock"))return false;
        }
        Field field=new Field(UUID.randomUUID(),farm,dimension,x1,x2,first.getY(),z1,z2,"",true);
        fields.put(field.id(),field);setDirty();return true;
    }
    private static boolean npcOwns(ServerLevel level,UUID ownerNpc,UUID farm){
        if(ownerNpc==null||farm==null)return false;
        return KingdomNpcData.get(level.getServer()).all().stream()
            .anyMatch(w->ownerNpc.equals(w.entityId)
                && farm.equals(KingdomNpcFarmOwner.ownedFarm(level.getServer(),w)));
    }

    public boolean toggle(ServerPlayer player,UUID farm,UUID id) {
        if(!allowed(player,farm))return false;
        Field old=fields.get(id);if(old==null||!old.farm().equals(farm))return false;
        fields.put(id,new Field(old.id(),old.farm(),old.dimension(),old.minX(),old.maxX(),
            old.y(),old.minZ(),old.maxZ(),old.seedId(),!old.enabled()));setDirty();return true;
    }
    public static String cropId(String seed) {
        if(seed==null||!seed.startsWith("tfc:seeds/"))return "";
        String name=seed.substring("tfc:seeds/".length());
        return name.isBlank()?"":"tfc:crop/"+name;
    }
    /** Only regular one-block crops. TFC double/climbing/pickable crops need separate actions. */
    public static boolean validSeed(String seed) {
        String crop=cropId(seed);
        ResourceLocation seedKey=ResourceLocation.tryParse(seed),cropKey=ResourceLocation.tryParse(crop);
        if(seedKey==null||cropKey==null || !BuiltInRegistries.ITEM.containsKey(seedKey)
                || !BuiltInRegistries.BLOCK.containsKey(cropKey))return false;
        Block plant=BuiltInRegistries.BLOCK.get(cropKey);
        return KingdomTfcFieldRules.simpleCrop(crop,plant.getClass().getName());
    }
    public static boolean allowed(ServerPlayer player, UUID farm) {
        var firms=KingdomEnterpriseData.get(player.getServer());
        var core=firms.core(farm);
        return firms.mayManage(player.getUUID(),farm) && firms.kind(farm)==EnterpriseKind.FARM
            && core!=null && core.active() && core.kind()==EnterpriseKind.FARM;
    }
    public boolean create(ServerPlayer player,UUID farm,BlockPos first,BlockPos second) {
        if(!allowed(player,farm) || first.getY()!=second.getY()
            || !player.serverLevel().dimension().location().toString().equals(
                KingdomEnterpriseData.get(player.getServer()).core(farm).dimension()))return false;
        int minX=Math.min(first.getX(),second.getX()),maxX=Math.max(first.getX(),second.getX()),
            minZ=Math.min(first.getZ(),second.getZ()),maxZ=Math.max(first.getZ(),second.getZ());
        if(maxX-minX+1>MAX_SIDE||maxZ-minZ+1>MAX_SIDE||of(farm).size()>=MAX_FIELDS)return false;
        String dimension=player.serverLevel().dimension().location().toString();
        for(Field existing:fields.values()) {
            if(existing.dimension().equals(dimension) && existing.y()==first.getY()
                && minX<=existing.maxX()&&maxX>=existing.minX()
                && minZ<=existing.maxZ()&&maxZ>=existing.minZ())return false;
        }
        var land=KingdomTerritories.get(player.getServer());ServerLevel level=player.serverLevel();
        // Every covered cell must really be owned; do not assume the two corners imply the rectangle.
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
            var soil=new BlockPos(x,first.getY(),z);
            if(!level.hasChunkAt(soil)||!land.canUse(level,soil,player.getUUID()))return false;
        }
        Field field=new Field(UUID.randomUUID(),farm,dimension,minX,maxX,first.getY(),
            minZ,maxZ,"",true);
        fields.put(field.id(),field);setDirty();return true;
    }
    public Field forCrop(UUID farm,String dimension,BlockPos crop) {
        for(Field field:of(farm))if(field.enabled()&&field.dimension().equals(dimension)
                &&field.contains(crop))return field;
        return null;
    }
    static KingdomFarmFieldData load(CompoundTag tag){
        var result=new KingdomFarmFieldData();
        for(Tag raw:tag.getList("Fields",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)raw;
            if(!t.hasUUID("Id")||!t.hasUUID("Farm"))continue;
            int x1=t.getInt("X1"),x2=t.getInt("X2"),z1=t.getInt("Z1"),z2=t.getInt("Z2");
            if(x2<x1||z2<z1||x2-x1+1>MAX_SIDE||z2-z1+1>MAX_SIDE)continue;
            var f=new Field(t.getUUID("Id"),t.getUUID("Farm"),t.getString("Dimension"),
                x1,x2,t.getInt("Y"),z1,z2,t.getString("Seed"),t.getBoolean("Enabled"));
            if(!f.dimension().isBlank()&&result.of(f.farm()).size()<MAX_FIELDS)result.fields.put(f.id(),f);
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag root) {
        ListTag list=new ListTag();
        for(Field f:fields.values()) {
            var t=new CompoundTag();t.putUUID("Id",f.id());t.putUUID("Farm",f.farm());
            t.putString("Dimension",f.dimension());t.putInt("X1",f.minX());t.putInt("X2",f.maxX());
            t.putInt("Y",f.y());t.putInt("Z1",f.minZ());t.putInt("Z2",f.maxZ());
            t.putString("Seed",f.seedId());t.putBoolean("Enabled",f.enabled());list.add(t);
        }
        root.put("Fields",list);return root;
    }
}
