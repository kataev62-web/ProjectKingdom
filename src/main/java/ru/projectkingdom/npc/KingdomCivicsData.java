package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;
import ru.projectkingdom.administration.AdministrativeKind;
import ru.projectkingdom.administration.AdministrativePermissionService;
import ru.projectkingdom.administration.KingdomAdministrationData;
import ru.projectkingdom.administration.MunicipalRole;

/** Towny-inspired server-owned towns/nations/plots; no Bukkit dependency. */
public final class KingdomCivicsData extends SavedData {
    private static final String KEY = "kingdom_civics_v1";
    public enum Zone { RESIDENTIAL, COMMERCIAL, INDUSTRIAL, FARM, MILITARY, PUBLIC }
    public enum Action { BUILD, BREAK, INTERACT, USE }
    public enum Relation { RESIDENT, NATION, ALLY, OUTSIDER }
    private final Map<UUID,Set<UUID>> alliances = new LinkedHashMap<>();
    private transient MinecraftServer runtimeServer;
    public record Town(UUID id, String name, UUID mayor, UUID nation, long treasury) {}
    public record Nation(UUID id, String name, UUID ruler, long treasury) {}
    public record Plot(String dimension, int x, int z, UUID town, UUID tenant, Zone zone, boolean publicUse) {}
    /** Stored alongside town balances and city plots for a single authoritative city commit. */
    public record ExpansionReceipt(UUID id,UUID townId,long copper,String dimension,int chunks) {}
    private final Map<UUID,ExpansionReceipt> expansionReceipts = new LinkedHashMap<>();
    public ExpansionReceipt expansionReceipt(UUID id){return expansionReceipts.get(id);}
    public Collection<ExpansionReceipt> expansionReceipts(UUID townId){
        List<ExpansionReceipt> result=new ArrayList<>();
        for(var receipt:expansionReceipts.values())if(receipt.townId().equals(townId))result.add(receipt);
        return List.copyOf(result);
    }
    private final Map<UUID,Town> towns = new LinkedHashMap<>();
    private final Map<UUID,Nation> nations = new LinkedHashMap<>();
    private final Map<String,Plot> plots = new LinkedHashMap<>();
    /** Personal title registry, distinct from municipal chunk jurisdiction. Legacy plots remain mayor-owned. */
    private final Map<String,UUID> plotOwners = new LinkedHashMap<>();
    public UUID plotOwner(String dimension,int x,int z) {
        String k=key(dimension,x,z); Plot p=plots.get(k);
        if(p==null)return null;
        UUID titled=plotOwners.get(k);
        Town town=towns.get(p.town());
        return titled!=null?titled:(town==null?null:town.mayor());
    }
    /** Registers a personal title only after an externally verified, paid land sale. */
    public boolean registerPaidSale(UUID seller,UUID buyer,String dimension,int x,int z) {
        String k=key(dimension,x,z); Plot p=plots.get(k);
        if(p==null||seller==null||buyer==null||seller.equals(buyer)||!seller.equals(plotOwner(dimension,x,z))
                ||p.tenant()!=null||leases.containsKey(k))return false;
        plotOwners.put(k,buyer); offers.remove(k); sales.remove(k);clearWorkers(k);setDirty();return true;
    }
    /** Server-side land sale listings. Payment is always physical cash at a town hall or bank counter. */
    public record SaleOffer(UUID seller, long priceCopper) {}
    private final Map<String,SaleOffer> sales = new LinkedHashMap<>();
    public SaleOffer saleOffer(String dimension,int x,int z) { return sales.get(key(dimension,x,z)); }
    public boolean offerSale(UUID seller,String dimension,int x,int z,long priceCopper) {
        String k=key(dimension,x,z);Plot p=plots.get(k);
        if(seller==null||p==null||!seller.equals(plotOwner(dimension,x,z))
                ||p.tenant()!=null||leases.containsKey(k)||priceCopper<1||priceCopper>1_000_000)return false;
        sales.put(k,new SaleOffer(seller,priceCopper));setDirty();return true;
    }
    public boolean cancelSale(UUID seller,String dimension,int x,int z) {
        String k=key(dimension,x,z);SaleOffer o=sales.get(k);
        if(o==null||!o.seller().equals(seller))return false;
        sales.remove(k);setDirty();return true;
    }
    public boolean canBuySale(UUID buyer,String dimension,int x,int z) {
        String k=key(dimension,x,z);SaleOffer o=sales.get(k);Plot p=plots.get(k);
        return buyer!=null&&o!=null&&p!=null&&!buyer.equals(o.seller())
                &&o.seller().equals(plotOwner(dimension,x,z))
                &&p.tenant()==null&&!leases.containsKey(k);
    }
    public boolean completeSale(UUID buyer,String dimension,int x,int z) {
        if(!canBuySale(buyer,dimension,x,z))return false;
        SaleOffer o=sales.get(key(dimension,x,z));
        return registerPaidSale(o.seller(),buyer,dimension,x,z);
    }
    /** Fixed-term prepaid leases. All timestamps are UTC epoch milliseconds. */
    public record LeaseOffer(UUID landlord, long priceCopper, long durationMillis) {}
    public record LeaseContract(UUID tenant, UUID landlord, long paidCopper, long expiresAt) {}
    private final Map<String,LeaseOffer> offers = new LinkedHashMap<>();
    private final Map<String,LeaseContract> leases = new LinkedHashMap<>();
    public LeaseOffer leaseOffer(String dimension,int x,int z) { return offers.get(key(dimension,x,z)); }
    public LeaseContract leaseContract(String dimension,int x,int z) { return leases.get(key(dimension,x,z)); }
    public boolean offerLease(UUID mayor,String dimension,int x,int z,long priceCopper,long durationMillis) {
        Plot p=plots.get(key(dimension,x,z));
        if(p==null||!mayor.equals(plotOwner(dimension,x,z))||p.tenant()!=null||priceCopper<1||priceCopper>1000000L||durationMillis<3600000L||durationMillis>2592000000L)return false;
        if(sales.containsKey(key(dimension,x,z)))return false;
        offers.put(key(dimension,x,z),new LeaseOffer(mayor,priceCopper,durationMillis));setDirty();return true;
    }
    /** Side-effect-free validation before receiving physical cash at a bank counter. */
    public boolean canAcceptLease(UUID tenant,String dimension,int x,int z,long now) {
        String k=key(dimension,x,z); Plot p=plots.get(k); LeaseOffer offer=offers.get(k);
        return tenant!=null && offer!=null && p!=null && !leases.containsKey(k) && !leaseDebts.containsKey(k)
                && offer.landlord().equals(plotOwner(dimension,x,z)) && !tenant.equals(offer.landlord())
                && p.tenant()==null && now<=Long.MAX_VALUE-offer.durationMillis();
    }
    public boolean canRenewLease(UUID tenant,String dimension,int x,int z,long now) {
        String k=key(dimension,x,z); Plot p=plots.get(k); LeaseContract c=leases.get(k);
        return tenant!=null && c!=null && p!=null && tenant.equals(c.tenant())
                && tenant.equals(p.tenant()) && now<c.expiresAt()
                && c.expiresAt()<=Long.MAX_VALUE-7L*24*60*60*1000;
    }
    /** Call only after successful physical wallet-to-wallet payment. */
    public boolean acceptLease(UUID tenant,String dimension,int x,int z,long now) {
        String k=key(dimension,x,z);Plot p=plots.get(k);LeaseOffer offer=offers.get(k);
        if(tenant==null||p==null||offer==null||leases.containsKey(k)||leaseDebts.containsKey(k)||!offer.landlord().equals(plotOwner(dimension,x,z))||tenant.equals(offer.landlord())||p.tenant()!=null||now>Long.MAX_VALUE-offer.durationMillis())return false;
        plots.put(k,new Plot(p.dimension(),p.x(),p.z(),p.town(),tenant,p.zone(),p.publicUse()));
        leases.put(k,new LeaseContract(tenant,offer.landlord(),offer.priceCopper(),now+offer.durationMillis()));
        offers.remove(k);setDirty();return true;
    }
    /** Renew only a valid, active lease after a successful physical cash payment. */
    public boolean renewLease(UUID tenant,String dimension,int x,int z,long now,long price,long duration) {
        String k=key(dimension,x,z);
        LeaseContract contract=leases.get(k);
        Plot p=plots.get(k);
        if(contract==null||p==null||tenant==null||!tenant.equals(contract.tenant())
                ||!tenant.equals(p.tenant())||now>=contract.expiresAt()
                ||price!=contract.paidCopper()||duration<=0||duration>2592000000L
                ||contract.expiresAt()>Long.MAX_VALUE-duration)return false;
        leases.put(k,new LeaseContract(tenant,contract.landlord(),price,contract.expiresAt()+duration));
        setDirty();return true;
    }
    /** Voluntary termination. The prepaid period is not refunded automatically. */
    public boolean surrenderLease(UUID tenant,String dimension,int x,int z) {
        String k=key(dimension,x,z);
        LeaseContract contract=leases.get(k);
        Plot p=plots.get(k);
        if(contract==null||p==null||tenant==null||!tenant.equals(contract.tenant())
                ||!tenant.equals(p.tenant()))return false;
        plots.put(k,new Plot(p.dimension(),p.x(),p.z(),p.town(),null,p.zone(),p.publicUse()));
        leases.remove(k);clearWorkers(k);setDirty();return true;
    }
    public boolean activeTenant(UUID actor,String dimension,int x,int z,long now) {
        String k=key(dimension,x,z);Plot p=plots.get(k);if(p==null||!actor.equals(p.tenant()))return false;
        LeaseContract contract=leases.get(k);
        return contract!=null&&contract.tenant().equals(actor)&&now<contract.expiresAt();
    }
    public boolean clearExpiredLease(String dimension,int x,int z,long now) {
        String k=key(dimension,x,z);LeaseContract contract=leases.get(k);
        if(contract==null||now<contract.expiresAt())return false;
        Plot p=plots.get(k);
        if(p!=null&&contract.tenant().equals(p.tenant()))plots.put(k,new Plot(p.dimension(),p.x(),p.z(),p.town(),null,p.zone(),p.publicUse()));
        assessExpiredLease(dimension,x,z,now);
        leases.remove(k);clearWorkers(k);setDirty();return true;
    }

    /** Periodic server-side lease maintenance, independent of any player opening the map.
     *  Only land rights and debt records change: no money is moved.
     *  Iteration uses a snapshot because expiry removes entries from leases.
     */
    public int expireDueLeases(long now) {
        int expired=0;
        for (String plotKey : new ArrayList<>(leases.keySet())) {
            LeaseContract contract=leases.get(plotKey);
            if (contract==null || now<contract.expiresAt()) continue;
            Plot plot=plots.get(plotKey);
            if (plot==null) {
                leases.remove(plotKey);
                clearWorkers(plotKey);
                setDirty();
                expired++;
            } else if (clearExpiredLease(plot.dimension(),plot.x(),plot.z(),now)) {
                expired++;
            }
        }
        return expired;
    }

    /** Lease arrears are liabilities, never automatic wallet/bank deductions. */
    public record LeaseDebt(UUID tenant, UUID landlord, long copper, long assessedAt) {}
    private final Map<String,LeaseDebt> leaseDebts = new LinkedHashMap<>();
    public LeaseDebt leaseDebt(String dimension,int x,int z) { return leaseDebts.get(key(dimension,x,z)); }
    /** Explicit assessment, idempotent per contract: no money is moved. */
    public boolean assessExpiredLease(String dimension,int x,int z,long now) {
        String k=key(dimension,x,z);
        LeaseContract c=leases.get(k);
        if(c==null||now<c.expiresAt()||leaseDebts.containsKey(k))return false;
        leaseDebts.put(k,new LeaseDebt(c.tenant(),c.landlord(),c.paidCopper(),now));
        setDirty();return true;
    }
    /** One plot's arrears cannot be erased by signing a new lease. */
    public boolean canPayLeaseDebt(UUID tenant,String dimension,int x,int z,long copper) {
        LeaseDebt d=leaseDebt(dimension,x,z);
        return tenant!=null&&d!=null&&tenant.equals(d.tenant())&&copper>0&&copper<=d.copper();
    }
    /** Record an externally verified physical payment; caller handles the coins. */
    public boolean recordLeaseDebtPayment(UUID tenant,String dimension,int x,int z,long copper) {
        String k=key(dimension,x,z);LeaseDebt d=leaseDebts.get(k);
        if(!canPayLeaseDebt(tenant,dimension,x,z,copper))return false;
        if(copper==d.copper())leaseDebts.remove(k);
        else leaseDebts.put(k,new LeaseDebt(d.tenant(),d.landlord(),d.copper()-copper,d.assessedAt()));
        setDirty();return true;
    }
    /** Scoped, persistent permission grants; never imply ownership or permit resale. */
    public record WorkerGrant(UUID employer, UUID worker, EnumSet<Action> actions) {}
    private final Map<String,Map<UUID,WorkerGrant>> workers = new LinkedHashMap<>();
    /** Only the title holder or an active tenant may hire workers for their plot. */
    public boolean setWorkerAccess(UUID employer, UUID worker, String dimension, int x, int z,
                                   Action action, boolean enabled, long now) {
        if (employer==null||worker==null||action==null||employer.equals(worker)
                ||!plots.containsKey(key(dimension,x,z))) return false;
        boolean owner=employer.equals(plotOwner(dimension,x,z));
        if (!owner&&!activeTenant(employer,dimension,x,z,now)) return false;
        // The landlord cannot override an active tenant's work permissions.
        if(owner && !activeTenant(employer,dimension,x,z,now)) {
            Plot plot=plots.get(key(dimension,x,z));
            if(plot.tenant()!=null && activeTenant(plot.tenant(),dimension,x,z,now))return false;
        }
        String k=key(dimension,x,z);
        Map<UUID,WorkerGrant> roster=workers.computeIfAbsent(k,ignored->new LinkedHashMap<>());
        WorkerGrant prior=roster.get(worker);
        if (prior!=null&&!prior.employer().equals(employer)) return false;
        EnumSet<Action> rights=prior==null?EnumSet.noneOf(Action.class):EnumSet.copyOf(prior.actions());
        if (enabled) rights.add(action); else rights.remove(action);
        if (rights.isEmpty()) roster.remove(worker);
        else roster.put(worker,new WorkerGrant(employer,worker,rights));
        if (roster.isEmpty()) workers.remove(k);
        setDirty();return true;
    }
    public boolean workerAllowed(UUID actor,String dimension,int x,int z,Action action,long now) {
        Map<UUID,WorkerGrant> roster=workers.get(key(dimension,x,z));
        WorkerGrant grant=roster==null?null:roster.get(actor);
        if(grant==null||!grant.actions().contains(action))return false;
        Plot plot=plots.get(key(dimension,x,z));
        if(plot==null)return false;
        if(plot.tenant()!=null&&activeTenant(plot.tenant(),dimension,x,z,now))
            return grant.employer().equals(plot.tenant());
        return grant.employer().equals(plotOwner(dimension,x,z));
    }
    public boolean workerHasGrant(UUID actor,String dimension,int x,int z,Action action) {
        Map<UUID,WorkerGrant> roster=workers.get(key(dimension,x,z));
        WorkerGrant grant=roster==null?null:roster.get(actor);
        return grant!=null&&workerAllowed(actor,dimension,x,z,action,System.currentTimeMillis());
    }
    private void clearWorkers(String k) { if(workers.remove(k)!=null)setDirty(); }

    private final Map<UUID,UUID> memberships = new LinkedHashMap<>();
    private static String key(String dim,int x,int z) { return dim+"|"+x+"|"+z; }
    private static String dim(ServerLevel level) { return level.dimension().location().toString(); }
    public static KingdomCivicsData get(MinecraftServer server) {
        KingdomCivicsData data=server.overworld().getDataStorage().computeIfAbsent(KingdomCivicsData::load, KingdomCivicsData::new, KEY);
        data.runtimeServer=server;return data;
    }
    public Collection<Town> towns() { return Collections.unmodifiableCollection(towns.values()); }
    /** Read-only source of truth for map integrations. Never mutate claims from a map renderer. */
    public Collection<Plot> mapPlots(String dimension) {
        List<Plot> result = new ArrayList<>();
        for (Plot p : plots.values()) if (p.dimension().equals(dimension)) result.add(p);
        return Collections.unmodifiableList(result);
    }
    public Town townById(UUID id) { return towns.get(id); }
    public Town townAt(ServerLevel level,BlockPos pos) {
        Plot claimed=plot(level,pos);return claimed==null?null:towns.get(claimed.town());
    }
    public boolean isMember(UUID townId,UUID playerId) {
        return townId!=null&&playerId!=null&&townId.equals(memberships.get(playerId));
    }
    public List<UUID> townMembers(UUID townId,int page,int pageSize) {
        if(townId==null||page<0||pageSize<1)return List.of();
        int bounded=Math.min(pageSize,32);List<UUID> result=new ArrayList<>();
        for(var entry:memberships.entrySet())if(townId.equals(entry.getValue()))result.add(entry.getKey());
        result.sort(Comparator.comparing(UUID::toString));int from=(int)Math.min(result.size(),(long)page*bounded);
        return List.copyOf(result.subList(from,Math.min(result.size(),from+bounded)));
    }
    public List<Plot> townPlots(UUID townId,int page,int pageSize) {
        if(townId==null||page<0||pageSize<1)return List.of();
        int bounded=Math.min(pageSize,32);List<Plot> result=new ArrayList<>();
        for(Plot plot:plots.values())if(townId.equals(plot.town()))result.add(plot);
        result.sort(Comparator.comparing(Plot::dimension).thenComparingInt(Plot::x).thenComparingInt(Plot::z));
        int from=(int)Math.min(result.size(),(long)page*bounded);return List.copyOf(result.subList(from,Math.min(result.size(),from+bounded)));
    }
    public boolean replaceMayorAuthorized(UUID townId,UUID actor,UUID replacement) {
        Town town=towns.get(townId);if(town==null||runtimeServer==null||replacement==null||!isMember(townId,replacement))return false;
        KingdomAdministrationData administration=KingdomAdministrationData.get(runtimeServer);
        if(!AdministrativePermissionService.mayPerformFounderOnly(actor,administration.founder(townId)))return false;
        if(replacement.equals(town.mayor()))return false;
        towns.put(town.id(),new Town(town.id(),town.name(),replacement,town.nation(),town.treasury()));
        administration.appoint(townId,actor,town.mayor(),replacement,MunicipalRole.MAYOR);setDirty();return true;
    }
    public boolean landOfficeSetZone(UUID townId,UUID actor,String dimension,int x,int z,Zone zone) {
        Plot plot=plots.get(key(dimension,x,z));
        if(plot==null||!townId.equals(plot.town())||zone==null||!mayManageLand(townId,actor))return false;
        plots.put(key(dimension,x,z),new Plot(plot.dimension(),plot.x(),plot.z(),plot.town(),plot.tenant(),zone,plot.publicUse()));setDirty();return true;
    }
    public boolean landOfficeSetPublicUse(UUID townId,UUID actor,String dimension,int x,int z,boolean allow) {
        Plot plot=plots.get(key(dimension,x,z));
        if(plot==null||!townId.equals(plot.town())||!mayManageLand(townId,actor))return false;
        plots.put(key(dimension,x,z),new Plot(plot.dimension(),plot.x(),plot.z(),plot.town(),plot.tenant(),plot.zone(),allow));setDirty();return true;
    }
    private boolean mayManageLand(UUID townId,UUID actor) {
        Town town=towns.get(townId);if(town==null||runtimeServer==null)return false;
        KingdomAdministrationData administration=KingdomAdministrationData.get(runtimeServer);
        return AdministrativePermissionService.mayManage(actor,administration.founder(townId),town.mayor(),administration.roles(townId,actor),AdministrativeKind.LAND_OFFICE);
    }

    /**
     * Operator-only service entry points. Commands enforce OP level 4 plus
     * persisted admin mode. These methods do not grant command permissions.
     */
    public boolean adminSetMayor(UUID townId,UUID npcUuid){
        Town town=towns.get(townId);
        if(town==null||npcUuid==null||runtimeServer==null)return false;
        UUID membership=memberships.get(npcUuid);
        if(membership!=null && !townId.equals(membership))return false;
        if(npcUuid.equals(town.mayor()))return true;
        // Changing the mayor must not silently transfer historical freeholds.
        for(Plot plot:plots.values())
            if(townId.equals(plot.town())) {
                String k=key(plot.dimension(),plot.x(),plot.z());
                plotOwners.putIfAbsent(k,town.mayor());
            }
        towns.put(townId,new Town(town.id(),town.name(),npcUuid,town.nation(),town.treasury()));
        memberships.put(npcUuid,townId);
        KingdomAdministrationData.get(runtimeServer).adminChangeMayor(townId,town.mayor(),npcUuid);
        setDirty();return true;
    }

    public boolean adminSetFounder(UUID townId,UUID npcUuid){
        Town town=towns.get(townId);
        if(town==null||npcUuid==null||runtimeServer==null)return false;
        UUID member=memberships.get(npcUuid);
        if(member!=null&&!townId.equals(member))return false;
        var administration=KingdomAdministrationData.get(runtimeServer);
        if(!administration.adminChangeFounder(townId,npcUuid))return false;
        memberships.put(npcUuid,townId);
        setDirty();return true;
    }

    public boolean adminSetRuler(UUID nationId,UUID npcUuid){
        Nation nation=nations.get(nationId);
        if(nation==null||npcUuid==null)return false;
        // A ruler must belong to the kingdom through an existing city.
        Town home=townOf(npcUuid);
        if(home==null||!nationId.equals(home.nation()))return false;
        nations.put(nationId,new Nation(nation.id(),nation.name(),npcUuid,nation.treasury()));
        setDirty();return true;
    }

    public Town adminFoundTown(UUID npcOwner,String name){
        if(name==null || towns.values().stream().anyMatch(t->t.name().equalsIgnoreCase(name)))
            return null;
        Town created=foundTown(npcOwner,name);
        if(created!=null&&runtimeServer!=null)
            KingdomAdministrationData.get(runtimeServer).ensureFounder(created.id(),npcOwner);
        return created;
    }
    public Nation adminFoundNation(UUID npcRuler,String name){
        if(name==null || nations.values().stream().anyMatch(n->n.name().equalsIgnoreCase(name)))
            return null;
        return foundNation(npcRuler,name);
    }
    public boolean adminClaimTown(UUID townId,String dimension,int x,int z){
        Town town=towns.get(townId);
        String k=key(dimension,x,z);
        if(town==null||runtimeServer==null||plots.containsKey(k))return false;
        var territory=KingdomTerritories.get(runtimeServer);
        // A personal/foreign legacy claim is not forcibly overridden.
        var already=territory.legacyClaimOwner(dimension,x,z);
        if(already!=null && !already.equals(town.mayor()))return false;
        if(!territory.claimByChunk(dimension,x,z,town.mayor()))return false;
        plots.put(k,new Plot(dimension,x,z,townId,null,Zone.PUBLIC,false));
        setDirty();return true;
    }

    public Nation nationById(UUID id){return nations.get(id);}

    public String adminTownDeletionBlocker(UUID townId){
        Town town=towns.get(townId);
        if(town==null)return "Город не найден.";
        if(town.treasury()!=0)return "Казна города не пуста. Деньги нельзя уничтожать.";
        if(runtimeServer==null)return "Хранилище сервера недоступно.";
        var administration=KingdomAdministrationData.get(runtimeServer);
        String reason=administration.adminRetirementBlocker(townId);
        if(reason!=null)return reason;
        // Personal land titles, leases and debts must be settled rather than lost.
        UUID initialOwner=administration.founder(townId);
        var legacy=KingdomTerritories.get(runtimeServer);
        for(var e:plots.entrySet()){
            if(!townId.equals(e.getValue().town()))continue;
            String k=e.getKey();
            if(leases.containsKey(k)||offers.containsKey(k)||leaseDebts.containsKey(k)
                    ||sales.containsKey(k)||e.getValue().tenant()!=null
                    ||plotOwners.containsKey(k))
                return "Есть арендованные/проданные участки или договоры. Сначала закрой земельные обязательства.";
            Plot plot=e.getValue();
            UUID old=legacy.legacyClaimOwner(plot.dimension(),plot.x(),plot.z());
            if(old!=null&&!old.equals(town.mayor())&&!old.equals(initialOwner))
                return "Участок защищён старым реестром на другого владельца; сначала урегулируй права.";
        }
        return null;
    }

    public boolean adminDeleteTown(UUID id){
        Town town=towns.get(id);
        if(town==null||adminTownDeletionBlocker(id)!=null)return false;
        var admin=KingdomAdministrationData.get(runtimeServer);
        // Remove administrative records while the city's metadata still exists.
        UUID previousFounder=admin.founder(id);
        if(!admin.adminRetireTown(id))return false;
        var territory=KingdomTerritories.get(runtimeServer);
        var obsolete=new ArrayList<>(plots.entrySet());
        for(var entry:obsolete){
            Plot plot=entry.getValue();
            if(!id.equals(plot.town()))continue;
            // Matching only: never unclaim land currently owned by somebody else.
            territory.adminReleaseClaim(plot.dimension(),plot.x(),plot.z(),town.mayor());
            if(previousFounder!=null&&!town.mayor().equals(previousFounder))
                territory.adminReleaseClaim(plot.dimension(),plot.x(),plot.z(),previousFounder);
            plots.remove(entry.getKey());
            plotOwners.remove(entry.getKey());
            sales.remove(entry.getKey());offers.remove(entry.getKey());
            leases.remove(entry.getKey());leaseDebts.remove(entry.getKey());
            workers.remove(entry.getKey());
        }
        memberships.values().removeIf(id::equals);
        expansionReceipts.values().removeIf(r->id.equals(r.townId()));
        towns.remove(id);
        setDirty();return true;
    }

    public String adminNationDeletionBlocker(UUID id){
        Nation nation=nations.get(id);
        if(nation==null)return "Королевство не найдено.";
        if(nation.treasury()!=0)return "Казна королевства не пуста.";
        for(Town town:towns.values())
            if(id.equals(town.nation()))return "В составе королевства ещё есть города.";
        return null;
    }
    public boolean adminDeleteNation(UUID id){
        if(adminNationDeletionBlocker(id)!=null)return false;
        nations.remove(id);alliances.remove(id);
        for(var values:alliances.values())values.remove(id);
        setDirty();return true;
    }

    public Collection<Nation> nations() { return Collections.unmodifiableCollection(nations.values()); }
    public Town townOf(UUID player) { return towns.get(memberships.get(player)); }
    public Nation nationOf(UUID player) { Town t=townOf(player);return t==null?null:nations.get(t.nation()); }
    public Plot plot(ServerLevel level,BlockPos pos) { return plots.get(key(dim(level),pos.getX()>>4,pos.getZ()>>4)); }
    public Town foundTown(UUID mayor,String name) {
        if(mayor==null || townOf(mayor)!=null || name==null || name.isBlank() || name.length()>48)return null;
        Town t=new Town(UUID.randomUUID(),name,mayor,null,0);towns.put(t.id(),t);memberships.put(mayor,t.id());setDirty();return t;
    }
    /**
     * Repair ONLY an older menu-created empty town that never received land.
     * Preserve the Town UUID, mayor, nation and treasury. Never overwrite a
     * populated/claimed town or silently create a second settlement.
     * Caller has already checked the authoritative and legacy chunk claims.
     */
    public boolean bootstrapEmptyLegacyTown(UUID actor,String title,String dim,int cx,int cz){
        Town town=townOf(actor);
        if(town==null||actor==null||!actor.equals(town.mayor())
                ||title==null||title.isBlank()||title.length()>32
                ||dim==null||dim.isBlank())return false;
        for(Town other:towns.values())
            if(!other.id().equals(town.id())&&other.name().equalsIgnoreCase(title))return false;
        for(Plot p:plots.values())if(town.id().equals(p.town()))return false;
        // A populated city is never silently converted into a new founding.
        if(memberships.values().stream().filter(town.id()::equals).count()!=1)return false;
        String plotKey=key(dim,cx,cz);
        if(plots.containsKey(plotKey))return false;
        towns.put(town.id(),new Town(town.id(),title,town.mayor(),town.nation(),town.treasury()));
        plots.put(plotKey,new Plot(dim,cx,cz,town.id(),null,Zone.PUBLIC,false));
        setDirty();return true;
    }

    public boolean joinTown(UUID player,UUID townId) {
        if(player==null||memberships.containsKey(player)||!towns.containsKey(townId))return false;
        memberships.put(player,townId);setDirty();return true;
    }
    public Nation foundNation(UUID ruler,String name) {
        Town t=townOf(ruler);
        if(t==null||!t.mayor().equals(ruler)||t.nation()!=null||name==null||name.isBlank()||name.length()>48)return null;
        Nation n=new Nation(UUID.randomUUID(),name,ruler,0);nations.put(n.id(),n);
        towns.put(t.id(),new Town(t.id(),t.name(),t.mayor(),n.id(),t.treasury()));setDirty();return n;
    }
    public boolean joinNation(UUID mayor,UUID nationId) {
        Town t=townOf(mayor);
        if(t==null||!t.mayor().equals(mayor)||t.nation()!=null||!nations.containsKey(nationId))return false;
        towns.put(t.id(),new Town(t.id(),t.name(),t.mayor(),nationId,t.treasury()));setDirty();return true;
    }
    /** Must also acquire the old KingdomTerritories claim before invoking this. */
    public boolean claim(UUID mayor,String dimension,int chunkX,int chunkZ) {
        Town t=townOf(mayor);String k=key(dimension,chunkX,chunkZ);
        if(t==null||!t.mayor().equals(mayor))return false;
        Plot existing=plots.get(k);
        if(existing!=null)return existing.town().equals(t.id()); // repeated claim is not a conflict
        plots.put(k,new Plot(dimension,chunkX,chunkZ,t.id(),null,Zone.PUBLIC,false));setDirty();return true;
    }
    /**
     * Commits the city treasury balance and ALL territory plots into one SavedData.
     * The matching treasury reserve debit must have succeeded before this call.
     * Never call from the client thread or without authenticated founder checks.
     */
    boolean commitPaidExpansion(UUID receiptId, UUID cityId, String dimension,
                                Collection<KingdomExpansionPlans.Cell> cells, long amount) {
        if(receiptId==null||cityId==null||dimension==null||cells==null
                ||expansionReceipts.containsKey(receiptId))return false;
        Town town=towns.get(cityId);
        if(town==null)return false;
        var quote=KingdomExpansionQuote.evaluate(cityId,dimension,cells,mapPlots(dimension),town.treasury());
        if(quote.status()!=KingdomExpansionQuote.Status.READY||quote.price()!=amount)return false;
        // All preconditions checked before changing either plots or the balance.
        for(var cell:cells) {
            String plotKey=key(cell.dimension(),cell.x(),cell.z());
            plots.put(plotKey,new Plot(cell.dimension(),cell.x(),cell.z(),cityId,null,Zone.PUBLIC,false));
        }
        towns.put(cityId,new Town(town.id(),town.name(),town.mayor(),town.nation(),town.treasury()-amount));
        expansionReceipts.put(receiptId,new ExpansionReceipt(receiptId,cityId,amount,dimension,cells.size()));
        setDirty();return true;
    }
    public boolean zone(UUID mayor,String dimension,int chunkX,int chunkZ,Zone zone) {
        String k=key(dimension,chunkX,chunkZ);Plot p=plots.get(k);Town t=p==null?null:towns.get(p.town());
        if(t==null||!t.mayor().equals(mayor)||zone==null)return false;
        plots.put(k,new Plot(p.dimension(),p.x(),p.z(),p.town(),p.tenant(),zone,p.publicUse()));setDirty();return true;
    }
    public boolean setTenant(UUID mayor,String dimension,int chunkX,int chunkZ,UUID tenant) {
        String k=key(dimension,chunkX,chunkZ);Plot p=plots.get(k);Town t=p==null?null:towns.get(p.town());
        if(t==null||!t.mayor().equals(mayor))return false;
        plots.put(k,new Plot(p.dimension(),p.x(),p.z(),p.town(),tenant,p.zone(),p.publicUse()));offers.remove(k);leases.remove(k);clearWorkers(k);setDirty();return true;
    }
    public boolean setPublicUse(UUID mayor,String dimension,int chunkX,int chunkZ,boolean allow) {
        String k=key(dimension,chunkX,chunkZ);Plot p=plots.get(k);Town t=p==null?null:towns.get(p.town());
        if(t==null||!t.mayor().equals(mayor))return false;
        plots.put(k,new Plot(p.dimension(),p.x(),p.z(),p.town(),p.tenant(),p.zone(),allow));setDirty();return true;
    }
    /** Atomic town treasury transaction: caller must debit a real inventory or other backing asset first. */
    /** Municipal land revenue: credit only after coins physically entered the city's
     * registered treasury vault. NOT an API for issuing money or player deposits. */
    public boolean creditTownLandRevenue(UUID city,long amount) {
        Town town=towns.get(city);
        if(town==null||runtimeServer==null||amount<=0||amount>1_000_000
                ||town.treasury()>Long.MAX_VALUE-amount)return false;
        var admin=KingdomAdministrationData.get(runtimeServer);
        if(!admin.depositTreasuryReserve(city,amount))return false;
        towns.put(city,new Town(town.id(),town.name(),town.mayor(),town.nation(),
                town.treasury()+amount));
        setDirty();return true;
    }
    /** Bank counter credits only after physical deposit or an explicit backed receipt transfer. */
    public boolean creditTownBankFunds(UUID city,long amount) {
        return creditTownLandRevenue(city,amount);
    }
    public boolean depositTown(UUID actor,long amount) {
        Town t=townOf(actor);
        return t!=null&&depositTownTo(t.id(),actor,amount);
    }
    public boolean depositTownTo(UUID townId,UUID actor,long amount) {
        Town t=towns.get(townId);
        if(t==null||!isMember(townId,actor)||amount<=0||amount>1_000_000||t.treasury()>Long.MAX_VALUE-amount||runtimeServer==null)return false;
        KingdomAdministrationData administration=KingdomAdministrationData.get(runtimeServer);
        if(!administration.depositTreasuryReserve(townId,amount))return false;
        towns.put(t.id(),new Town(t.id(),t.name(),t.mayor(),t.nation(),t.treasury()+amount));setDirty();return true;
    }
    /** Server-side payroll debit for a verified member of the exact city. */
    public boolean payTownMember(UUID townId, UUID member, long amount) {
        Town t=towns.get(townId);
        if(t==null||!townId.equals(memberships.get(member))||amount<=0||amount>10000||t.treasury()<amount||runtimeServer==null)return false;
        if(!KingdomAdministrationData.get(runtimeServer).withdrawTreasuryReserve(townId,amount))return false;
        towns.put(t.id(),new Town(t.id(),t.name(),t.mayor(),t.nation(),t.treasury()-amount));setDirty();return true;
    }
    /** Payment of previously verified payroll liabilities, including to former residents.
     * Callers must validate their own authoritative debt registry before this method.
     */
    public boolean payTownObligation(UUID townId,long amount) {
        Town t=towns.get(townId);
        if(t==null || amount<=0 || amount>10000 || t.treasury()<amount||runtimeServer==null)return false;
        if(!KingdomAdministrationData.get(runtimeServer).withdrawTreasuryReserve(townId,amount))return false;
        towns.put(t.id(),new Town(t.id(),t.name(),t.mayor(),t.nation(),t.treasury()-amount));setDirty();return true;
    }
    /** Deposit of a previously verified historical tax debt to its ORIGINAL city. */
    public boolean depositVerifiedTax(UUID townId,long amount) {
        Town t=towns.get(townId);
        if(t==null || amount<=0 || amount>1000000 || t.treasury()>Long.MAX_VALUE-amount||runtimeServer==null)return false;
        if(!KingdomAdministrationData.get(runtimeServer).depositTreasuryReserve(townId,amount))return false;
        towns.put(t.id(),new Town(t.id(),t.name(),t.mayor(),t.nation(),t.treasury()+amount));setDirty();return true;
    }
    /** All citizens, for periodic tax accrual independent of UI opening. */
    public Set<UUID> residents(){return Collections.unmodifiableSet(memberships.keySet());}
    /** Compensation for a failed wallet credit; called only after successful treasury debit. */
    public boolean refundTown(UUID townId,long amount) {
        Town t=towns.get(townId);
        if(t==null||amount<=0||amount>10000||t.treasury()>Long.MAX_VALUE-amount||runtimeServer==null)return false;
        if(!KingdomAdministrationData.get(runtimeServer).depositTreasuryReserve(townId,amount))return false;
        towns.put(t.id(),new Town(t.id(),t.name(),t.mayor(),t.nation(),t.treasury()+amount));setDirty();return true;
    }
    /** Transfers existing funds only; never mints currency. */
    /** Mayor-authorized payout; a real receiving wallet must be credited by the caller. */
    public boolean withdrawTown(UUID mayor,long amount) {
        Town t=townOf(mayor);
        return t!=null&&withdrawTownAuthorized(t.id(),mayor,amount);
    }
    public boolean withdrawTownAuthorized(UUID townId,UUID actor,long amount) {
        Town t=towns.get(townId);
        if(t==null||amount<=0||amount>1_000_000||t.treasury()<amount||runtimeServer==null)return false;
        KingdomAdministrationData administration=KingdomAdministrationData.get(runtimeServer);
        if(!AdministrativePermissionService.mayManage(actor,administration.founder(townId),t.mayor(),administration.roles(townId,actor),AdministrativeKind.TREASURY)
                ||!administration.withdrawTreasuryReserve(townId,amount))return false;
        towns.put(t.id(),new Town(t.id(),t.name(),t.mayor(),t.nation(),t.treasury()-amount));setDirty();return true;
    }
    /** Transfers funds to the town treasury from a member; wallet debit is performed by the caller. */
    public boolean collectTax(UUID member,long amount) {return depositTown(member,amount);}
    public boolean townPaysNation(UUID mayor,long amount) {
        Town t=townOf(mayor);
        if(t==null||!t.mayor().equals(mayor)||t.nation()==null||amount<=0||t.treasury()<amount||runtimeServer==null)return false;
        Nation n=nations.get(t.nation());if(n==null||n.treasury()>Long.MAX_VALUE-amount)return false;
        if(!KingdomAdministrationData.get(runtimeServer).withdrawTreasuryReserve(t.id(),amount))return false;
        towns.put(t.id(),new Town(t.id(),t.name(),t.mayor(),t.nation(),t.treasury()-amount));
        nations.put(n.id(),new Nation(n.id(),n.name(),n.ruler(),n.treasury()+amount));setDirty();return true;
    }
    public boolean ally(UUID ruler,UUID otherNation) {
        Nation n=nationOf(ruler),other=nations.get(otherNation);
        if(n==null||other==null||n.id().equals(other.id())||!n.ruler().equals(ruler))return false;
        alliances.computeIfAbsent(n.id(),k->new LinkedHashSet<>()).add(other.id());setDirty();return true;
    }
    public Relation relation(UUID actor,Plot plot) {
        Town town=towns.get(plot.town());if(town==null)return Relation.OUTSIDER;
        UUID member=memberships.get(actor);
        if(town.id().equals(member))return Relation.RESIDENT;
        Town visitor=towns.get(member);
        if(visitor!=null&&town.nation()!=null&&town.nation().equals(visitor.nation()))return Relation.NATION;
        if(visitor!=null&&town.nation()!=null&&visitor.nation()!=null && alliances.getOrDefault(town.nation(),Set.of()).contains(visitor.nation()))return Relation.ALLY;
        return Relation.OUTSIDER;
    }
    /** Fail closed: never grant build rights on unclaimed land, including NPC architects. */
    public boolean allowed(UUID actor,String dimension,int blockX,int blockZ,Action action) {
        Plot p=plots.get(key(dimension,blockX>>4,blockZ>>4));
        if(actor==null||p==null)return false;
        Town t=towns.get(p.town());if(t==null)return false;
        long now=System.currentTimeMillis();
        if(p.tenant()!=null&&activeTenant(p.tenant(),dimension,blockX>>4,blockZ>>4,now)) {
            if(actor.equals(p.tenant()))return true;
            return workerAllowed(actor,dimension,blockX>>4,blockZ>>4,action,now)
                    ||action==Action.USE&&p.publicUse();
        }
        if(actor.equals(plotOwner(dimension,blockX>>4,blockZ>>4)))return true;
        return workerAllowed(actor,dimension,blockX>>4,blockZ>>4,action,System.currentTimeMillis())
                || action==Action.USE && p.publicUse();
    }
    private static KingdomCivicsData load(CompoundTag root) {
        KingdomCivicsData d=new KingdomCivicsData();
        ListTag ts=root.getList("Towns",Tag.TAG_COMPOUND);
        for(int i=0;i<ts.size();i++){CompoundTag r=ts.getCompound(i);if(!r.hasUUID("Id")||!r.hasUUID("Mayor"))continue;
            UUID n=r.hasUUID("Nation")?r.getUUID("Nation"):null;Town t=new Town(r.getUUID("Id"),r.getString("Name"),r.getUUID("Mayor"),n,r.getLong("Treasury"));d.towns.put(t.id(),t);}
        ListTag ns=root.getList("Nations",Tag.TAG_COMPOUND);
        for(int i=0;i<ns.size();i++){CompoundTag r=ns.getCompound(i);if(!r.hasUUID("Id")||!r.hasUUID("Ruler"))continue;
            Nation n=new Nation(r.getUUID("Id"),r.getString("Name"),r.getUUID("Ruler"),r.getLong("Treasury"));d.nations.put(n.id(),n);}
        ListTag ms=root.getList("Members",Tag.TAG_COMPOUND);
        for(int i=0;i<ms.size();i++){CompoundTag r=ms.getCompound(i);if(r.hasUUID("Player")&&r.hasUUID("Town"))d.memberships.put(r.getUUID("Player"),r.getUUID("Town"));}
        ListTag ps=root.getList("Plots",Tag.TAG_COMPOUND);
        for(int i=0;i<ps.size();i++){CompoundTag r=ps.getCompound(i);if(!r.hasUUID("Town"))continue;
            Zone z;try{z=Zone.valueOf(r.getString("Zone"));}catch(IllegalArgumentException e){z=Zone.PUBLIC;}
            UUID tenant=r.hasUUID("Tenant")?r.getUUID("Tenant"):null;
            Plot p=new Plot(r.getString("Dimension"),r.getInt("X"),r.getInt("Z"),r.getUUID("Town"),tenant,z,r.getBoolean("PublicUse"));
            if(d.towns.containsKey(p.town()))d.plots.put(key(p.dimension(),p.x(),p.z()),p);}
        ListTag titles=root.getList("PersonalTitles",Tag.TAG_COMPOUND);
        for(int i=0;i<titles.size();i++){CompoundTag r=titles.getCompound(i);String k=r.getString("Key");
            if(r.hasUUID("Owner")&&d.plots.containsKey(k))d.plotOwners.put(k,r.getUUID("Owner"));}
        ListTag as=root.getList("Alliances",Tag.TAG_COMPOUND);
        for(int i=0;i<as.size();i++){CompoundTag r=as.getCompound(i);
            if(r.hasUUID("Nation")&&r.hasUUID("Ally"))d.alliances.computeIfAbsent(r.getUUID("Nation"),k->new LinkedHashSet<>()).add(r.getUUID("Ally"));}
        ListTag os=root.getList("LeaseOffers",Tag.TAG_COMPOUND);
        for(int i=0;i<os.size();i++){CompoundTag r=os.getCompound(i);if(r.hasUUID("Landlord"))d.offers.put(r.getString("Key"),new LeaseOffer(r.getUUID("Landlord"),r.getLong("Price"),r.getLong("Duration")));}
        ListTag ls=root.getList("LeaseContracts",Tag.TAG_COMPOUND);
        for(int i=0;i<ls.size();i++){CompoundTag r=ls.getCompound(i);if(r.hasUUID("Tenant")&&r.hasUUID("Landlord"))d.leases.put(r.getString("Key"),new LeaseContract(r.getUUID("Tenant"),r.getUUID("Landlord"),r.getLong("Paid"),r.getLong("Expires")));}
        ListTag ss=root.getList("SaleOffers",Tag.TAG_COMPOUND);
        for(int i=0;i<ss.size();i++){CompoundTag r=ss.getCompound(i);String k=r.getString("Key");
            if(r.hasUUID("Seller")&&d.plots.containsKey(k)&&r.getLong("Price")>0)
                d.sales.put(k,new SaleOffer(r.getUUID("Seller"),r.getLong("Price")));}
        ListTag wg=root.getList("WorkerGrants",Tag.TAG_COMPOUND);
        for(int i=0;i<wg.size();i++) {
            CompoundTag r=wg.getCompound(i);
            if(!r.hasUUID("Employer")||!r.hasUUID("Worker"))continue;
            String k=r.getString("Key");
            if(!d.plots.containsKey(k))continue;
            EnumSet<Action> actions=EnumSet.noneOf(Action.class);
            for(Action action:Action.values())if(r.getBoolean("Allow_"+action.name()))actions.add(action);
            if(!actions.isEmpty())d.workers.computeIfAbsent(k,unused->new LinkedHashMap<>())
                .put(r.getUUID("Worker"),new WorkerGrant(r.getUUID("Employer"),r.getUUID("Worker"),actions));
        }
        ListTag debts=root.getList("LeaseDebts",Tag.TAG_COMPOUND);
        for(int i=0;i<debts.size();i++){
            CompoundTag r=debts.getCompound(i);String k=r.getString("Key");
            if(r.hasUUID("Tenant")&&r.hasUUID("Landlord")&&r.getLong("Copper")>0)
                d.leaseDebts.put(k,new LeaseDebt(r.getUUID("Tenant"),r.getUUID("Landlord"),r.getLong("Copper"),r.getLong("AssessedAt")));
        }
        ListTag receipts=root.getList("ExpansionReceipts",Tag.TAG_COMPOUND);
        for(int i=0;i<receipts.size();i++){
            CompoundTag row=receipts.getCompound(i);
            if(!row.hasUUID("Id")||!row.hasUUID("Town")||row.getLong("Copper")<=0)continue;
            var receipt=new ExpansionReceipt(row.getUUID("Id"),row.getUUID("Town"),
                    row.getLong("Copper"),row.getString("Dimension"),row.getInt("Chunks"));
            d.expansionReceipts.putIfAbsent(receipt.id(),receipt);
        }
        return d;
    }
    @Override public CompoundTag save(CompoundTag root) {
        ListTag ts=new ListTag();for(Town t:towns.values()){CompoundTag r=new CompoundTag();r.putUUID("Id",t.id());r.putString("Name",t.name());r.putUUID("Mayor",t.mayor());if(t.nation()!=null)r.putUUID("Nation",t.nation());r.putLong("Treasury",t.treasury());ts.add(r);}root.put("Towns",ts);
        ListTag ns=new ListTag();for(Nation n:nations.values()){CompoundTag r=new CompoundTag();r.putUUID("Id",n.id());r.putString("Name",n.name());r.putUUID("Ruler",n.ruler());r.putLong("Treasury",n.treasury());ns.add(r);}root.put("Nations",ns);
        ListTag ms=new ListTag();for(var e:memberships.entrySet()){CompoundTag r=new CompoundTag();r.putUUID("Player",e.getKey());r.putUUID("Town",e.getValue());ms.add(r);}root.put("Members",ms);
        ListTag ps=new ListTag();for(Plot p:plots.values()){CompoundTag r=new CompoundTag();r.putString("Dimension",p.dimension());r.putInt("X",p.x());r.putInt("Z",p.z());r.putUUID("Town",p.town());if(p.tenant()!=null)r.putUUID("Tenant",p.tenant());r.putString("Zone",p.zone().name());r.putBoolean("PublicUse",p.publicUse());ps.add(r);}root.put("Plots",ps);
        ListTag titles=new ListTag();for(var e:plotOwners.entrySet()){CompoundTag r=new CompoundTag();r.putString("Key",e.getKey());r.putUUID("Owner",e.getValue());titles.add(r);}root.put("PersonalTitles",titles);
        ListTag as=new ListTag();for(var e:alliances.entrySet())for(UUID ally:e.getValue()){
            CompoundTag r=new CompoundTag();r.putUUID("Nation",e.getKey());r.putUUID("Ally",ally);as.add(r);
        }root.put("Alliances",as);
        ListTag os=new ListTag();for(var e:offers.entrySet()){CompoundTag r=new CompoundTag();r.putString("Key",e.getKey());r.putUUID("Landlord",e.getValue().landlord());r.putLong("Price",e.getValue().priceCopper());r.putLong("Duration",e.getValue().durationMillis());os.add(r);}root.put("LeaseOffers",os);
        ListTag ls=new ListTag();for(var e:leases.entrySet()){CompoundTag r=new CompoundTag();r.putString("Key",e.getKey());r.putUUID("Tenant",e.getValue().tenant());r.putUUID("Landlord",e.getValue().landlord());r.putLong("Paid",e.getValue().paidCopper());r.putLong("Expires",e.getValue().expiresAt());ls.add(r);}root.put("LeaseContracts",ls);
        ListTag ss=new ListTag();for(var e:sales.entrySet()){
            CompoundTag r=new CompoundTag();r.putString("Key",e.getKey());r.putUUID("Seller",e.getValue().seller());r.putLong("Price",e.getValue().priceCopper());ss.add(r);
        }root.put("SaleOffers",ss);
        ListTag wg=new ListTag();
        for(var entry:workers.entrySet())for(WorkerGrant grant:entry.getValue().values()) {
            CompoundTag r=new CompoundTag();r.putString("Key",entry.getKey());
            r.putUUID("Employer",grant.employer());r.putUUID("Worker",grant.worker());
            for(Action action:grant.actions())r.putBoolean("Allow_"+action.name(),true);
            wg.add(r);
        }
        root.put("WorkerGrants",wg);
        ListTag debts=new ListTag();for(var e:leaseDebts.entrySet()){
            CompoundTag r=new CompoundTag();r.putString("Key",e.getKey());
            r.putUUID("Tenant",e.getValue().tenant());r.putUUID("Landlord",e.getValue().landlord());
            r.putLong("Copper",e.getValue().copper());r.putLong("AssessedAt",e.getValue().assessedAt());debts.add(r);
        }root.put("LeaseDebts",debts);
        ListTag receipts=new ListTag();
        for(ExpansionReceipt receipt:expansionReceipts.values()){
            CompoundTag row=new CompoundTag();row.putUUID("Id",receipt.id());
            row.putUUID("Town",receipt.townId());row.putLong("Copper",receipt.copper());
            row.putString("Dimension",receipt.dimension());row.putInt("Chunks",receipt.chunks());
            receipts.add(row);
        }
        root.put("ExpansionReceipts",receipts);
        return root;
    }
}
