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
    public static final int DATA_VERSION=4;
    public static final int MAX_LOG_ENTRIES=500;

    public record LogEntry(UUID actor, MunicipalRole role, String action, long gameTime) {}
    public record BoundContainer(AdministrativeBindingRole role,String dimension,long packedPos) {}
    public record WarrantEntry(UUID target,UUID caseId,String reason,long issuedAt) {}
    public record ExpansionDebit(UUID id,UUID townId,long copper,boolean refunded) {}

    private final Map<UUID,UUID> founders=new LinkedHashMap<>();
    private final Map<UUID,Map<UUID,EnumSet<MunicipalRole>>> roles=new LinkedHashMap<>();
    private final Map<UUID,EnumMap<AdministrativeKind,AdministrativeCoreBinding>> cores=new LinkedHashMap<>();
    private final Map<UUID,ArrayDeque<LogEntry>> logs=new LinkedHashMap<>();
    private final Map<UUID,EnumMap<AdministrativeBindingRole,LinkedHashSet<BoundContainer>>> containers=new LinkedHashMap<>();
    private final Map<UUID,Long> bankReserves=new LinkedHashMap<>();
    private final Map<UUID,Long> treasuryReserves=new LinkedHashMap<>();
    /** Recorded but physically unbacked legacy liabilities, never spendable. */
    private final Map<UUID,Long> legacyBankLiabilities=new LinkedHashMap<>();
    private final Map<UUID,Long> legacyTreasuryLiabilities=new LinkedHashMap<>();
    private final Map<UUID,Long> historicalTreasuries=new LinkedHashMap<>();
    private final Map<UUID,ExpansionDebit> expansionDebits=new LinkedHashMap<>();
    private final Map<UUID,LinkedHashMap<UUID,JusticeCase>> justiceCases=new LinkedHashMap<>();
    private final Map<UUID,LinkedHashMap<UUID,PrisonRecord>> prisonRecords=new LinkedHashMap<>();
    private final Map<UUID,LinkedHashMap<UUID,GuardRecord>> guardRecords=new LinkedHashMap<>();
    private final Map<UUID,LinkedHashMap<UUID,WarrantEntry>> warrants=new LinkedHashMap<>();
    private int dataVersion=DATA_VERSION;

    public static KingdomAdministrationData get(MinecraftServer server){
        KingdomAdministrationData data=server.overworld().getDataStorage()
                .computeIfAbsent(KingdomAdministrationData::fromTag,KingdomAdministrationData::new,KEY);
        boolean changed=false;
        for(KingdomCivicsData.Town town:KingdomCivicsData.get(server).towns()){
            changed|=data.migrateFounder(town.id(),town.mayor())==1;
            changed|=data.ensureHistoricalTreasury(town.id(),town.treasury());
        }
        if(changed)data.setDirty();
        return data;
    }
    public int dataVersion(){return dataVersion;}
    public UUID founder(UUID townId){return founders.get(townId);}

    /** Privileged service only: transfers founding responsibility, not treasury or land title. */
    public boolean adminChangeFounder(UUID townId,UUID founder){
        if(townId==null||founder==null||!founders.containsKey(townId))return false;
        for(var entry:founders.entrySet())
            if(!entry.getKey().equals(townId)&&founder.equals(entry.getValue()))return false;
        founders.put(townId,founder);setDirty();return true;
    }

    /** Keep the elected mayor role consistent with KingdomCivicsData.Town.mayor. */
    public void adminChangeMayor(UUID townId,UUID former,UUID next){
        if(townId==null||next==null)return;
        Map<UUID,EnumSet<MunicipalRole>> roster=roles.computeIfAbsent(townId,k->new LinkedHashMap<>());
        if(former!=null){
            var old=roster.get(former);
            if(old!=null){old.remove(MunicipalRole.MAYOR);if(old.isEmpty())roster.remove(former);}
        }
        roster.computeIfAbsent(next,k->EnumSet.noneOf(MunicipalRole.class))
            .add(MunicipalRole.MAYOR);
        setDirty();
    }

    /** A city with money, legacy liabilities or pending treasury recoveries is NEVER deleted. */
    public String adminRetirementBlocker(UUID townId){
        if(townId==null)return "Неизвестный город.";
        if(bankReserve(townId)!=0||treasuryReserve(townId)!=0
            ||legacyBankLiability(townId)!=0||legacyTreasuryLiability(townId)!=0
            ||historicalTreasuryLiability(townId)!=0)
            return "Существуют банковские резервы, казна или исторические обязательства.";
        if(expansionDebits(townId).stream().anyMatch(e->!e.refunded()))
            return "В реестре есть платежи расширения: сначала проведи финансовую сверку.";
        if(justiceCases.containsKey(townId)||prisonRecords.containsKey(townId)
            ||warrants.containsKey(townId))
            return "Есть судебные дела, заключённые или ордера — перенеси или закрой их.";
        return null;
    }

    /** Called only after checking all city finances and city/kingdom constraints. */
    public boolean adminRetireTown(UUID townId){
        if(!founders.containsKey(townId)||adminRetirementBlocker(townId)!=null)return false;
        founders.remove(townId);roles.remove(townId);cores.remove(townId);
        containers.remove(townId);logs.remove(townId);guardRecords.remove(townId);
        bankReserves.remove(townId);treasuryReserves.remove(townId);
        legacyBankLiabilities.remove(townId);legacyTreasuryLiabilities.remove(townId);
        historicalTreasuries.remove(townId);
        expansionDebits.values().removeIf(e->townId.equals(e.townId()));
        setDirty();return true;
    }
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
    public List<BoundContainer> boundContainers(UUID townId,AdministrativeBindingRole role){
        return containerBindings(townId,role);
    }
    public long bankReserve(UUID townId){return bankReserves.getOrDefault(townId,0L);}
    public long treasuryReserve(UUID townId){return treasuryReserves.getOrDefault(townId,0L);}
    public long legacyBankLiability(UUID town){return legacyBankLiabilities.getOrDefault(town,0L);}
    public long legacyTreasuryLiability(UUID town){return legacyTreasuryLiabilities.getOrDefault(town,0L);}
    /** Freeze a city's unbacked numerical reserves for investigation, never mint them.
     * Invoked only during an explicit manager-confirmed empty-vault migration.
     */
    public void archiveUnbackedReserves(UUID town){
        if(town==null)return;
        long oldBank=bankReserve(town),oldCity=treasuryReserve(town);
        if(oldBank>0){
            legacyBankLiabilities.merge(town,oldBank,KingdomAdministrationData::saturatingSum);
            bankReserves.remove(town);
        }
        if(oldCity>0){
            legacyTreasuryLiabilities.merge(town,oldCity,KingdomAdministrationData::saturatingSum);
            treasuryReserves.remove(town);
        }
        if(oldBank>0||oldCity>0)setDirty();
    }
    private static long saturatingSum(long a,long b){
        return a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b;
    }
    public ExpansionDebit expansionDebit(UUID id){return expansionDebits.get(id);}
    public List<ExpansionDebit> expansionDebits(UUID townId){
        List<ExpansionDebit> result=new ArrayList<>();
        for(ExpansionDebit value:expansionDebits.values())if(value.townId().equals(townId))result.add(value);
        return List.copyOf(result);
    }
    /** Idempotent debit of actual treasury backing; never debit one transaction twice. */
    public boolean debitExpansion(UUID townId,UUID transactionId,long copper){
        if(townId==null||transactionId==null||copper<=0||copper>25600)return false;
        ExpansionDebit prior=expansionDebits.get(transactionId);
        if(prior!=null)return !prior.refunded()&&prior.townId().equals(townId)&&prior.copper()==copper;
        if(!subtractReserve(treasuryReserves,townId,copper))return false;
        expansionDebits.put(transactionId,new ExpansionDebit(transactionId,townId,copper,false));
        setDirty();return true;
    }
    /** Compensation only for a payment whose city purchase did NOT commit. */
    public boolean refundExpansion(UUID transactionId){
        ExpansionDebit debit=expansionDebits.get(transactionId);
        if(debit==null)return false;
        if(debit.refunded())return true;
        if(!addReserve(treasuryReserves,debit.townId(),debit.copper()))return false;
        expansionDebits.put(transactionId,new ExpansionDebit(debit.id(),debit.townId(),debit.copper(),true));
        setDirty();return true;
    }
    public long historicalTreasuryLiability(UUID townId){return historicalTreasuries.getOrDefault(townId,0L);}
    public boolean ensureHistoricalTreasury(UUID townId,long amount){
        if(townId==null||amount<0||historicalTreasuries.containsKey(townId))return false;
        historicalTreasuries.put(townId,amount);setDirty();return true;
    }
    public boolean depositBankReserve(UUID townId,long amount){return addReserve(bankReserves,townId,amount);}
    public boolean withdrawBankReserve(UUID townId,long amount){return subtractReserve(bankReserves,townId,amount);}
    public boolean depositTreasuryReserve(UUID townId,long amount){return addReserve(treasuryReserves,townId,amount);}
    public boolean withdrawTreasuryReserve(UUID townId,long amount){return subtractReserve(treasuryReserves,townId,amount);}
    private boolean addReserve(Map<UUID,Long> reserves,UUID townId,long amount){
        long prior=townId==null?0:reserves.getOrDefault(townId,0L);
        if(townId==null||amount<=0||prior>Long.MAX_VALUE-amount)return false;
        reserves.put(townId,prior+amount);setDirty();return true;
    }
    private boolean subtractReserve(Map<UUID,Long> reserves,UUID townId,long amount){
        long prior=townId==null?0:reserves.getOrDefault(townId,0L);
        if(townId==null||amount<=0||prior<amount)return false;
        reserves.put(townId,prior-amount);setDirty();return true;
    }
    public JusticeCase createCase(UUID townId,UUID actor,UUID mayor,Set<MunicipalRole> actorRoles,
                                  UUID complainant,UUID defendant,String summary,long gameTime){
        if(!AdministrativePermissionService.mayManage(actor,founder(townId),mayor,actorRoles,AdministrativeKind.COURT))return null;
        JusticeCase value;try{value=new JusticeCase(UUID.randomUUID(),townId,complainant,defendant,null,JusticeCase.Status.OPEN,summary,"",0,gameTime);}catch(IllegalArgumentException ex){return null;}
        justiceCases.computeIfAbsent(townId,unused->new LinkedHashMap<>()).put(value.id(),value);setDirty();return value;
    }
    public boolean decideCase(UUID townId,UUID actor,UUID mayor,Set<MunicipalRole> actorRoles,UUID caseId,String decision,long fineCopper){
        if(!AdministrativePermissionService.mayManage(actor,founder(townId),mayor,actorRoles,AdministrativeKind.COURT))return false;
        LinkedHashMap<UUID,JusticeCase> values=justiceCases.get(townId);JusticeCase prior=values==null?null:values.get(caseId);if(prior==null)return false;
        try{JusticeCase assigned=prior.judge()==null?prior.assignJudge(actor):prior;if(!actor.equals(assigned.judge()))return false;values.put(caseId,assigned.decide(decision,fineCopper));setDirty();return true;}
        catch(IllegalArgumentException ex){return false;}
    }
    public List<JusticeCase> justiceCases(UUID townId,int page,int pageSize){return page(justiceCases.get(townId),page,pageSize);}
    public boolean sentence(UUID townId,UUID actor,UUID mayor,Set<MunicipalRole> actorRoles,UUID prisoner,UUID caseId,
                            String dimension,long cellPos,long start,long end,String reason){
        if(!AdministrativePermissionService.mayManage(actor,founder(townId),mayor,actorRoles,AdministrativeKind.PRISON)
                ||containerBindings(townId,AdministrativeBindingRole.PRISON_CELL).stream().noneMatch(c->c.dimension().equals(dimension)&&c.packedPos()==cellPos))return false;
        PrisonRecord value;try{value=new PrisonRecord(UUID.randomUUID(),townId,prisoner,caseId,dimension,cellPos,start,end,reason,true);}catch(IllegalArgumentException ex){return false;}
        prisonRecords.computeIfAbsent(townId,unused->new LinkedHashMap<>()).put(value.id(),value);setDirty();return true;
    }
    public boolean releasePrisoner(UUID townId,UUID actor,UUID mayor,Set<MunicipalRole> actorRoles,UUID recordId){
        if(!AdministrativePermissionService.mayManage(actor,founder(townId),mayor,actorRoles,AdministrativeKind.PRISON))return false;
        LinkedHashMap<UUID,PrisonRecord> values=prisonRecords.get(townId);PrisonRecord prior=values==null?null:values.get(recordId);if(prior==null||!prior.active())return false;
        values.put(recordId,prior.release());setDirty();return true;
    }
    public List<PrisonRecord> prisonRecords(UUID townId,int page,int pageSize){return page(prisonRecords.get(townId),page,pageSize);}
    public boolean registerGuard(UUID townId,UUID actor,UUID mayor,Set<MunicipalRole> actorRoles,GuardRecord value){
        if(value==null||!townId.equals(value.townId())||!AdministrativePermissionService.mayManage(actor,founder(townId),mayor,actorRoles,AdministrativeKind.GUARD_ADMINISTRATION))return false;
        LinkedHashMap<UUID,GuardRecord> values=guardRecords.computeIfAbsent(townId,unused->new LinkedHashMap<>());if(values.containsKey(value.id()))return false;
        values.put(value.id(),value);setDirty();return true;
    }
    public boolean deactivateGuard(UUID townId,UUID actor,UUID mayor,Set<MunicipalRole> actorRoles,UUID recordId){
        if(!AdministrativePermissionService.mayManage(actor,founder(townId),mayor,actorRoles,AdministrativeKind.GUARD_ADMINISTRATION))return false;
        LinkedHashMap<UUID,GuardRecord> values=guardRecords.get(townId);GuardRecord prior=values==null?null:values.get(recordId);if(prior==null||!prior.active())return false;
        values.put(recordId,prior.deactivate());setDirty();return true;
    }
    public List<GuardRecord> guards(UUID townId,int page,int pageSize){return page(guardRecords.get(townId),page,pageSize);}
    public boolean issueWarrant(UUID townId,UUID actor,UUID mayor,Set<MunicipalRole> actorRoles,UUID target,UUID caseId,String reason,long gameTime){
        if(target==null||caseId==null||gameTime<0||!JusticeCase.valid(reason,96)
                ||!AdministrativePermissionService.mayManage(actor,founder(townId),mayor,actorRoles,AdministrativeKind.GUARD_ADMINISTRATION))return false;
        LinkedHashMap<UUID,WarrantEntry> values=warrants.computeIfAbsent(townId,unused->new LinkedHashMap<>());if(values.containsKey(target))return false;
        values.put(target,new WarrantEntry(target,caseId,reason,gameTime));setDirty();return true;
    }
    public boolean clearWarrant(UUID townId,UUID actor,UUID mayor,Set<MunicipalRole> actorRoles,UUID target){
        if(!AdministrativePermissionService.mayManage(actor,founder(townId),mayor,actorRoles,AdministrativeKind.GUARD_ADMINISTRATION))return false;
        Map<UUID,WarrantEntry> values=warrants.get(townId);if(values==null||values.remove(target)==null)return false;setDirty();return true;
    }
    public List<WarrantEntry> wantedEntries(UUID townId,int page,int pageSize){return page(warrants.get(townId),page,pageSize);}
    private static <T> List<T> page(Map<UUID,T> values,int page,int pageSize){
        if(values==null||page<0||pageSize<1)return List.of();int bounded=Math.min(pageSize,32);List<T> all=new ArrayList<>(values.values());
        int from=(int)Math.min(all.size(),(long)page*bounded);return List.copyOf(all.subList(from,Math.min(all.size(),from+bounded)));
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
        ListTag financeList=root.getList("Finance",Tag.TAG_COMPOUND);
        for(int i=0;i<financeList.size();i++){
            CompoundTag row=financeList.getCompound(i);if(!row.hasUUID("Town"))continue;UUID town=row.getUUID("Town");
            data.bankReserves.put(town,Math.max(0,row.getLong("BankReserve")));
            data.treasuryReserves.put(town,Math.max(0,row.getLong("TreasuryReserve")));
            data.historicalTreasuries.put(town,Math.max(0,row.getLong("HistoricalTreasury")));
            long legacyBank=Math.max(0,row.getLong("LegacyBankLiability"));
            long legacyCity=Math.max(0,row.getLong("LegacyTreasuryLiability"));
            if(legacyBank>0)data.legacyBankLiabilities.put(town,legacyBank);
            if(legacyCity>0)data.legacyTreasuryLiabilities.put(town,legacyCity);
        }
        ListTag expansionList=root.getList("ExpansionDebits",Tag.TAG_COMPOUND);
        for(int i=0;i<expansionList.size();i++){
            CompoundTag row=expansionList.getCompound(i);
            if(!row.hasUUID("Id")||!row.hasUUID("Town")||row.getLong("Copper")<=0)continue;
            var debit=new ExpansionDebit(row.getUUID("Id"),row.getUUID("Town"),
                    row.getLong("Copper"),row.getBoolean("Refunded"));
            data.expansionDebits.putIfAbsent(debit.id(),debit);
        }
        ListTag cases=root.getList("JusticeCases",Tag.TAG_COMPOUND);for(int i=0;i<cases.size();i++){
            CompoundTag row=cases.getCompound(i);if(!row.hasUUID("Id")||!row.hasUUID("Town")||!row.hasUUID("Complainant")||!row.hasUUID("Defendant"))continue;
            try{JusticeCase value=new JusticeCase(row.getUUID("Id"),row.getUUID("Town"),row.getUUID("Complainant"),row.getUUID("Defendant"),row.hasUUID("Judge")?row.getUUID("Judge"):null,
                    JusticeCase.Status.valueOf(row.getString("Status")),row.getString("Summary"),row.getString("Decision"),row.getLong("Fine"),row.getLong("Created"));
                data.justiceCases.computeIfAbsent(value.townId(),unused->new LinkedHashMap<>()).putIfAbsent(value.id(),value);}catch(IllegalArgumentException ignored){}
        }
        ListTag prisoners=root.getList("PrisonRecords",Tag.TAG_COMPOUND);for(int i=0;i<prisoners.size();i++){
            CompoundTag row=prisoners.getCompound(i);if(!row.hasUUID("Id")||!row.hasUUID("Town")||!row.hasUUID("Prisoner"))continue;
            try{PrisonRecord value=new PrisonRecord(row.getUUID("Id"),row.getUUID("Town"),row.getUUID("Prisoner"),row.hasUUID("Case")?row.getUUID("Case"):null,
                    row.getString("Dimension"),row.getLong("Cell"),row.getLong("Start"),row.getLong("End"),row.getString("Reason"),row.getBoolean("Active"));
                data.prisonRecords.computeIfAbsent(value.townId(),unused->new LinkedHashMap<>()).putIfAbsent(value.id(),value);}catch(IllegalArgumentException ignored){}
        }
        ListTag guards=root.getList("GuardRecords",Tag.TAG_COMPOUND);for(int i=0;i<guards.size();i++){
            CompoundTag row=guards.getCompound(i);if(!row.hasUUID("Id")||!row.hasUUID("Town")||!row.hasUUID("Guard"))continue;
            try{GuardRecord value=new GuardRecord(row.getUUID("Id"),row.getUUID("Town"),row.getUUID("Guard"),row.getString("Name"),row.getString("Dimension"),
                    row.getInt("MinX"),row.getInt("MinZ"),row.getInt("MaxX"),row.getInt("MaxZ"),row.getBoolean("Active"));
                data.guardRecords.computeIfAbsent(value.townId(),unused->new LinkedHashMap<>()).putIfAbsent(value.id(),value);}catch(IllegalArgumentException ignored){}
        }
        ListTag warrantList=root.getList("Warrants",Tag.TAG_COMPOUND);for(int i=0;i<warrantList.size();i++){
            CompoundTag row=warrantList.getCompound(i);if(!row.hasUUID("Town")||!row.hasUUID("Target")||!row.hasUUID("Case")||!JusticeCase.valid(row.getString("Reason"),96))continue;
            UUID town=row.getUUID("Town"),target=row.getUUID("Target");data.warrants.computeIfAbsent(town,unused->new LinkedHashMap<>())
                    .putIfAbsent(target,new WarrantEntry(target,row.getUUID("Case"),row.getString("Reason"),Math.max(0,row.getLong("Issued"))));
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
        }root.put("Containers",containerList);
        java.util.LinkedHashSet<UUID> financialTowns=new java.util.LinkedHashSet<>();financialTowns.addAll(bankReserves.keySet());financialTowns.addAll(treasuryReserves.keySet());financialTowns.addAll(historicalTreasuries.keySet());financialTowns.addAll(legacyBankLiabilities.keySet());financialTowns.addAll(legacyTreasuryLiabilities.keySet());
        ListTag financeList=new ListTag();for(UUID town:financialTowns){
            CompoundTag row=new CompoundTag();row.putUUID("Town",town);row.putLong("BankReserve",bankReserve(town));
            row.putLong("TreasuryReserve",treasuryReserve(town));row.putLong("HistoricalTreasury",historicalTreasuryLiability(town));
            row.putLong("LegacyBankLiability",legacyBankLiability(town));
            row.putLong("LegacyTreasuryLiability",legacyTreasuryLiability(town));
            financeList.add(row);
        }root.put("Finance",financeList);
        ListTag expansionList=new ListTag();
        for(ExpansionDebit value:expansionDebits.values()){
            CompoundTag row=new CompoundTag();row.putUUID("Id",value.id());row.putUUID("Town",value.townId());
            row.putLong("Copper",value.copper());row.putBoolean("Refunded",value.refunded());expansionList.add(row);
        }
        root.put("ExpansionDebits",expansionList);
        ListTag cases=new ListTag();for(var town:justiceCases.entrySet())for(JusticeCase value:town.getValue().values()){
            CompoundTag row=new CompoundTag();row.putUUID("Id",value.id());row.putUUID("Town",value.townId());row.putUUID("Complainant",value.complainant());row.putUUID("Defendant",value.defendant());
            if(value.judge()!=null)row.putUUID("Judge",value.judge());row.putString("Status",value.status().name());row.putString("Summary",value.summary());row.putString("Decision",value.decision());
            row.putLong("Fine",value.fineCopper());row.putLong("Created",value.createdAt());cases.add(row);
        }root.put("JusticeCases",cases);
        ListTag prisoners=new ListTag();for(var town:prisonRecords.entrySet())for(PrisonRecord value:town.getValue().values()){
            CompoundTag row=new CompoundTag();row.putUUID("Id",value.id());row.putUUID("Town",value.townId());row.putUUID("Prisoner",value.prisoner());if(value.caseId()!=null)row.putUUID("Case",value.caseId());
            row.putString("Dimension",value.cellDimension());row.putLong("Cell",value.cellPos());row.putLong("Start",value.sentenceStart());row.putLong("End",value.sentenceEnd());
            row.putString("Reason",value.reason());row.putBoolean("Active",value.active());prisoners.add(row);
        }root.put("PrisonRecords",prisoners);
        ListTag guards=new ListTag();for(var town:guardRecords.entrySet())for(GuardRecord value:town.getValue().values()){
            CompoundTag row=new CompoundTag();row.putUUID("Id",value.id());row.putUUID("Town",value.townId());row.putUUID("Guard",value.guardId());row.putString("Name",value.name());
            row.putString("Dimension",value.patrolDimension());row.putInt("MinX",value.minChunkX());row.putInt("MinZ",value.minChunkZ());row.putInt("MaxX",value.maxChunkX());row.putInt("MaxZ",value.maxChunkZ());
            row.putBoolean("Active",value.active());guards.add(row);
        }root.put("GuardRecords",guards);
        ListTag warrantList=new ListTag();for(var town:warrants.entrySet())for(WarrantEntry value:town.getValue().values()){
            CompoundTag row=new CompoundTag();row.putUUID("Town",town.getKey());row.putUUID("Target",value.target());row.putUUID("Case",value.caseId());row.putString("Reason",value.reason());row.putLong("Issued",value.issuedAt());warrantList.add(row);
        }root.put("Warrants",warrantList);return root;
    }
}
