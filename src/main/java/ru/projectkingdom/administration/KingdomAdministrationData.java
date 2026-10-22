package ru.projectkingdom.administration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import ru.projectkingdom.npc.KingdomCivicsData;

public final class KingdomAdministrationData extends SavedData {
    private static final String KEY="kingdom_administration_v1";
    public static final int DATA_VERSION=2;
    public static final int MAX_LOG_ENTRIES=500;

    public record LogEntry(UUID actor, MunicipalRole role, String action, long gameTime) {}
    public record BoundContainer(AdministrativeBindingRole role,String dimension,long packedPos) {}

    private final Map<UUID,UUID> founders=new LinkedHashMap<>();
    private final Map<UUID,Map<UUID,EnumSet<MunicipalRole>>> roles=new LinkedHashMap<>();
    private final Map<UUID,EnumMap<AdministrativeKind,AdministrativeCoreBinding>> cores=new LinkedHashMap<>();
    private final Map<UUID,ArrayDeque<LogEntry>> logs=new LinkedHashMap<>();
    private final Map<UUID,EnumMap<AdministrativeBindingRole,LinkedHashSet<BoundContainer>>> containers=new LinkedHashMap<>();
    private int dataVersion=DATA_VERSION;

    public static KingdomAdministrationData get(MinecraftServer server){
        KingdomAdministrationData data=server.overworld().getDataStorage()
                .computeIfAbsent(KingdomAdministrationData::fromTag,KingdomAdministrationData::new,KEY);
        boolean changed=false;
        for(KingdomCivicsData.Town town:KingdomCivicsData.get(server).towns())
            changed|=data.migrateFounder(town.id(),town.mayor())==1;
        if(changed)data.setDirty();
        return data;
    }
    public int dataVersion(){return dataVersion;}
    public UUID founder(UUID townId){return founders.get(townId);}
    public boolean ensureFounder(UUID townId,UUID founder){return migrateFounder(townId,founder)==1;}
    public int migrateFounder(UUID townId,UUID mayor){
        if(townId==null||mayor==null||founders.containsKey(townId))return 0;
        founders.put(townId,mayor);setDirty();return 1;
    }
    public Set<MunicipalRole> roles(UUID townId,UUID playerId){
        Map<UUID,EnumSet<MunicipalRole>> townRoles=roles.get(townId);
        EnumSet<MunicipalRole> value=townRoles==null?null:townRoles.get(playerId);
        return value==null?Set.of():Collections.unmodifiableSet(EnumSet.copyOf(value));
    }
    private boolean mayGovern(UUID townId,UUID actor,UUID mayor){
        return actor!=null&&(actor.equals(founder(townId))||actor.equals(mayor));
    }
    public boolean appoint(UUID townId,UUID actor,UUID mayor,UUID playerId,MunicipalRole role){
        if(townId==null||playerId==null||role==null||!mayGovern(townId,actor,mayor))return false;
        if(role==MunicipalRole.MAYOR&&!actor.equals(founder(townId)))return false;
        EnumSet<MunicipalRole> assigned=roles.computeIfAbsent(townId,unused->new LinkedHashMap<>())
                .computeIfAbsent(playerId,unused->EnumSet.noneOf(MunicipalRole.class));
        if(!assigned.add(role))return false;
        setDirty();return true;
    }
    public boolean revoke(UUID townId,UUID actor,UUID mayor,UUID playerId,MunicipalRole role){
        if(townId==null||playerId==null||role==null||!mayGovern(townId,actor,mayor))return false;
        if(role==MunicipalRole.MAYOR&&!actor.equals(founder(townId)))return false;
        Map<UUID,EnumSet<MunicipalRole>> townRoles=roles.get(townId);
        EnumSet<MunicipalRole> assigned=townRoles==null?null:townRoles.get(playerId);
        if(assigned==null||!assigned.remove(role))return false;
        if(assigned.isEmpty())townRoles.remove(playerId);
        if(townRoles.isEmpty())roles.remove(townId);
        setDirty();return true;
    }
    public AdministrativeCoreBinding core(UUID townId,AdministrativeKind kind){
        Map<AdministrativeKind,AdministrativeCoreBinding> townCores=cores.get(townId);
        return townCores==null?null:townCores.get(kind);
    }
    public Collection<AdministrativeCoreBinding> cores(UUID townId){
        Map<AdministrativeKind,AdministrativeCoreBinding> townCores=cores.get(townId);
        return townCores==null?List.of():List.copyOf(townCores.values());
    }
    public boolean activateCore(UUID actor,UUID mayor,AdministrativeCoreBinding binding){
        if(binding==null||!AdministrativePermissionService.mayManage(actor,founder(binding.townId()),mayor,
                roles(binding.townId(),actor),binding.kind()))return false;
        EnumMap<AdministrativeKind,AdministrativeCoreBinding> townCores=cores.computeIfAbsent(binding.townId(),unused->new EnumMap<>(AdministrativeKind.class));
        if(townCores.containsKey(binding.kind()))return false;
        townCores.put(binding.kind(),binding);setDirty();return true;
    }
    public boolean suspendCore(UUID actor,UUID mayor,AdministrativeCoreBinding binding){
        if(binding==null||!AdministrativePermissionService.mayManage(actor,founder(binding.townId()),mayor,
                roles(binding.townId(),actor),binding.kind()))return false;
        EnumMap<AdministrativeKind,AdministrativeCoreBinding> townCores=cores.get(binding.townId());
        if(townCores==null||!binding.equals(townCores.get(binding.kind())))return false;
        townCores.remove(binding.kind());
        if(townCores.isEmpty())cores.remove(binding.townId());
        setDirty();return true;
    }
    public boolean record(UUID townId,UUID actor,MunicipalRole role,String action,long gameTime){
        if(townId==null||actor==null||action==null||action.isBlank()||action.length()>96
                ||action.chars().anyMatch(Character::isISOControl))return false;
        ArrayDeque<LogEntry> townLog=logs.computeIfAbsent(townId,unused->new ArrayDeque<>());
        while(townLog.size()>=MAX_LOG_ENTRIES)townLog.removeFirst();
        townLog.addLast(new LogEntry(actor,role,action,Math.max(0,gameTime)));setDirty();return true;
    }
    public List<LogEntry> entries(UUID townId,int limit){
        if(limit<=0)return List.of();
        ArrayDeque<LogEntry> townLog=logs.get(townId);if(townLog==null)return List.of();
        List<LogEntry> all=new ArrayList<>(townLog);
        int start=Math.max(0,all.size()-Math.min(limit,MAX_LOG_ENTRIES));
        return List.copyOf(all.subList(start,all.size()));
    }
    public boolean bindContainer(UUID townId,AdministrativeBindingRole role,String dimension,long packedPos){
        if(townId==null||role==null||dimension==null||dimension.isBlank())return false;
        EnumMap<AdministrativeBindingRole,LinkedHashSet<BoundContainer>> townBindings=containers.computeIfAbsent(townId,unused->new EnumMap<>(AdministrativeBindingRole.class));
        LinkedHashSet<BoundContainer> values=townBindings.computeIfAbsent(role,unused->new LinkedHashSet<>());
        BoundContainer binding=new BoundContainer(role,dimension,packedPos);
        if(values.contains(binding)||(!role.multiple()&&!values.isEmpty())||values.size()>=64)return false;
        values.add(binding);setDirty();return true;
    }
    public List<BoundContainer> containerBindings(UUID townId,AdministrativeBindingRole role){
        Map<AdministrativeBindingRole,LinkedHashSet<BoundContainer>> townBindings=containers.get(townId);
        Set<BoundContainer> values=townBindings==null?null:townBindings.get(role);
        return values==null?List.of():List.copyOf(values);
    }

    public static KingdomAdministrationData fromTag(CompoundTag root){
        KingdomAdministrationData data=new KingdomAdministrationData();
        data.dataVersion=Math.max(1,root.getInt("DataVersion"));
        ListTag founders=root.getList("Founders",Tag.TAG_COMPOUND);
        for(int i=0;i<founders.size();i++){
            CompoundTag row=founders.getCompound(i);
            if(row.hasUUID("Town")&&row.hasUUID("Founder"))data.founders.put(row.getUUID("Town"),row.getUUID("Founder"));
        }
        ListTag roleList=root.getList("Roles",Tag.TAG_COMPOUND);
        for(int i=0;i<roleList.size();i++){
            CompoundTag row=roleList.getCompound(i);if(!row.hasUUID("Town")||!row.hasUUID("Player"))continue;
            MunicipalRole role;try{role=MunicipalRole.valueOf(row.getString("Role"));}catch(IllegalArgumentException ex){continue;}
            data.roles.computeIfAbsent(row.getUUID("Town"),unused->new LinkedHashMap<>())
                    .computeIfAbsent(row.getUUID("Player"),unused->EnumSet.noneOf(MunicipalRole.class)).add(role);
        }
        ListTag coreList=root.getList("Cores",Tag.TAG_COMPOUND);
        for(int i=0;i<coreList.size();i++){
            CompoundTag row=coreList.getCompound(i);if(!row.hasUUID("Town"))continue;
            AdministrativeKind kind=AdministrativeKind.byId(row.getString("Kind")).orElse(null);
            String dimension=row.getString("Dimension");if(kind==null||dimension.isBlank())continue;
            UUID town=row.getUUID("Town");
            data.cores.computeIfAbsent(town,unused->new EnumMap<>(AdministrativeKind.class))
                    .putIfAbsent(kind,new AdministrativeCoreBinding(town,kind,dimension,row.getLong("Pos")));
        }
        ListTag logList=root.getList("Logs",Tag.TAG_COMPOUND);
        for(int i=Math.max(0,logList.size()-MAX_LOG_ENTRIES);i<logList.size();i++){
            CompoundTag row=logList.getCompound(i);if(!row.hasUUID("Town")||!row.hasUUID("Actor"))continue;
            String action=row.getString("Action");if(action.isBlank()||action.length()>96)continue;
            MunicipalRole role=null;try{if(row.contains("Role",Tag.TAG_STRING))role=MunicipalRole.valueOf(row.getString("Role"));}catch(IllegalArgumentException ignored){}
            data.logs.computeIfAbsent(row.getUUID("Town"),unused->new ArrayDeque<>())
                    .addLast(new LogEntry(row.getUUID("Actor"),role,action,Math.max(0,row.getLong("GameTime"))));
        }
        ListTag containerList=root.getList("Containers",Tag.TAG_COMPOUND);
        for(int i=0;i<containerList.size();i++){
            CompoundTag row=containerList.getCompound(i);if(!row.hasUUID("Town"))continue;
            AdministrativeBindingRole role;try{role=AdministrativeBindingRole.valueOf(row.getString("Role"));}catch(IllegalArgumentException ex){continue;}
            String dimension=row.getString("Dimension");if(dimension.isBlank())continue;
            UUID town=row.getUUID("Town");LinkedHashSet<BoundContainer> values=data.containers
                    .computeIfAbsent(town,unused->new EnumMap<>(AdministrativeBindingRole.class))
                    .computeIfAbsent(role,unused->new LinkedHashSet<>());
            if((role.multiple()||values.isEmpty())&&values.size()<64)values.add(new BoundContainer(role,dimension,row.getLong("Pos")));
        }
        data.dataVersion=DATA_VERSION;return data;
    }
    @Override public CompoundTag save(CompoundTag root){
        root.putInt("DataVersion",DATA_VERSION);
        ListTag founderList=new ListTag();for(var entry:founders.entrySet()){
            CompoundTag row=new CompoundTag();row.putUUID("Town",entry.getKey());row.putUUID("Founder",entry.getValue());founderList.add(row);
        }root.put("Founders",founderList);
        ListTag roleList=new ListTag();for(var town:roles.entrySet())for(var player:town.getValue().entrySet())for(MunicipalRole role:player.getValue()){
            CompoundTag row=new CompoundTag();row.putUUID("Town",town.getKey());row.putUUID("Player",player.getKey());row.putString("Role",role.name());roleList.add(row);
        }root.put("Roles",roleList);
        ListTag coreList=new ListTag();for(var town:cores.entrySet())for(var binding:town.getValue().values()){
            CompoundTag row=new CompoundTag();row.putUUID("Town",binding.townId());row.putString("Kind",binding.kind().id());
            row.putString("Dimension",binding.dimension());row.putLong("Pos",binding.packedPos());coreList.add(row);
        }root.put("Cores",coreList);
        ListTag logList=new ListTag();for(var town:logs.entrySet())for(LogEntry entry:town.getValue()){
            CompoundTag row=new CompoundTag();row.putUUID("Town",town.getKey());row.putUUID("Actor",entry.actor());
            if(entry.role()!=null)row.putString("Role",entry.role().name());row.putString("Action",entry.action());row.putLong("GameTime",entry.gameTime());logList.add(row);
        }root.put("Logs",logList);
        ListTag containerList=new ListTag();for(var town:containers.entrySet())for(var role:town.getValue().entrySet())for(BoundContainer binding:role.getValue()){
            CompoundTag row=new CompoundTag();row.putUUID("Town",town.getKey());row.putString("Role",binding.role().name());
            row.putString("Dimension",binding.dimension());row.putLong("Pos",binding.packedPos());containerList.add(row);
        }root.put("Containers",containerList);return root;
    }
}
