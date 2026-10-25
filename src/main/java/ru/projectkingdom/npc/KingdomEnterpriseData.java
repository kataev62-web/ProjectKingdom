package ru.projectkingdom.npc;

import java.util.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import ru.projectkingdom.enterprise.EnterpriseActivation;
import ru.projectkingdom.enterprise.EnterpriseCoreBinding;
import ru.projectkingdom.enterprise.EnterpriseKind;
import ru.projectkingdom.enterprise.EnterpriseBindingRole;
import ru.projectkingdom.enterprise.EnterpriseAccessService;

/** Server-thread-owned enterprise accounts, physical counters and trade orders.
 * Legacy cash balances remain backed by coins deposited into wallets; this ledger
 * NEVER mints coins. No invoice may be paid without verified physical delivery.
 */
public final class KingdomEnterpriseData extends SavedData {
    private static final String KEY = "kingdom_enterprises_v1"; // keep old save key
    private static final int MAX_BUSINESSES = 16;
    public record Enterprise(UUID id, UUID owner, String name, long cash) {}
    /** Legacy invoices are read for migration/administration but not payable. */
    public record Invoice(UUID id, UUID seller, UUID buyer, long price, boolean paid) {}
    public record Counter(UUID enterprise, String dimension, BlockPos pos) {}
    public record StorageBinding(UUID enterprise, EnterpriseBindingRole role, String dimension, BlockPos pos) {}
    public record Offer(UUID id, UUID seller, String itemId, long unitPrice, UUID stockWorker) {
        public Offer(UUID id, UUID seller, String itemId, long unitPrice){this(id,seller,itemId,unitPrice,null);}
    }
    public enum TradeState { PENDING, SETTLED, NEEDS_RECONCILIATION }
    public record Trade(UUID id, UUID offer, UUID buyer, UUID seller, int count, long copper, TradeState state) {}

    private final Map<UUID, Enterprise> firms = new LinkedHashMap<>();
    /** Durable audit trail. Closing never erases a business ID or historical trades. */
    public record ClosedBusiness(UUID id,UUID owner,String name,long gameDay) {}
    private final Map<UUID,ClosedBusiness> closedBusinesses=new LinkedHashMap<>();
    public boolean wasClosed(UUID id){return closedBusinesses.containsKey(id);}
    public Collection<ClosedBusiness> closedBusinesses(){return List.copyOf(closedBusinesses.values());}
    public enum ClosureStatus {
        CLOSED, NO_PERMISSION, NOT_FOUND, NO_CORE, NOT_AT_CORE,
        CASH_REMAINING, TRADE_NOT_SETTLED, STAFF_DEBT, ACTIVE_BUILD,
        STORAGE_UNLOADED, STORAGE_NOT_EMPTY, OLD_COUNTER_NOT_EMPTY, NOTICE_BOARD_ERROR, ACTIVE_GUESTS
    }
    public static String closeMessage(ClosureStatus status){
        return switch(status){
            case CLOSED->"Предприятие закрыто. Работники освобождены, объявления сняты, лимит освободился. Можно установить новое ядро предприятия.";
            case NO_PERMISSION->"Закрыть предприятие может только его владелец.";
            case NOT_FOUND->"Это предприятие уже закрыто или удалено.";
            case NO_CORE->"Блок предприятия ещё установлен или занят. Для действующего ядра используйте кнопку в его настройках.";
            case NOT_AT_CORE->"Откройте свой блок предприятия и оставайтесь рядом.";
            case CASH_REMAINING->"В кассе есть учтённые деньги. Сначала снимите их через вкладку «Касса».";
            case TRADE_NOT_SETTLED->"Есть незавершённые торговые операции или недоступен журнал сделок. Закрытие запрещено до сверки.";
            case STAFF_DEBT->"Есть начисленная задолженность работникам. Погасите зарплату перед закрытием.";
            case ACTIVE_BUILD->"К строительной конторе привязана стройка. Сначала завершите её.";
            case STORAGE_UNLOADED->"Не удалось проверить один из складов или старую кассу. Прогрузите чанки и откройте блок снова.";
            case STORAGE_NOT_EMPTY->"В складах или кассе остались предметы. Заберите всё в свой инвентарь.";
            case OLD_COUNTER_NOT_EMPTY->"В старой кассе-прилавке остались предметы. Заберите их.";
            case NOTICE_BOARD_ERROR->"Не удалось удалить объявления предприятия из городского рынка. Проверьте целостность торговой доски.";
            case ACTIVE_GUESTS->"Гостиница или таверна имеет объявления либо действующие договоры. Завершите их перед закрытием.";
        };
    }
    /**
     * Explicit business deregistration. Does NOT destroy stock, employees' claims
     * or coin Items. We require physical inventories empty and every accrued wage paid.
     * Persistent history is retained for audit, while the placed block is returned
     * as an UNBOUND item so it can create a fresh registered enterprise.
     */
    public enum CashMigration { MOVED, READY, NO_OLD_CASHBOX, NO_RIGHTS, CORE_UNAVAILABLE,
        OLD_UNAVAILABLE, UNSUPPORTED_BALANCE, FULL, PARTIAL }
    public static String cashMigrationMessage(CashMigration result){
        return switch(result){
            case MOVED->"Все монеты прежней кассы перенесены внутрь блока предприятия. Старый сундук освобождён.";
            case READY->"Касса уже находится внутри блока. Дополнительный сундук не нужен.";
            case NO_OLD_CASHBOX->"Старая касса не привязана. Пользуйтесь кассой внутри блока предприятия.";
            case NO_RIGHTS->"Перенос кассы может выполнить только владелец предприятия.";
            case CORE_UNAVAILABLE->"Подойдите к своему блоку предприятия и загрузите его чанк.";
            case OLD_UNAVAILABLE->"Старый кассовый сундук не найден или не загружен. Подойдите к нему и повторите.";
            case UNSUPPORTED_BALANCE->"Сумма монет в старой кассе и в блоке не совпадает с учтёнными деньгами предприятия. Нужна сверка; монеты не созданы.";
            case FULL->"Внутренней кассе не хватило места или номиналы не позволяют перенести сумму.";
            case PARTIAL->"Перенесена часть суммы. Нажмите «Перенести монеты» снова.";
        };
    }
    /** Migrate old physical cash without duplicating the ledger or erasing goods.
     * The ledger must equal the combined real coin value before any transfer.
     */
    public CashMigration migrateLegacyCash(ServerPlayer player,UUID business){
        Enterprise firm=firms.get(business);
        if(player==null||firm==null||!mayManage(player.getUUID(),business))return CashMigration.NO_RIGHTS;
        var inner=EnterpriseAccessService.snapshot(player.serverLevel(),this,business,EnterpriseBindingRole.CASHBOX);
        if(inner.status()!=EnterpriseAccessService.Status.BOUND_AVAILABLE||inner.container()==null
                ||inner.position().distSqr(player.blockPosition())>64)return CashMigration.CORE_UNAVAILABLE;
        var old=storageBinding(business,EnterpriseBindingRole.CASHBOX);
        if(old==null)return CashMigration.NO_OLD_CASHBOX;
        if(!old.dimension().equals(player.serverLevel().dimension().location().toString())
                ||!player.serverLevel().hasChunkAt(old.pos()))return CashMigration.OLD_UNAVAILABLE;
        var from=KingdomContainerAccess.resolve(player.serverLevel(),old.pos());
        if(from==null||from==inner.container())return CashMigration.OLD_UNAVAILABLE;
        long source=KingdomPhysicalCash.value(from),inside=KingdomPhysicalCash.value(inner.container());
        if(source<0||inside<0||source>Long.MAX_VALUE-inside || source+inside!=firm.cash())
            return CashMigration.UNSUPPORTED_BALANCE;
        if(source==0){
            storageBindings.get(business).remove(EnterpriseBindingRole.CASHBOX);
            setDirty();return CashMigration.READY;
        }
        long amount=Math.min(source,1_000_000L);
        if(!KingdomPhysicalCash.move(from,inner.container(),amount))return CashMigration.FULL;
        if(source==amount){
            storageBindings.get(business).remove(EnterpriseBindingRole.CASHBOX);
            setDirty();return CashMigration.MOVED;
        }
        setDirty();return CashMigration.PARTIAL;
    }

    public ClosureStatus closeBusiness(ServerPlayer player,UUID business,BlockPos pos){
        if(player==null||business==null)return ClosureStatus.NOT_FOUND;
        Enterprise firm=firms.get(business);
        if(firm==null)return ClosureStatus.NOT_FOUND;
        if(!firm.owner().equals(player.getUUID()))return ClosureStatus.NO_PERMISSION;
        EnterpriseCoreBinding binding=cores.get(business);
        boolean blockPresent=pos!=null;
        ServerLevel level=player.serverLevel();
        if(blockPresent){
            if(binding==null||!binding.active())return ClosureStatus.NO_CORE;
            if(!binding.dimension().equals(level.dimension().location().toString())
                    ||binding.packedPos()!=pos.asLong()||pos.distSqr(player.blockPosition())>64
                    ||!level.hasChunkAt(pos)
                    ||!(level.getBlockEntity(pos) instanceof ru.projectkingdom.enterprise.EnterpriseCoreBlockEntity core)
                    ||!core.bound()||!business.equals(core.enterpriseId()))return ClosureStatus.NOT_AT_CORE;
        }else{
            // No remote closure of an installed core. A previously removed core
            // can be deregistered from the player's main enterprise menu.
            if(binding!=null){
                if(binding.active())return ClosureStatus.NO_CORE;
                ServerLevel former=null;
                for(var candidate:player.getServer().getAllLevels())
                    if(candidate.dimension().location().toString().equals(binding.dimension()))
                        former=candidate;
                if(former==null||!former.hasChunkAt(BlockPos.of(binding.packedPos())))
                    return ClosureStatus.STORAGE_UNLOADED;
                var located=former.getBlockEntity(BlockPos.of(binding.packedPos()));
                if(located instanceof ru.projectkingdom.enterprise.EnterpriseCoreBlockEntity old
                        && old.bound()&&business.equals(old.enterpriseId()))return ClosureStatus.NO_CORE;
            }
        }
        if(ru.projectkingdom.property.KingdomRentalsData.get(player.getServer())
                .receptionHasObligations(business))return ClosureStatus.ACTIVE_GUESTS;
        if(firm.cash()!=0)return ClosureStatus.CASH_REMAINING;
        if(blockPresent && level.getBlockEntity(pos) instanceof ru.projectkingdom.enterprise.EnterpriseCoreBlockEntity live
                && !live.cashInventory().isEmpty())return ClosureStatus.CASH_REMAINING;
        if(unresolved(business))return ClosureStatus.TRADE_NOT_SETTLED;
        var payroll=KingdomEnterprisePayrollData.get(player.getServer());
        var contracts=new ArrayList<KingdomEnterprisePayrollData.Employment>();
        for(var current:payroll.all())if(business.equals(current.enterprise())){
            payroll.accrue(player.getServer(),business,current.worker());
            var updated=payroll.contract(business,current.worker());
            if(updated!=null){
                if(updated.owed()!=0)return ClosureStatus.STAFF_DEBT;
                if(updated.active())contracts.add(updated);
            }
        }
        if(binding!=null && kind(business)==EnterpriseKind.CONSTRUCTION){
            var marker=BlockPos.of(binding.packedPos());
            for(var oldLevel:player.getServer().getAllLevels())
                if(oldLevel.dimension().location().toString().equals(binding.dimension())
                        && KindIsConstructionAndHasSite(oldLevel,business,marker))
                    return ClosureStatus.ACTIVE_BUILD;
        }
        for(EnterpriseBindingRole role:EnterpriseBindingRole.values()){
            var bindingSlot=storageBinding(business,role);
            if(bindingSlot==null)continue;
            if(!bindingSlot.dimension().equals(level.dimension().location().toString())
                    ||!level.hasChunkAt(bindingSlot.pos()))return ClosureStatus.STORAGE_UNLOADED;
            var container=KingdomContainerAccess.resolve(level,bindingSlot.pos());
            if(container==null)return ClosureStatus.STORAGE_UNLOADED;
            if(!container.isEmpty())return ClosureStatus.STORAGE_NOT_EMPTY;
        }
        Counter legacy=counters.get(business);
        if(legacy!=null){
            if(!legacy.dimension().equals(level.dimension().location().toString())
                    ||!level.hasChunkAt(legacy.pos()))return ClosureStatus.STORAGE_UNLOADED;
            var counterInventory=KingdomContainerAccess.resolve(level,legacy.pos());
            if(counterInventory!=null&&!counterInventory.isEmpty())
                return ClosureStatus.OLD_COUNTER_NOT_EMPTY;
        }
        // Cancel the information-only market announcements before closing.
        // A failed persistent write leaves the business active and tradeable.
        try {
            var board=SharedTradeNoticeBoards.get(player.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("data").resolve("kingdom_trade_notices.dat"));
            for(var side:SimpleTradeNoticeBoard.Side.values())
                for(var notice:board.offers(business,side))
                    if(!board.remove(notice.id()))return ClosureStatus.NOTICE_BOARD_ERROR;
        }catch(IOException error){return ClosureStatus.NOTICE_BOARD_ERROR;}
        // All preconditions checked before mutating accounting records.
        for(var contract:contracts)
            if(!payroll.dismiss(player.getServer(),firm.owner(),business,contract.worker()))
                return ClosureStatus.STAFF_DEBT;
        closedBusinesses.put(business,new ClosedBusiness(business,firm.owner(),firm.name(),
                Math.max(0,level.getDayTime()/24000L)));
        offers.values().removeIf(offer->business.equals(offer.seller()));
        selections.entrySet().removeIf(entry->business.equals(entry.getValue()));
        cashiers.remove(business);
        ru.projectkingdom.enterprise.KingdomEnterpriseApplications.get(player.getServer())
            .cancelForClosedEnterprise(business);
        counters.remove(business);
        storageBindings.remove(business);
        cores.remove(business);
        kinds.remove(business);
        firms.remove(business);
        setDirty();
        if(blockPresent){
            // Remove active core and return one fresh unbound core to the owner.
            var item=level.getBlockState(pos).getBlock().asItem();
            level.removeBlock(pos,false);
            var fresh=new ItemStack(item);
            if(!player.getInventory().add(fresh)&&!fresh.isEmpty())
                net.minecraft.world.level.block.Block.popResource(level,pos,fresh);
            player.getInventory().setChanged();
        }
        // If core was dismantled earlier, the original bound item becomes
        // reusable thanks to EnterpriseCoreBlock's closed-ID migration.
        return ClosureStatus.CLOSED;
    }
    private boolean KindIsConstructionAndHasSite(ServerLevel level,UUID firm,BlockPos pos){
        if(kind(firm)!=EnterpriseKind.CONSTRUCTION)return false;
        var site=KingdomConstructionData.get(level.getServer()).atMarker(
                level.dimension().location().toString(),pos.getX(),pos.getY(),pos.getZ());
        return site!=null && site.status()!=KingdomConstructionData.Status.COMPLETED;
    }

    private final Map<UUID, Invoice> legacyInvoices = new LinkedHashMap<>();
    private final Map<UUID, UUID> selections = new LinkedHashMap<>();
    private final Map<UUID, Counter> counters = new LinkedHashMap<>();
    private final Map<UUID, Set<UUID>> cashiers = new LinkedHashMap<>();
    private final Map<UUID, Offer> offers = new LinkedHashMap<>();
    private final Map<UUID, Trade> trades = new LinkedHashMap<>();
    private final Map<UUID, EnterpriseKind> kinds = new LinkedHashMap<>();
    private final Map<UUID, EnterpriseCoreBinding> cores = new LinkedHashMap<>();
    private final Map<UUID, EnumMap<EnterpriseBindingRole,StorageBinding>> storageBindings = new LinkedHashMap<>();
    // A durable intent is authoritative even when SavedData has not yet been flushed.
    private TradeWriteAheadJournal journal;
    private boolean journalUnavailable = true;
    private final Set<UUID> quarantined = new HashSet<>();
    private void connectJournal(MinecraftServer server) {
        if (journal != null && !journalUnavailable) return;
        try {
            TradeWriteAheadJournal candidate = new TradeWriteAheadJournal(
                    server.getWorldPath(LevelResource.ROOT).resolve("data/kingdom_trade_journal"));
            Set<UUID> affected = new HashSet<>();
            // Terminal receipts do not prove that the independent chunk and SavedData
            // writes survived a crash. Recheck them after restart instead of trusting
            // the journal to unlock accounts with potentially rolled-back balances.
            for (TradeWriteAheadJournal.Entry entry : candidate.allEntries()) {
                affected.add(entry.buyer()); affected.add(entry.seller());
                trades.putIfAbsent(entry.id(), new Trade(entry.id(), entry.id(), entry.buyer(),
                        entry.seller(), 0, 0, TradeState.NEEDS_RECONCILIATION));
            }
            for (Trade old : trades.values()) {
                if (old.state()!=TradeState.SETTLED) {
                    affected.add(old.buyer()); affected.add(old.seller());
                }
            }
            quarantined.clear(); quarantined.addAll(affected);
            journal = candidate; journalUnavailable = false;
            if (!affected.isEmpty()) setDirty();
        } catch (IOException | RuntimeException ex) {
            journalUnavailable = true; // Fail closed on unreadable or corrupted journals.
            System.err.println("[Kingdom] Trade journal unavailable; purchases disabled: " + ex);
        }
    }
    private static String snapshotDigest(Enterprise buyer, Enterprise seller,
                                         List<ItemStack> source, List<ItemStack> dest) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((buyer.id()+":"+buyer.cash()+":"+seller.id()+":"+seller.cash()).getBytes(StandardCharsets.UTF_8));
            for (List<ItemStack> stacks : List.of(source,dest)) {
                md.update((byte) 0x7f);
                for (ItemStack stack : stacks) {
                    md.update(stack.save(new CompoundTag()).toString().getBytes(StandardCharsets.UTF_8));
                    md.update((byte) 0);
                }
            }
            return java.util.HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    public static KingdomEnterpriseData get(MinecraftServer server) {
        KingdomEnterpriseData data = server.overworld().getDataStorage().computeIfAbsent(KingdomEnterpriseData::load, KingdomEnterpriseData::new, KEY);
        data.connectJournal(server);
        return data;
    }
    public Collection<Enterprise> all() { return Collections.unmodifiableCollection(firms.values()); }
    public Enterprise find(UUID id) { return firms.get(id); }
    public Collection<Enterprise> ownedAll(UUID owner) {
        return firms.values().stream().filter(f -> f.owner().equals(owner)).toList();
    }
    public Enterprise owned(UUID owner) {
        UUID selected = selections.get(owner);
        Enterprise firm = selected == null ? null : firms.get(selected);
        if(firm != null && firm.owner().equals(owner)) return firm;
        return firms.values().stream().filter(f -> f.owner().equals(owner)).findFirst().orElse(null);
    }
    public boolean select(UUID owner, UUID business) {
        Enterprise firm = firms.get(business);
        if(owner == null || firm == null || !firm.owner().equals(owner)) return false;
        selections.put(owner,business);setDirty();return true;
    }
    public boolean selectNext(UUID owner) {
        List<Enterprise> list = new ArrayList<>(ownedAll(owner));
        if(list.size()<2)return false;
        Enterprise active=owned(owner);
        int index=list.indexOf(active);
        return select(owner,list.get((index+1)%list.size()).id());
    }
    /** Only trusted server code may call this with an NPC UUID. Never accept owner from a client packet. */
    public Enterprise create(UUID owner, String name) {
        return create(owner,name,EnterpriseKind.LEGACY);
    }
    public Enterprise create(UUID owner, String name, EnterpriseKind kind) {
        if(owner==null || name==null || name.isBlank() || name.length()>48 || ownedAll(owner).size()>=MAX_BUSINESSES)return null;
        if(kind==null)return null;
        Enterprise firm=new Enterprise(UUID.randomUUID(),owner,name,0);
        firms.put(firm.id(),firm);kinds.put(firm.id(),kind);selections.put(owner,firm.id());setDirty();return firm;
    }
    public EnterpriseKind kind(UUID enterprise){return kinds.getOrDefault(enterprise,EnterpriseKind.LEGACY);}
    public EnterpriseCoreBinding core(UUID enterprise){return cores.get(enterprise);}
    public boolean mayManage(UUID actor,UUID enterprise){
        Enterprise firm=firms.get(enterprise);return actor!=null&&firm!=null&&firm.owner().equals(actor);
    }
    public EnterpriseActivation activateCore(UUID actor,UUID enterprise,EnterpriseKind kind,String dimension,long packedPos){
        Enterprise firm=firms.get(enterprise);
        if(firm==null)return EnterpriseActivation.NOT_FOUND;
        if(actor==null||!firm.owner().equals(actor))return EnterpriseActivation.FOREIGN_OWNER;
        if(kind==null||kind==EnterpriseKind.LEGACY||kind(enterprise)!=kind)return EnterpriseActivation.TYPE_MISMATCH;
        if(dimension==null||dimension.isBlank())return EnterpriseActivation.INVALID_LOCATION;
        EnterpriseCoreBinding existing=cores.get(enterprise);
        if(existing!=null&&existing.active())return EnterpriseActivation.ALREADY_ACTIVE;
        for(EnterpriseCoreBinding other:cores.values())if(other.active()&&other.dimension().equals(dimension)
                &&other.packedPos()==packedPos&&!other.enterpriseId().equals(enterprise))return EnterpriseActivation.INVALID_LOCATION;
        cores.put(enterprise,new EnterpriseCoreBinding(enterprise,firm.owner(),kind,dimension,packedPos,true));
        setDirty();return EnterpriseActivation.ACTIVATED;
    }
    /**
     * Heal a legacy/stale active marker only when its former chunk is loaded
     * and the matching core truly does not exist. Unloaded sites fail closed.
     */
    public boolean reconcileMissingCore(MinecraftServer server, UUID enterprise) {
        EnterpriseCoreBinding existing=cores.get(enterprise);
        if(server==null || existing==null || !existing.active()) return false;
        ServerLevel level=null;
        for(ServerLevel candidate:server.getAllLevels()) {
            if(candidate.dimension().location().toString().equals(existing.dimension())) {
                level=candidate; break;
            }
        }
        if(level==null)return false;
        BlockPos previous=BlockPos.of(existing.packedPos());
        if(!level.hasChunkAt(previous))return false;
        BlockEntity be=level.getBlockEntity(previous);
        if(be instanceof ru.projectkingdom.enterprise.EnterpriseCoreBlockEntity core
                && core.bound() && enterprise.equals(core.enterpriseId())
                && existing.kind()==core.kind())return false;
        cores.put(enterprise,existing.suspended());
        setDirty();
        return true;
    }
    public boolean suspendCore(UUID actor,UUID enterprise,String dimension,long packedPos){
        EnterpriseCoreBinding existing=cores.get(enterprise);
        if(!mayManage(actor,enterprise)||existing==null||!existing.active()
                ||!existing.dimension().equals(dimension)||existing.packedPos()!=packedPos)return false;
        cores.put(enterprise,existing.suspended());setDirty();return true;
    }
    public boolean adoptLegacy(UUID actor,UUID enterprise,EnterpriseKind target){
        if(!mayManage(actor,enterprise)||target==null||!target.placeable()||kind(enterprise)!=EnterpriseKind.LEGACY)return false;
        EnterpriseCoreBinding existing=cores.get(enterprise);
        if(existing!=null&&existing.active())return false;
        kinds.put(enterprise,target);setDirty();return true;
    }
    public boolean rename(UUID actor,UUID enterprise,String name){
        Enterprise firm=firms.get(enterprise);String clean=name==null?"":name.trim();
        if(!mayManage(actor,enterprise)||clean.isEmpty()||clean.length()>48||clean.indexOf('\u00a7')>=0
                ||clean.chars().anyMatch(Character::isISOControl))return false;
        firms.put(enterprise,new Enterprise(firm.id(),firm.owner(),clean,firm.cash()));setDirty();return true;
    }
    public StorageBinding storageBinding(UUID enterprise,EnterpriseBindingRole role){
        return storageBindings.getOrDefault(enterprise,new EnumMap<>(EnterpriseBindingRole.class)).get(role);
    }
    /** Prevent legacy worker chests being attributed to another firm's warehouse. */
    public boolean storageClaimedByOther(UUID enterprise,String dimension,BlockPos pos){
        for(var roles:storageBindings.values())for(StorageBinding s:roles.values())
            if(!s.enterprise().equals(enterprise)&&s.dimension().equals(dimension)&&s.pos().equals(pos))return true;
        return false;
    }
    public boolean bindStorage(UUID actor,UUID enterprise,EnterpriseBindingRole role,String dimension,BlockPos pos){
        if(!mayManage(actor,enterprise)||role==null||role==EnterpriseBindingRole.CASHBOX
                ||dimension==null||dimension.isBlank()||pos==null)return false;
        for(var byRole:storageBindings.values())for(StorageBinding other:byRole.values())
            if(other.dimension().equals(dimension)&&other.pos().equals(pos)
                    &&(!other.enterprise().equals(enterprise)||other.role()!=role))return false;
        storageBindings.computeIfAbsent(enterprise,key->new EnumMap<>(EnterpriseBindingRole.class))
                .put(role,new StorageBinding(enterprise,role,dimension,pos.immutable()));setDirty();return true;
    }
    public boolean depositAtCore(ServerPlayer actor,UUID enterprise,long amount){
        Enterprise firm=firms.get(enterprise);EnterpriseCoreBinding core=cores.get(enterprise);
        if(firm==null||core==null||!core.active()||!mayManage(actor.getUUID(),enterprise)||amount<=0||amount>1_000_000
                ||firm.cash()>Long.MAX_VALUE-amount)return false;
        var cash=EnterpriseAccessService.snapshot(actor.serverLevel(),this,enterprise,EnterpriseBindingRole.CASHBOX);
        if(cash.status()!=EnterpriseAccessService.Status.BOUND_AVAILABLE
                ||KingdomPhysicalCash.value(cash.container())<firm.cash()
                ||!KingdomPhysicalCash.deposit(actor,cash.container(),amount))return false;
        firms.put(enterprise,new Enterprise(firm.id(),firm.owner(),firm.name(),firm.cash()+amount));setDirty();return true;
    }
    /**
     * A guest pays at the real hotel/tavern reception and into the firm's
     * prebound physical cashbox. Bank accounts are never used for room rent.
     * The full on-server validation is repeated at click time.
     */
    public boolean payHospitalityRoom(ServerPlayer payer,UUID enterprise,UUID roomId,boolean renewal){
        if(payer==null||enterprise==null||roomId==null)return false;
        Enterprise firm=firms.get(enterprise);
        EnterpriseCoreBinding binding=cores.get(enterprise);
        if(firm==null||binding==null||!binding.active()
                ||(kind(enterprise)!=EnterpriseKind.HOTEL&&kind(enterprise)!=EnterpriseKind.TAVERN)
                ||binding.kind()!=kind(enterprise)
                ||!firm.owner().equals(binding.owner()))return false;
        ServerLevel level=payer.serverLevel();
        BlockPos pos=BlockPos.of(binding.packedPos());
        if(!binding.dimension().equals(level.dimension().location().toString())
                ||pos.distSqr(payer.blockPosition())>64||!level.hasChunkAt(pos))return false;
        if(!(level.getBlockEntity(pos) instanceof ru.projectkingdom.enterprise.EnterpriseCoreBlockEntity core)
                ||!core.bound()||!enterprise.equals(core.enterpriseId())
                ||core.kind()!=kind(enterprise)||!firm.owner().equals(core.owner()))return false;
        var rentals=ru.projectkingdom.property.KingdomRentalsData.get(payer.getServer());
        var room=rentals.room(roomId);
        var building=room==null?null:rentals.building(room.buildingId());
        if(room==null||building==null||!enterprise.equals(rentals.receptionForRoom(roomId))
                ||!firm.owner().equals(building.owner())||!building.dimension().equals(binding.dimension())
                ||!building.bounds().contains(pos))return false;
        var deeds=KingdomCivicsData.get(payer.getServer());
        long now=System.currentTimeMillis();
        if(renewal?!rentals.canRenew(deeds,payer.getUUID(),roomId,now)
                  :!rentals.canRent(deeds,payer.getUUID(),roomId,now))return false;
        long price=room.priceCopper();
        if(price<1||price>1_000_000||firm.cash()>Long.MAX_VALUE-price
                ||KingdomCurrency.balance(payer)<price)return false;
        var cash=EnterpriseAccessService.snapshot(level,this,enterprise,EnterpriseBindingRole.CASHBOX);
        if(cash.status()!=EnterpriseAccessService.Status.BOUND_AVAILABLE
                ||KingdomPhysicalCash.value(cash.container())<firm.cash())return false;
        if(!KingdomPhysicalCash.deposit(payer,cash.container(),price))return false;
        boolean updated=renewal?rentals.renewPaid(deeds,payer.getUUID(),roomId,now)
                                :rentals.rentPaid(deeds,payer.getUUID(),roomId,now);
        if(!updated){
            if(!KingdomPhysicalCash.withdraw(payer,cash.container(),price))
                throw new IllegalStateException("Hospitality rent payment refund failed");
            return false;
        }
        firms.put(enterprise,new Enterprise(firm.id(),firm.owner(),firm.name(),firm.cash()+price));
        setDirty();
        payer.getInventory().setChanged();
        return true;
    }

    public boolean withdrawAtCore(ServerPlayer actor,UUID enterprise,long amount){
        Enterprise firm=firms.get(enterprise);EnterpriseCoreBinding core=cores.get(enterprise);
        if(firm==null||core==null||!core.active()||!mayManage(actor.getUUID(),enterprise)||amount<=0||amount>1_000_000
                ||firm.cash()<amount)return false;
        var cash=EnterpriseAccessService.snapshot(actor.serverLevel(),this,enterprise,EnterpriseBindingRole.CASHBOX);
        if(cash.status()!=EnterpriseAccessService.Status.BOUND_AVAILABLE
                ||KingdomPhysicalCash.value(cash.container())<firm.cash()
                ||!KingdomPhysicalCash.withdraw(actor,cash.container(),amount))return false;
        firms.put(enterprise,new Enterprise(firm.id(),firm.owner(),firm.name(),firm.cash()-amount));setDirty();return true;
    }
    public Counter counter(UUID id) { return counters.get(id); }
    /** The owner stands at a real loaded container on land they can legally use. */
    public boolean bindCounter(ServerPlayer actor, BlockPos pos) {
        Enterprise firm=owned(actor.getUUID());
        if(firm==null || pos==null || pos.distSqr(actor.blockPosition())>36)return false;
        ServerLevel level=actor.serverLevel();
        if(!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof Container)
                || !KingdomTerritories.get(actor.getServer()).canUse(level,pos,actor.getUUID()))return false;
        String dim=level.dimension().location().toString();
        for(Counter other:counters.values())if(!other.enterprise().equals(firm.id())
                && other.dimension().equals(dim) && other.pos().equals(pos))return false;
        counters.put(firm.id(),new Counter(firm.id(),dim,pos.immutable()));setDirty();return true;
    }
    public boolean grantCashier(UUID owner, UUID business, UUID employee, boolean enabled) {
        Enterprise firm=firms.get(business);
        if(firm==null || !firm.owner().equals(owner) || employee==null || employee.equals(owner))return false;
        Set<UUID> crew=cashiers.computeIfAbsent(business, k->new LinkedHashSet<>());
        if(enabled)crew.add(employee);else crew.remove(employee);
        if(crew.isEmpty())cashiers.remove(business);
        setDirty();return true;
    }
    public boolean isCashier(UUID actor, UUID business) {
        Enterprise firm=firms.get(business);
        return firm!=null && (firm.owner().equals(actor) || cashiers.getOrDefault(business,Set.of()).contains(actor));
    }
    public boolean atCounter(ServerPlayer player, UUID business) {
        Enterprise firm=firms.get(business);Counter c=counters.get(business);
        if(firm==null)return false;
        var core=EnterpriseAccessService.snapshot(player.serverLevel(),this,business,EnterpriseBindingRole.CASHBOX);
        if(core.status()==EnterpriseAccessService.Status.BOUND_AVAILABLE
                &&core.position().distSqr(player.blockPosition())<=64)return true;
        if(c==null)return false;
        ServerLevel level=player.serverLevel();
        return c.dimension().equals(level.dimension().location().toString())
                && c.pos().distSqr(player.blockPosition())<=36
                && level.hasChunkAt(c.pos()) && level.getBlockEntity(c.pos()) instanceof Container
                && KingdomTerritories.get(player.getServer()).canUse(level,c.pos(),firm.owner());
    }
    /** Ledger funds exist only after wallet debit and are not free money. */
    public boolean deposit(ServerPlayer actor,long amount) {
        Enterprise firm=owned(actor.getUUID());
        if(firm==null || !atCounter(actor,firm.id()) || amount<=0 || amount>1000000 || firm.cash()>Long.MAX_VALUE-amount)return false;
        var cash=EnterpriseAccessService.snapshot(actor.serverLevel(),this,firm.id(),EnterpriseBindingRole.CASHBOX);
        if(cash.status()!=EnterpriseAccessService.Status.BOUND_AVAILABLE
                ||KingdomPhysicalCash.value(cash.container())<firm.cash()
                ||!KingdomPhysicalCash.deposit(actor,cash.container(),amount))return false;
        firms.put(firm.id(),new Enterprise(firm.id(),firm.owner(),firm.name(),firm.cash()+amount));
        actor.getInventory().setChanged();setDirty();return true;
    }
    public boolean withdraw(ServerPlayer actor,long amount) {
        Enterprise firm=owned(actor.getUUID());
        if(firm==null || !atCounter(actor,firm.id()) || amount<=0 || amount>1000000 || firm.cash()<amount)return false;
        var cash=EnterpriseAccessService.snapshot(actor.serverLevel(),this,firm.id(),EnterpriseBindingRole.CASHBOX);
        if(cash.status()!=EnterpriseAccessService.Status.BOUND_AVAILABLE
                ||KingdomPhysicalCash.value(cash.container())<firm.cash()
                ||!KingdomPhysicalCash.withdraw(actor,cash.container(),amount))return false;
        firms.put(firm.id(),new Enterprise(firm.id(),firm.owner(),firm.name(),firm.cash()-amount));
        actor.getInventory().setChanged();setDirty();return true;
    }
    /** Trusted payroll adapter. The payroll subsystem must verify an existing earned debt.
     * Never expose this endpoint directly as an arbitrary-amount player command.
     */
    boolean payVerifiedWage(ServerPlayer employee,UUID firmId,long amount) {
        Enterprise firm=firms.get(firmId);
        if(firm==null || !atCounter(employee,firmId) || amount<1 || amount>10000 || firm.cash()<amount)return false;
        var cash=EnterpriseAccessService.snapshot(employee.serverLevel(),this,firmId,EnterpriseBindingRole.CASHBOX);
        if(cash.status()!=EnterpriseAccessService.Status.BOUND_AVAILABLE
                ||KingdomPhysicalCash.value(cash.container())<firm.cash()
                ||!KingdomPhysicalCash.withdraw(employee,cash.container(),amount))return false;
        firms.put(firmId,new Enterprise(firm.id(),firm.owner(),firm.name(),firm.cash()-amount));
        employee.getInventory().setChanged();setDirty();return true;
    }
    /** Trusted NPC wage payout: funds come only from a funded firm and a physical desk visit.
     * The caller validates earned debt and capacity in the NPC's actual persistent coin purse.
     */
    boolean debitVerifiedNpcWage(ServerLevel level, UUID business, UUID npcId,long amount) {
        Enterprise firm=firms.get(business);
        if(firm==null || amount<=0 || amount>10000 || firm.cash()<amount)return false;
        var cash=EnterpriseAccessService.snapshot(level,this,business,EnterpriseBindingRole.CASHBOX);
        if(cash.status()!=EnterpriseAccessService.Status.BOUND_AVAILABLE)return false;
        var entity=level.getEntity(npcId);
        if(!(entity instanceof net.minecraft.world.entity.npc.Villager npc)||!npc.isAlive()
                ||npc.distanceToSqr(cash.position().getX()+0.5,
                    cash.position().getY()+0.5,cash.position().getZ()+0.5)>9)return false;
        if(KingdomPhysicalCash.value(cash.container())<firm.cash()
                ||!KingdomPhysicalCash.takeForNpc(cash.container(),amount))return false;
        firms.put(business,new Enterprise(firm.id(),firm.owner(),firm.name(),firm.cash()-amount));
        setDirty();return true;
    }
    /** Called only by a completed server-side notice trade after actual coins left
     * the company's private core till. There is no client-direct ledger command.
     */
    public boolean recordPhysicalNoticePayment(ServerLevel level,UUID business,Container till,long amount){
        Enterprise firm=firms.get(business);
        var core=EnterpriseAccessService.snapshot(level,this,business,EnterpriseBindingRole.CASHBOX);
        if(firm==null||amount<1||amount>1_000_000||firm.cash()<amount
                ||core.status()!=EnterpriseAccessService.Status.BOUND_AVAILABLE
                ||core.container()!=till || KingdomPhysicalCash.value(till)!=firm.cash()-amount)
            return false;
        firms.put(business,new Enterprise(firm.id(),firm.owner(),firm.name(),firm.cash()-amount));
        setDirty();return true;
    }
    /** Explicit trade offer by owner; offer is NOT a reservation and is rechecked at purchase. */
    public Offer listItem(ServerPlayer seller, String itemId, long unitPrice) {
        Enterprise firm=owned(seller.getUUID());
        if(firm==null || !atCounter(seller,firm.id()) || unitPrice<1 || unitPrice>100000)return null;
        ResourceLocation id=ResourceLocation.tryParse(itemId);
        if(id==null || !BuiltInRegistries.ITEM.containsKey(id) || stock(seller.serverLevel(),firm.id(),itemId)<1
                || unresolved(firm.id()))return null;
        // One price per item per firm. Replacing does not invalidate the audit history.
        offers.values().removeIf(o->o.seller().equals(firm.id()) && o.itemId().equals(itemId));
        Offer offer=new Offer(UUID.randomUUID(),firm.id(),itemId,unitPrice);
        offers.put(offer.id(),offer);setDirty();return offer;
    }
    /** Publish only actual workshop output in its physically marked finished chest.
     * The employee has already performed a real recipe and reached the workbench.
     * An offer is a listing, NOT guaranteed stock: purchase always rechecks the chest.
     */
    public Offer publishWorkshop(MinecraftServer server,UUID worker,UUID firmId,long unitPrice){
        Enterprise firm=firms.get(firmId);
        EnterpriseCoreBinding core=cores.get(firmId);
        if(firm==null||core==null||!core.active()||core.kind()!=kind(firmId)
                ||worker==null||unitPrice<1||unitPrice>100000)return null;
        var plan=KingdomNpcWorkshopData.get(server).plan(worker);
        if(plan==null||!firmId.equals(plan.enterprise())||unresolved(firmId))return null;
        var employment=KingdomEnterprisePayrollData.get(server).contract(firmId,worker);
        var person=KingdomNpcData.get(server).all().stream().filter(w->worker.equals(w.entityId))
                .findFirst().orElse(null);
        if(employment==null||!employment.active()||person==null||!firm.owner().equals(person.employer)
                ||!KingdomProfessionWorkRules.crafting(person.job,kind(firmId))
                ||!person.hasDestination)return null;
        for(ServerLevel level:server.getAllLevels()){
            if(!person.dimension.equals(level.dimension().location().toString()))continue;
            Container goods=workshopInventory(level,worker,firmId,plan.output());
            if(goods==null || stockIn(goods,plan.output())<1)return null;
            for(Offer existing:new ArrayList<>(offers.values()))if(existing.seller().equals(firmId)
                    && existing.itemId().equals(plan.output())){
                if(worker.equals(existing.stockWorker()) && existing.unitPrice()!=unitPrice){
                    Offer repriced=new Offer(existing.id(),firmId,plan.output(),unitPrice,worker);
                    offers.put(existing.id(),repriced);setDirty();return repriced;
                }
                return existing;
            }
            Offer offer=new Offer(UUID.randomUUID(),firmId,plan.output(),unitPrice,worker);
            offers.put(offer.id(),offer);setDirty();return offer;
        }
        return null;
    }
    private static long stockIn(Container inventory,String itemId){
        if(inventory==null)return 0;
        long result=0;
        for(int i=0;i<inventory.getContainerSize();i++){
            var stack=inventory.getItem(i);
            if(!stack.isEmpty() && itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))result+=stack.getCount();
        }
        return result;
    }
    private Container workshopInventory(ServerLevel level,UUID worker,UUID firmId,String item){
        Enterprise firm=firms.get(firmId);
        var plan=KingdomNpcWorkshopData.get(level.getServer()).plan(worker);
        var w=KingdomNpcData.get(level.getServer()).all().stream().filter(p->p.entityId.equals(worker))
                .findFirst().orElse(null);
        if(firm==null||plan==null||w==null||!plan.enterprise().equals(firmId)
                ||!plan.output().equals(item)||!firm.owner().equals(w.employer)
                ||!w.hasDestination||!w.dimension.equals(level.dimension().location().toString()))return null;
        BlockPos pos=new BlockPos(w.destX,w.destY,w.destZ);
        Counter counter=counters.get(firmId);
        if(counter==null || !counter.dimension().equals(w.dimension) || counter.pos().distSqr(pos)>36)
            return null; // No remote delivery disguised as a local counter transaction.
        if(!level.hasChunkAt(pos)||!KingdomTerritories.get(level.getServer()).usable(level,pos,firm.owner(),"finished"))return null;
        var entity=level.getBlockEntity(pos);return entity instanceof Container c?c:null;
    }
    private Container inventoryForOffer(ServerLevel level,Offer offer){
        return offer.stockWorker()==null ? finishedInventory(level,offer.seller())
                : workshopInventory(level,offer.stockWorker(),offer.seller(),offer.itemId());
    }
    private Container finishedInventory(ServerLevel level,UUID enterprise){
        var binding=storageBinding(enterprise,EnterpriseBindingRole.FINISHED_STORAGE);
        if(binding!=null){
            var snapshot=EnterpriseAccessService.snapshot(level,this,enterprise,EnterpriseBindingRole.FINISHED_STORAGE);
            return snapshot.status()==EnterpriseAccessService.Status.BOUND_AVAILABLE?snapshot.container():null;
        }
        return legacyInventory(level,enterprise);
    }
    private Container receivingInventory(ServerLevel level,UUID enterprise){
        var binding=storageBinding(enterprise,EnterpriseBindingRole.RAW_STORAGE);
        if(binding!=null){
            var snapshot=EnterpriseAccessService.snapshot(level,this,enterprise,EnterpriseBindingRole.RAW_STORAGE);
            return snapshot.status()==EnterpriseAccessService.Status.BOUND_AVAILABLE?snapshot.container():null;
        }
        return legacyInventory(level,enterprise);
    }
    /** Unresolved trades quarantine BOTH counterparties until an operator reconciles them. */
    public boolean unresolved(UUID seller){
        return journalUnavailable || quarantined.contains(seller) || trades.values().stream()
                .anyMatch(t->(t.seller().equals(seller) || t.buyer().equals(seller)) && t.state()!=TradeState.SETTLED);
    }
    public Collection<Offer> offers() {return Collections.unmodifiableCollection(offers.values());}
    public Collection<Trade> trades() {return Collections.unmodifiableCollection(trades.values());}
    public long stock(ServerLevel level,Offer offer){
        if(offer==null)return 0;
        return stockIn(inventoryForOffer(level,offer),offer.itemId());
    }
    public long stock(ServerLevel level,UUID business,String itemId) {
        Container inventory=finishedInventory(level,business);
        if(inventory==null)return 0;
        ResourceLocation item=ResourceLocation.tryParse(itemId);
        if(item==null)return 0;
        long count=0;
        for(int i=0;i<inventory.getContainerSize();i++){
            ItemStack stack=inventory.getItem(i);
            if(!stack.isEmpty() && item.equals(BuiltInRegistries.ITEM.getKey(stack.getItem())))count+=stack.getCount();
        }
        return count;
    }
    private Container legacyInventory(ServerLevel level,UUID business) {
        Counter counter=counters.get(business);Enterprise firm=firms.get(business);
        if(counter==null || firm==null || !counter.dimension().equals(level.dimension().location().toString())
                || !level.hasChunkAt(counter.pos())
                || !KingdomTerritories.get(level.getServer()).canUse(level,counter.pos(),firm.owner()))return null;
        BlockEntity entity=level.getBlockEntity(counter.pos());
        return entity instanceof Container container ? container : null;
    }
    /** Read-only view of a bound, loaded physical counter for recovery diagnostics.
     * The enterprise ledger balance is included as an observed value, NOT as proof
     * that physical coins were durably persisted. Never loads chunks on demand.
     */
    public TradeAtomicEconomy.Account inspectPhysicalAccount(ServerLevel level, UUID enterpriseId)
            throws IOException {
        Enterprise firm = firms.get(enterpriseId);
        if (firm == null || journalUnavailable) return null;
        Container container = finishedInventory(level, enterpriseId);
        if (container == null) return null;
        return TradeMinecraftContainerView.snapshot(enterpriseId, firm.cash(), container);
    }

    /** Fail-closed comparison with the atomic store. No inventory writes occur. */
    public TradePhysicalReconciliation.Result inspectPhysicalAccounts(ServerLevel level,
            TradeAtomicEconomy economy, Collection<UUID> enterpriseIds) throws IOException {
        if (journalUnavailable) return TradePhysicalReconciliation.Result.UNAVAILABLE;
        return TradePhysicalReconciliation.inspectAll(economy, enterpriseIds,
                id -> inspectPhysicalAccount(level, id));
    }

    /** A synchronous, physically verified local sale. No automatic item minting or money transfer.
     * Recovery guarantee: unresolved trades block further purchases from their offers;
     * cross-SavedData and block inventories are NOT crash-atomic and need manual reconciliation.
     */
    public boolean buy(ServerPlayer buyer,UUID offerId,int count) {
        Offer offer=offers.get(offerId);
        Enterprise purchaser=owned(buyer.getUUID());
        if(offer==null || purchaser==null || !atCounter(buyer,purchaser.id())
                || !atCounter(buyer,offer.seller()))return false;
        return purchase(buyer.serverLevel(),purchaser.id(),offer,count,false);
    }
    /**
     * Customer-facing, single-item purchase. The server owns the published offer,
     * physical stock, wallet debit and delivery; the client only selects an ID.
     * Inventory space and purse balance are checked before any mutation.
     */
    public boolean buyAsVisitor(ServerPlayer visitor, UUID sellerId, UUID offerId) {
        if(visitor==null || sellerId==null || offerId==null)return false;
        Offer offer=offers.get(offerId);
        Enterprise vendor=firms.get(sellerId);
        if(offer==null || vendor==null || !sellerId.equals(offer.seller())
                || vendor.owner().equals(visitor.getUUID()) || unresolved(sellerId)
                || offer.unitPrice()<1 || offer.unitPrice()>1_000_000
                || vendor.cash()>Long.MAX_VALUE-offer.unitPrice())return false;
        ServerLevel level=visitor.serverLevel();
        var core=cores.get(sellerId);
        if(core==null || !core.active()
                || !level.dimension().location().toString().equals(core.dimension())
                || !level.hasChunkAt(BlockPos.of(core.packedPos()))
                || BlockPos.of(core.packedPos()).distSqr(visitor.blockPosition())>64
                || !(level.getBlockEntity(BlockPos.of(core.packedPos()))
                     instanceof ru.projectkingdom.enterprise.EnterpriseCoreBlockEntity present)
                || !sellerId.equals(present.enterpriseId()))return false;
        Container source=inventoryForOffer(level,offer);
        if(source==null || KingdomCurrency.balance(visitor)<offer.unitPrice())return false;
        ResourceLocation itemId=ResourceLocation.tryParse(offer.itemId());
        if(itemId==null)return false;
        int stockSlot=-1;
        ItemStack delivery=ItemStack.EMPTY;
        for(int i=0;i<source.getContainerSize();i++) {
            ItemStack stack=source.getItem(i);
            if(!stack.isEmpty() && itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                    && source.canTakeItem(source,i,stack)) {
                stockSlot=i;
                delivery=stack.copy();delivery.setCount(1);break;
            }
        }
        if(stockSlot<0)return false;
        // Do not rely on partial Inventory.add operations: reserve one main-inventory
        // slot that can accept the exact stack and its NBT metadata.
        int destination=-1;
        var inventory=visitor.getInventory();
        for(int i=0;i<36;i++){
            var stack=inventory.getItem(i);
            if(!stack.isEmpty() && ItemStack.isSameItemSameTags(stack,delivery)
                    && stack.getCount()<Math.min(stack.getMaxStackSize(),inventory.getMaxStackSize())){
                destination=i;break;
            }
        }
        if(destination<0)for(int i=0;i<36;i++) {
            if(inventory.getItem(i).isEmpty()){destination=i;break;}
        }
        if(destination<0)return false;
        // Validate the stock before taking money, not after the cash is deposited.
        ItemStack from=source.getItem(stockSlot);
        ItemStack originalSlot=inventory.getItem(destination);
        if(from.isEmpty() || !ItemStack.isSameItemSameTags(from,delivery)
                || (!originalSlot.isEmpty() &&
                    (!ItemStack.isSameItemSameTags(originalSlot,delivery)
                    || originalSlot.getCount()>=originalSlot.getMaxStackSize())))return false;
        var cash=EnterpriseAccessService.snapshot(level,this,sellerId,EnterpriseBindingRole.CASHBOX);
        if(cash.status()!=EnterpriseAccessService.Status.BOUND_AVAILABLE || cash.container()==source
                || KingdomPhysicalCash.value(cash.container())<vendor.cash()
                || !KingdomPhysicalCash.deposit(visitor,cash.container(),offer.unitPrice()))return false;
        from.shrink(1);
        if(originalSlot.isEmpty())inventory.setItem(destination,delivery.copy());
        else originalSlot.grow(1);
        source.setChanged();
        inventory.setChanged();
        firms.put(vendor.id(),new Enterprise(vendor.id(),vendor.owner(),vendor.name(),
                vendor.cash()+offer.unitPrice()));
        setDirty();
        return true;
    }
    /** Authorised adjacent-counter pickup by a real physically present, contracted NPC.
     * This transfers goods directly between two nearby physical counters. For distant
     * parties a separate courier escrow/delivery protocol is necessary.
     */
    boolean buyForNpc(ServerLevel level,UUID worker,UUID firmId,UUID offerId,int count){
        Offer offer=offers.get(offerId);Enterprise purchaser=firms.get(firmId);
        if(offer==null||purchaser==null||count<1||count>16)return false;
        Counter a=counters.get(firmId),b=counters.get(offer.seller());
        if(a==null||b==null||!a.dimension().equals(level.dimension().location().toString())
                ||!b.dimension().equals(a.dimension())||a.pos().distSqr(b.pos())>25)return false;
        var employment=KingdomEnterprisePayrollData.get(level.getServer()).contract(firmId,worker);
        if(employment==null||!employment.active())return false;
        var workerState=KingdomNpcData.get(level.getServer()).all().stream()
                .filter(w->w.entityId.equals(worker)).findFirst().orElse(null);
        if(workerState==null||!purchaser.owner().equals(workerState.employer)
                ||!"work".equals(workerState.order)||workerState.contractPaused)return false;
        var entity=level.getEntity(worker);
        if(!(entity instanceof net.minecraft.world.entity.npc.Villager npc)||!npc.isAlive()
                ||npc.distanceToSqr(a.pos().getX()+0.5,a.pos().getY()+0.5,a.pos().getZ()+0.5)>9
                ||npc.distanceToSqr(b.pos().getX()+0.5,b.pos().getY()+0.5,b.pos().getZ()+0.5)>9)return false;
        return purchase(level,firmId,offer,count,true);
    }
    /** Single transfer implementation for manual and adjacent-counter NPC purchases.
     * Inventories and cash balances are never changed on unsuccessful validation.
     */
    private boolean purchase(ServerLevel level,UUID buyerFirm,Offer offer,int count,boolean npc){
        connectJournal(level.getServer());
        Enterprise purchaser=firms.get(buyerFirm);
        if(offer==null||purchaser==null||count<1||count>64||purchaser.id().equals(offer.seller()))return false;
        Enterprise vendor=firms.get(offer.seller());
        if(vendor==null||offer.unitPrice()>Long.MAX_VALUE/count)return false;
        long total=offer.unitPrice()*count;
        if(total>1_000_000||purchaser.cash()<total||vendor.cash()>Long.MAX_VALUE-total)return false;
        if(unresolved(vendor.id()) || unresolved(purchaser.id()))return false;
        Container from=inventoryForOffer(level,offer),to=receivingInventory(level,purchaser.id());
        if(from==null||to==null||from==to||(npc&&to.getContainerSize()<=9))return false;
        var buyerCash=EnterpriseAccessService.snapshot(level,this,buyerFirm,EnterpriseBindingRole.CASHBOX);
        var sellerCash=EnterpriseAccessService.snapshot(level,this,vendor.id(),EnterpriseBindingRole.CASHBOX);
        if(buyerCash.status()!=EnterpriseAccessService.Status.BOUND_AVAILABLE
                ||sellerCash.status()!=EnterpriseAccessService.Status.BOUND_AVAILABLE)return false;
        Container coinsFrom=buyerCash.container(),coinsTo=sellerCash.container();
        if(coinsFrom==null||coinsTo==null||coinsFrom==coinsTo
                ||coinsFrom==from||coinsFrom==to||coinsTo==from||coinsTo==to
                ||KingdomPhysicalCash.value(coinsFrom)<purchaser.cash()
                ||KingdomPhysicalCash.value(coinsTo)<vendor.cash()
                ||!KingdomPhysicalCash.canMove(coinsFrom,coinsTo,total))return false;
        List<ItemStack> source=new ArrayList<>(),dest=new ArrayList<>();
        for(int i=0;i<from.getContainerSize();i++)source.add(from.getItem(i).copy());
        for(int i=0;i<to.getContainerSize();i++)dest.add(to.getItem(i).copy());
        ResourceLocation itemId=ResourceLocation.tryParse(offer.itemId());
        if(itemId==null)return false;
        int remaining=count;
        for(int i=0;i<source.size() && remaining>0;i++){
            ItemStack stack=source.get(i);
            if(stack.isEmpty()||!itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                    ||!from.canTakeItem(from,i,stack))continue;
            int take=Math.min(stack.getCount(),remaining);
            ItemStack cargo=stack.copy();cargo.setCount(take);
            if(!insert(dest,to,cargo,npc?9:0))return false;
            stack.shrink(take);remaining-=take;
        }
        if(remaining!=0)return false;
        // Persist both sides of the transfer so recovery can distinguish an
        // unchanged world from an apparently completed or partially saved trade.
        List<ItemStack> originalSource = new ArrayList<>(), originalDest = new ArrayList<>();
        for (int i=0;i<from.getContainerSize();i++) originalSource.add(from.getItem(i).copy());
        for (int i=0;i<to.getContainerSize();i++) originalDest.add(to.getItem(i).copy());
        String beforeDigest=snapshotDigest(purchaser,vendor,originalSource,originalDest);
        Enterprise buyerAfter=new Enterprise(purchaser.id(),purchaser.owner(),purchaser.name(),purchaser.cash()-total);
        Enterprise sellerAfter=new Enterprise(vendor.id(),vendor.owner(),vendor.name(),vendor.cash()+total);
        String afterDigest=snapshotDigest(buyerAfter,sellerAfter,source,dest);
        UUID id=UUID.randomUUID();
        // fsync intent BEFORE touching a single inventory slot or account.
        // If the journal cannot be written, the transaction must not begin.
        try { journal.prepare(id,purchaser.id(),vendor.id(),beforeDigest+":"+afterDigest); }
        catch (IOException | RuntimeException ex) {
            journalUnavailable = true;
            System.err.println("[Kingdom] Trade intent write failed; purchases disabled: " + ex);
            return false;
        }
        quarantined.add(purchaser.id()); quarantined.add(vendor.id());
        trades.put(id,new Trade(id,offer.id(),purchaser.id(),vendor.id(),count,total,TradeState.PENDING));setDirty();
        // An unflushed chunk and SavedData are NOT an atomic commit. Remain quarantined
        // after apparent success; an operator must reconcile durable world state.
        // Physical coins must move first, between the actual independent cashboxes.
        if(!KingdomPhysicalCash.move(coinsFrom,coinsTo,total)) {
            trades.put(id,new Trade(id,offer.id(),purchaser.id(),vendor.id(),count,total,
                TradeState.NEEDS_RECONCILIATION));
            setDirty();
            return false;
        }
        for(int i=0;i<source.size();i++)from.setItem(i,source.get(i));
        for(int i=0;i<dest.size();i++)to.setItem(i,dest.get(i));
        from.setChanged();to.setChanged();
        firms.put(purchaser.id(),buyerAfter);
        firms.put(vendor.id(),sellerAfter);
        trades.put(id,new Trade(id,offer.id(),purchaser.id(),vendor.id(),count,total,TradeState.NEEDS_RECONCILIATION));
        try { journal.markForAudit(id); }
        catch (IOException ex) { journalUnavailable = true; System.err.println("[Kingdom] Trade audit write failed: " + ex); }
        setDirty();return true;
    }
    /** Read-only crash diagnostic. This NEVER closes or replays a transaction.
     * If either counter is unloaded, missing, or a workshop stock container cannot
     * be resolved, the result is indeterminate and both firms stay quarantined.
     */
    public TradeWriteAheadJournal.RecoveryAssessment assessTrade(ServerLevel level, UUID id) {
        Trade trade=trades.get(id);
        if (journalUnavailable || journal==null || trade==null)
            return TradeWriteAheadJournal.RecoveryAssessment.MIXED_OR_CHANGED;
        Offer offer=offers.get(trade.offer());
        Enterprise buyer=firms.get(trade.buyer()), seller=firms.get(trade.seller());
        if (offer==null || buyer==null || seller==null || !offer.seller().equals(seller.id()))
            return TradeWriteAheadJournal.RecoveryAssessment.MIXED_OR_CHANGED;
        Container from=inventoryForOffer(level,offer), to=receivingInventory(level,buyer.id());
        if (from==null || to==null || from==to)
            return TradeWriteAheadJournal.RecoveryAssessment.MIXED_OR_CHANGED;
        List<ItemStack> source=new ArrayList<>(), dest=new ArrayList<>();
        for (int i=0;i<from.getContainerSize();i++) source.add(from.getItem(i).copy());
        for (int i=0;i<to.getContainerSize();i++) dest.add(to.getItem(i).copy());
        try {
            TradeWriteAheadJournal.Entry entry=journal.read(id);
            if (!entry.buyer().equals(buyer.id()) || !entry.seller().equals(seller.id()))
                return TradeWriteAheadJournal.RecoveryAssessment.MIXED_OR_CHANGED;
            return TradeWriteAheadJournal.assess(entry,snapshotDigest(buyer,seller,source,dest));
        } catch (IOException ex) {
            journalUnavailable=true;
            return TradeWriteAheadJournal.RecoveryAssessment.MIXED_OR_CHANGED;
        }
    }
    /** Read-only recovery recommendation. Minecraft chunk/SavedData durability has
     * not been established by a trusted barrier, so this API never authorizes
     * automatic release of the affected enterprises. */
    public TradeRecoveryPolicy.Decision recoveryRecommendation(ServerLevel level, UUID tradeId,
                                                               String worldGeneration, long saveEpoch) {
        TradeWriteAheadJournal.RecoveryAssessment assessment = assessTrade(level, tradeId);
        TradeRecoveryPolicy.Observed observed = switch (assessment) {
            case BEFORE -> TradeRecoveryPolicy.Observed.BEFORE;
            case AFTER -> TradeRecoveryPolicy.Observed.AFTER;
            default -> TradeRecoveryPolicy.Observed.MIXED;
        };
        boolean checkpoint = checkpointCandidate(level, tradeId, worldGeneration, saveEpoch);
        return TradeRecoveryPolicy.evaluate(new TradeRecoveryPolicy.Evidence(
                observed, !journalUnavailable, checkpoint,
                false, // No trustworthy Minecraft multi-file fsync barrier yet.
                worldGeneration != null && !worldGeneration.isBlank(), false));
    }

    /** Read-only checkpoint diagnostic. A receipt is NOT a durable Minecraft save
     * barrier: even a match does not authorize automatic reconciliation.
     * The caller must supply a verified world generation and durable save epoch.
     */
    public boolean checkpointCandidate(ServerLevel level, UUID tradeId,
                                       String worldGeneration, long durableSaveEpoch) {
        if (assessTrade(level, tradeId) != TradeWriteAheadJournal.RecoveryAssessment.AFTER)
            return false;
        try {
            TradeWriteAheadJournal.Entry entry = journal.read(tradeId);
            String[] digests = entry.snapshotDigest().split(":", -1);
            if (digests.length != 2) return false;
            TradeCheckpointLedger ledger = new TradeCheckpointLedger(
                    level.getServer().getWorldPath(LevelResource.ROOT)
                            .resolve("data/kingdom_trade_checkpoints"));
            return new TradeCheckpointGate(ledger).candidate(tradeId, digests[1],
                    worldGeneration, durableSaveEpoch);
        } catch (IOException | RuntimeException ex) {
            System.err.println("[Kingdom] Checkpoint diagnostic failed: " + ex);
            return false;
        }
    }

    /** Operator has reconciled actual source/destination inventories AND both cash balances.
     * This does not mutate items or money. Never call automatically after a restart.
     */
    /** Verified acknowledgement: never closes a transaction unless the loaded
     * inventories and cash balances match the journal's expected AFTER state.
     * Operators must still ensure that world saves are durable before acknowledging.
     */
    public boolean acknowledgeVerified(ServerLevel level, UUID id) {
        if (assessTrade(level,id)!=TradeWriteAheadJournal.RecoveryAssessment.AFTER) return false;
        return closeVerifiedReconciliation(id);
    }
    private boolean closeVerifiedReconciliation(UUID id) {
        Trade pending=trades.get(id);
        if(pending==null)return false;
        if (journalUnavailable || journal == null) return false;
        try {
            TradeWriteAheadJournal.Entry entry = journal.read(id);
            if (!entry.buyer().equals(pending.buyer()) || !entry.seller().equals(pending.seller())
                    || !("NEEDS_RECONCILIATION".equals(entry.state())
                    || "RECONCILED".equals(entry.state()))) return false;
            // The receipt is fsynced before unlocking. A crash after this point can
            // only leave stale SavedData, which connectJournal repairs from receipt.
            if (!"RECONCILED".equals(entry.state())) journal.closeReconciled(id);
            trades.put(id,new Trade(id,pending.offer(),pending.buyer(),pending.seller(),
                    pending.count(),pending.copper(),TradeState.SETTLED));
            quarantined.remove(pending.buyer()); quarantined.remove(pending.seller());
            // Keep either counterparty locked if another unresolved trade exists.
            for (Trade other : trades.values()) if (other.state()!=TradeState.SETTLED) {
                quarantined.add(other.buyer()); quarantined.add(other.seller());
            }
            setDirty(); return true;
        } catch (IOException | RuntimeException ex) {
            journalUnavailable = true;
            System.err.println("[Kingdom] Trade reconciliation receipt failed: " + ex);
            return false;
        }
    }
    private static boolean insert(List<ItemStack> destination,Container inventory,ItemStack cargo,int firstSlot) {
        // Full copy with NBT is transferred, preserving TFC food/quality state.
        for(int i=firstSlot;i<destination.size() && !cargo.isEmpty();i++){
            ItemStack there=destination.get(i);
            if(!there.isEmpty() && ItemStack.isSameItemSameTags(there,cargo) && inventory.canPlaceItem(i,cargo)) {
                int free=Math.max(0,Math.min(there.getMaxStackSize(),inventory.getMaxStackSize())-there.getCount());
                int moved=Math.min(free,cargo.getCount());there.grow(moved);cargo.shrink(moved);
            }
        }
        for(int i=firstSlot;i<destination.size() && !cargo.isEmpty();i++){
            if(!destination.get(i).isEmpty() || !inventory.canPlaceItem(i,cargo))continue;
            int moved=Math.min(cargo.getCount(),Math.min(cargo.getMaxStackSize(),inventory.getMaxStackSize()));
            if(moved<1)continue;
            ItemStack next=cargo.copy();next.setCount(moved);destination.set(i,next);cargo.shrink(moved);
        }
        return cargo.isEmpty();
    }
    /** Old cash-only trade API is deliberately disabled: it could transfer money with no goods. */
    @Deprecated public Invoice invoice(UUID buyerOwner,UUID sellerOwner,long price) {return null;}
    @Deprecated public boolean settle(UUID buyerOwner,UUID invoiceId) {return false;}

    private static KingdomEnterpriseData load(CompoundTag root) {
        KingdomEnterpriseData d=new KingdomEnterpriseData();
        for(Tag row:root.getList("Firms",Tag.TAG_COMPOUND)) {
            CompoundTag t=(CompoundTag)row;
            if(t.hasUUID("Id") && t.hasUUID("Owner")) {
                Enterprise e=new Enterprise(t.getUUID("Id"),t.getUUID("Owner"),t.getString("Name"),Math.max(0,t.getLong("Cash")));
                d.firms.put(e.id(),e);
                d.kinds.put(e.id(),t.contains("Kind")?EnterpriseKind.byId(t.getString("Kind")):EnterpriseKind.LEGACY);
            }
        }
        for(Tag row:root.getList("Invoices",Tag.TAG_COMPOUND)) {
            CompoundTag t=(CompoundTag)row;
            if(t.hasUUID("Id") && t.hasUUID("Buyer") && t.hasUUID("Seller")) {
                Invoice v=new Invoice(t.getUUID("Id"),t.getUUID("Seller"),t.getUUID("Buyer"),Math.max(0,t.getLong("Price")),t.getBoolean("Paid"));
                d.legacyInvoices.put(v.id(),v);
            }
        }
        for(Tag row:root.getList("Selections",Tag.TAG_COMPOUND)) {CompoundTag t=(CompoundTag)row;
            if(t.hasUUID("Owner") && t.hasUUID("Firm"))d.selections.put(t.getUUID("Owner"),t.getUUID("Firm"));}
        for(Tag row:root.getList("Counters",Tag.TAG_COMPOUND)) {CompoundTag t=(CompoundTag)row;
            if(t.hasUUID("Firm"))d.counters.put(t.getUUID("Firm"),new Counter(t.getUUID("Firm"),t.getString("Dim"),new BlockPos(t.getInt("X"),t.getInt("Y"),t.getInt("Z"))));}
        for(Tag row:root.getList("Cashiers",Tag.TAG_COMPOUND)) {CompoundTag t=(CompoundTag)row;
            if(t.hasUUID("Firm") && t.hasUUID("Person"))d.cashiers.computeIfAbsent(t.getUUID("Firm"),k->new LinkedHashSet<>()).add(t.getUUID("Person"));}
        for(Tag row:root.getList("Offers",Tag.TAG_COMPOUND)) {CompoundTag t=(CompoundTag)row;
            if(t.hasUUID("Id") && t.hasUUID("Seller") && t.getLong("Price")>0)
                d.offers.put(t.getUUID("Id"),new Offer(t.getUUID("Id"),t.getUUID("Seller"),t.getString("Item"),t.getLong("Price"),
                        t.hasUUID("Worker")?t.getUUID("Worker"):null));}
        for(Tag row:root.getList("Trades",Tag.TAG_COMPOUND)) {CompoundTag t=(CompoundTag)row;
            if(!t.hasUUID("Id") || !t.hasUUID("Offer") || !t.hasUUID("Buyer") || !t.hasUUID("Seller"))continue;
            try {
                TradeState state=TradeState.valueOf(t.getString("State"));
                if(state==TradeState.PENDING)state=TradeState.NEEDS_RECONCILIATION;
                Trade v=new Trade(t.getUUID("Id"),t.getUUID("Offer"),t.getUUID("Buyer"),t.getUUID("Seller"),t.getInt("Count"),t.getLong("Price"),state);
                d.trades.put(v.id(),v);
            }catch(IllegalArgumentException ignored){}
        }
        for(Tag row:root.getList("EnterpriseCores",Tag.TAG_COMPOUND)) {CompoundTag t=(CompoundTag)row;
            if(!t.hasUUID("Firm")||!t.hasUUID("Owner"))continue;
            try{
                UUID id=t.getUUID("Firm");EnterpriseKind kind=EnterpriseKind.byId(t.getString("Kind"));
                if(d.firms.containsKey(id)&&kind!=EnterpriseKind.LEGACY)d.cores.put(id,new EnterpriseCoreBinding(
                        id,t.getUUID("Owner"),kind,t.getString("Dim"),t.getLong("Pos"),t.getBoolean("Active")));
            }catch(IllegalArgumentException ignored){}
        }
        for(Tag row:root.getList("EnterpriseStorage",Tag.TAG_COMPOUND)){CompoundTag t=(CompoundTag)row;
            if(!t.hasUUID("Firm"))continue;
            try{UUID id=t.getUUID("Firm");EnterpriseBindingRole role=EnterpriseBindingRole.valueOf(t.getString("Role"));
                d.storageBindings.computeIfAbsent(id,key->new EnumMap<>(EnterpriseBindingRole.class)).put(role,
                        new StorageBinding(id,role,t.getString("Dim"),BlockPos.of(t.getLong("Pos"))));
            }catch(IllegalArgumentException ignored){}
        }
        for(Tag row:root.getList("ClosedBusinesses",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)row;
            if(t.hasUUID("Id")&&t.hasUUID("Owner")){
                UUID id=t.getUUID("Id");
                d.closedBusinesses.put(id,new ClosedBusiness(id,t.getUUID("Owner"),t.getString("Name"),Math.max(0,t.getLong("Day"))));
            }
        }
        return d;
    }
    @Override public CompoundTag save(CompoundTag root) {
        ListTag a=new ListTag();for(Enterprise e:firms.values()){CompoundTag t=new CompoundTag();t.putUUID("Id",e.id());t.putUUID("Owner",e.owner());t.putString("Name",e.name());t.putLong("Cash",e.cash());t.putString("Kind",kind(e.id()).id());a.add(t);}root.put("Firms",a);
        ListTag b=new ListTag();for(Invoice v:legacyInvoices.values()){CompoundTag t=new CompoundTag();t.putUUID("Id",v.id());t.putUUID("Buyer",v.buyer());t.putUUID("Seller",v.seller());t.putLong("Price",v.price());t.putBoolean("Paid",v.paid());b.add(t);}root.put("Invoices",b);
        a=new ListTag();for(var e:selections.entrySet()){CompoundTag t=new CompoundTag();t.putUUID("Owner",e.getKey());t.putUUID("Firm",e.getValue());a.add(t);}root.put("Selections",a);
        a=new ListTag();for(Counter c:counters.values()){CompoundTag t=new CompoundTag();t.putUUID("Firm",c.enterprise());t.putString("Dim",c.dimension());t.putInt("X",c.pos().getX());t.putInt("Y",c.pos().getY());t.putInt("Z",c.pos().getZ());a.add(t);}root.put("Counters",a);
        a=new ListTag();for(var e:cashiers.entrySet())for(UUID p:e.getValue()){CompoundTag t=new CompoundTag();t.putUUID("Firm",e.getKey());t.putUUID("Person",p);a.add(t);}root.put("Cashiers",a);
        a=new ListTag();for(Offer o:offers.values()){CompoundTag t=new CompoundTag();t.putUUID("Id",o.id());t.putUUID("Seller",o.seller());t.putString("Item",o.itemId());t.putLong("Price",o.unitPrice());if(o.stockWorker()!=null)t.putUUID("Worker",o.stockWorker());a.add(t);}root.put("Offers",a);
        a=new ListTag();for(Trade v:trades.values()){CompoundTag t=new CompoundTag();t.putUUID("Id",v.id());t.putUUID("Offer",v.offer());t.putUUID("Buyer",v.buyer());t.putUUID("Seller",v.seller());t.putInt("Count",v.count());t.putLong("Price",v.copper());t.putString("State",v.state().name());a.add(t);}root.put("Trades",a);
        a=new ListTag();for(EnterpriseCoreBinding c:cores.values()){CompoundTag t=new CompoundTag();t.putUUID("Firm",c.enterpriseId());t.putUUID("Owner",c.owner());t.putString("Kind",c.kind().id());t.putString("Dim",c.dimension());t.putLong("Pos",c.packedPos());t.putBoolean("Active",c.active());a.add(t);}root.put("EnterpriseCores",a);
        a=new ListTag();for(var byRole:storageBindings.values())for(StorageBinding s:byRole.values()){CompoundTag t=new CompoundTag();t.putUUID("Firm",s.enterprise());t.putString("Role",s.role().name());t.putString("Dim",s.dimension());t.putLong("Pos",s.pos().asLong());a.add(t);}root.put("EnterpriseStorage",a);
        ListTag archived=new ListTag();
        for(var closed:closedBusinesses.values()){
            CompoundTag tag=new CompoundTag();tag.putUUID("Id",closed.id());tag.putUUID("Owner",closed.owner());
            tag.putString("Name",closed.name());tag.putLong("Day",closed.gameDay());archived.add(tag);
        }
        root.put("ClosedBusinesses",archived);
        return root;
    }
}
