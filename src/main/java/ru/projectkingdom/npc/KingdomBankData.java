package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import ru.projectkingdom.administration.AdministrativeCoreBlockEntity;
import ru.projectkingdom.administration.AdministrativeKind;
import ru.projectkingdom.administration.AdministrativeBindingRole;
import ru.projectkingdom.administration.KingdomAdministrationData;

/** Deposits are backed by real copper-value coins taken from a physical wallet.
 * Personal receipts are never spent implicitly; an explicit donation can transfer
 * their ownership to the town budget in the same vault. */
public final class KingdomBankData extends SavedData {
    private static final String KEY = "kingdom_bank_storage_v1";
    private final Map<UUID, Long> accounts = new LinkedHashMap<>();
    /** Every backed account is payable from exactly one bank vault. */
    private final Map<UUID, UUID> accountHomes = new LinkedHashMap<>();
    private boolean payableAt(UUID account,UUID bank) {
        if(account==null||bank==null)return false;
        UUID home=accountHomes.get(account);
        return home==null ? balance(account)==0 : home.equals(bank);
    }
    private final Map<String, UUID> branches = new LinkedHashMap<>();
    private long vault = 0;
    private static String location(ServerLevel level, BlockPos pos) {
        return level.dimension().location()+"|"+pos.getX()+"|"+pos.getY()+"|"+pos.getZ();
    }
    public static KingdomBankData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(KingdomBankData::load, KingdomBankData::new, KEY);
    }
    public boolean register(ServerLevel level, BlockPos pos, UUID owner) {
        if(owner==null || !level.hasChunkAt(pos) || !level.getBlockState(pos).is(Blocks.LODESTONE))return false;
        String key=location(level,pos);
        if(branches.containsKey(key))return false;
        branches.put(key,owner);setDirty();return true;
    }
    public boolean branch(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.LODESTONE)
            && branches.containsKey(location(level,pos));
    }
    public long balance(UUID owner) {return accounts.getOrDefault(owner,0L);}
    public long vault() {return vault;}
    /** Legacy receipts are preserved here for an administrator, but never withdrawable. */
    private final Map<UUID,Long> legacyAccounts=new LinkedHashMap<>();
    public long legacyBalance(UUID owner){return legacyAccounts.getOrDefault(owner,0L);}
    private final Map<UUID,Long> auditConfirmations=new LinkedHashMap<>();
    public enum ReconcileStatus { CONFIRM_REQUIRED, DONE, NOT_AUTHORIZED, NOT_IN_BANK,
        NO_VAULT, VAULT_NOT_EMPTY, NOTHING_TO_ARCHIVE, TOO_MANY_ENTRIES }
    public static String reconciliationMessage(ReconcileStatus status){
        return switch(status){
            case CONFIRM_REQUIRED->"ВНИМАНИЕ: старые неподтверждённые остатки будут перенесены в архив требований, а новые счета начнут с нуля. Монеты не создаются и прежние обязательства не исчезают. Нажмите кнопку повторно в течение 30 секунд.";
            case DONE->"Сверка зарегистрирована: старые неподтверждённые суммы сохранены в архиве, новые операции доступны с нулевого обеспеченного резерва.";
            case NOT_AUTHORIZED->"Сверку может провести только мэр или управляющий банка.";
            case NOT_IN_BANK->"Подойдите к городскому банку и откройте его меню.";
            case NO_VAULT->"Сначала привяжите банковский сейф и загрузите его чанк.";
            case VAULT_NOT_EMPTY->"В банковском сейфе уже есть монеты. Не смешивайте обеспеченные и старые остатки; требуется ручная сверка.";
            case NOTHING_TO_ARCHIVE->"Неподтверждённых старых остатков нет.";
            case TOO_MANY_ENTRIES->"Слишком много счетов для автоматической сверки; нужен администратор сервера.";
        };
    }
    /** Two-click, manager-only migration for worlds whose bank has NO physical
     * coins. Numerical promises remain an archive, never get minted.
     * The server records new deposits only after this explicit action.
     */
    public ReconcileStatus reconcileEmptyBank(ServerPlayer manager){
        if(manager==null)return ReconcileStatus.NOT_IN_BANK;
        UUID town=bankTown(manager);
        if(town==null)return ReconcileStatus.NOT_IN_BANK;
        var civic=KingdomCivicsData.get(manager.getServer()).townById(town);
        var admin=KingdomAdministrationData.get(manager.getServer());
        if(civic==null||!ru.projectkingdom.administration.AdministrativePermissionService.mayManage(
                manager.getUUID(),admin.founder(town),civic.mayor(),
                admin.roles(town,manager.getUUID()),AdministrativeKind.BANK))
            return ReconcileStatus.NOT_AUTHORIZED;
        var vault=KingdomPhysicalCash.townVault(manager,town,AdministrativeBindingRole.BANK_VAULT);
        if(vault==null)return ReconcileStatus.NO_VAULT;
        if(KingdomPhysicalCash.value(vault)!=0)return ReconcileStatus.VAULT_NOT_EMPTY;
        if(accounts.size()>10000)return ReconcileStatus.TOO_MANY_ENTRIES;
        boolean oldReserves=admin.bankReserve(town)>0||admin.treasuryReserve(town)>0;
        boolean oldAccounts=false;
        for(var entry:accounts.entrySet())if(entry.getValue()>0
                &&(accountHomes.get(entry.getKey())==null
                ||town.equals(accountHomes.get(entry.getKey())))){oldAccounts=true;break;}
        if(!oldAccounts&&!oldReserves)return ReconcileStatus.NOTHING_TO_ARCHIVE;
        long now=manager.serverLevel().getGameTime();
        Long confirmed=auditConfirmations.get(manager.getUUID());
        if(confirmed==null||confirmed<now||confirmed>now+600){
            auditConfirmations.put(manager.getUUID(),now+600);
            return ReconcileStatus.CONFIRM_REQUIRED;
        }
        auditConfirmations.remove(manager.getUUID());
        for(var entry:accounts.entrySet()){
            UUID home=accountHomes.get(entry.getKey());
            if(entry.getValue()>0&&(home==null||town.equals(home))){
                legacyAccounts.merge(entry.getKey(),entry.getValue(),
                    (a,b)->a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b);
                accounts.put(entry.getKey(),0L);
                accountHomes.put(entry.getKey(),town);
            }
        }
        admin.archiveUnbackedReserves(town);
        setDirty();
        return ReconcileStatus.DONE;
    }

    private static boolean covered(long cash,long bank,long city){
        return cash>=0&&bank>=0&&city>=0&&bank<=Long.MAX_VALUE-city
                &&cash>=bank+city;
    }
    /** Exact human-readable deposit/withdrawal diagnostics for the city bank. */
    public enum LegacyTransferStatus {
        MOVED, NOT_BANK_MANAGER, NOT_AT_BANK, NO_OLD_VAULT, NO_NEW_VAULT,
        EMPTY, SAME_CONTAINER, TOO_MANY_COINS, NO_ROOM
    }
    public static String legacyTransferMessage(LegacyTransferStatus status){
        return switch(status){
            case MOVED->"Монеты из старого сундука казны перенесены в банковский сейф.";
            case NOT_BANK_MANAGER->"Перенос может выполнять только мэр или уполномоченный казначей.";
            case NOT_AT_BANK->"Откройте главное меню установленного городского банка.";
            case NO_OLD_VAULT->"Ранее привязанный сундук казны не найден или его чанк не загружен.";
            case NO_NEW_VAULT->"Привяжите отдельный банковский сейф и убедитесь, что его чанк загружен.";
            case EMPTY->"В старом сундуке нет монет Kingdom — переносить нечего.";
            case SAME_CONTAINER->"Старая и новая касса указывают на один сундук. Перенос не нужен.";
            case TOO_MANY_COINS->"В старой казне больше 1 000 000 медных. Разделите перенос на несколько партий.";
            case NO_ROOM->"В банковском сейфе не хватает места для всех монет; освободите слоты.";
        };
    }
    /** Backwards-compatible physical consolidation. Does not touch city finances:
     * existing bank/treasury ledgers remain unchanged, no minting or withdrawal.
     * The old vault becomes empty of coins if the operation succeeds.
     */
    public LegacyTransferStatus consolidateOldTownVault(ServerPlayer manager){
        UUID town=manager==null?null:bankTown(manager);
        if(town==null)return LegacyTransferStatus.NOT_AT_BANK;
        var civics=KingdomCivicsData.get(manager.getServer());
        var city=civics.townById(town);
        var admin=KingdomAdministrationData.get(manager.getServer());
        if(city==null||!ru.projectkingdom.administration.AdministrativePermissionService.mayManage(
                manager.getUUID(),admin.founder(town),city.mayor(),
                admin.roles(town,manager.getUUID()),AdministrativeKind.BANK))
            return LegacyTransferStatus.NOT_BANK_MANAGER;
        var oldBindings=admin.containerBindings(town,AdministrativeBindingRole.TREASURY_VAULT);
        if(oldBindings.size()!=1)return LegacyTransferStatus.NO_OLD_VAULT;
        var newBindings=admin.containerBindings(town,AdministrativeBindingRole.BANK_VAULT);
        if(newBindings.size()!=1)return LegacyTransferStatus.NO_NEW_VAULT;
        if(oldBindings.get(0).dimension().equals(newBindings.get(0).dimension())
                &&KingdomContainerAccess.samePhysicalStorage(manager.serverLevel(),
                    BlockPos.of(oldBindings.get(0).packedPos()),
                    BlockPos.of(newBindings.get(0).packedPos())))
            return LegacyTransferStatus.SAME_CONTAINER;
        var source=KingdomPhysicalCash.townVault(manager,town,AdministrativeBindingRole.TREASURY_VAULT);
        var destination=KingdomPhysicalCash.townVault(manager,town,AdministrativeBindingRole.BANK_VAULT);
        if(source==null)return LegacyTransferStatus.NO_OLD_VAULT;
        if(destination==null)return LegacyTransferStatus.NO_NEW_VAULT;
        if(source==destination)return LegacyTransferStatus.SAME_CONTAINER;
        long copper=KingdomPhysicalCash.value(source);
        if(copper==0)return LegacyTransferStatus.EMPTY;
        if(copper<0||copper>1_000_000)return LegacyTransferStatus.TOO_MANY_COINS;
        if(!KingdomPhysicalCash.move(source,destination,copper))return LegacyTransferStatus.NO_ROOM;
        return LegacyTransferStatus.MOVED;
    }

    public String depositProblem(ServerPlayer player,long amount){
        if(player==null||!atBranch(player))return "Откройте меню банковского блока и подойдите к нему.";
        if(amount<=0||amount>1_000_000)return "Допустимая сумма от 1 до 1 000 000 медных.";
        UUID town=bankTown(player);
        if(town==null)return "Нужен установленный городской банк, а не старый магнетитовый терминал.";
        if(!payableAt(player.getUUID(),town))
            return "Старый баланс счёта ещё не привязан к физическому банку. Нужна сверка старого счёта.";
        var cash=KingdomPhysicalCash.townVault(player,town,AdministrativeBindingRole.BANK_VAULT);
        if(cash==null)return "Банковский сейф не подключён или его чанк выгружен. Управляющий должен привязать сундук.";
        var admin=KingdomAdministrationData.get(player.getServer());
        if(!covered(KingdomPhysicalCash.value(cash),admin.bankReserve(town),admin.treasuryReserve(town)))
            return "В сейфе не хватает реальных монет для уже записанных счетов и казны. Требуется сверка.";
        long wallet=KingdomCurrency.balance(player);
        if(wallet<amount)return "В кошельке "+wallet+" медных. Внесите монеты в кошелёк Kingdom: нужно "+amount+".";
        if(balance(player.getUUID())>Long.MAX_VALUE-amount)return "Баланс счёта превысил допустимый размер.";
        if(admin.bankReserve(town)>Long.MAX_VALUE-amount)return "Банковский резерв переполнен.";
        return "Банковский сундук полон или не принимает такой номинал монет.";
    }
    public String withdrawProblem(ServerPlayer player,long amount){
        if(player==null||!atBranch(player))return "Подойдите к городскому банку и откройте его меню.";
        if(amount<=0||amount>1_000_000)return "Некорректная сумма.";
        UUID town=bankTown(player);
        if(town==null)return "Городской банк не найден.";
        if(!payableAt(player.getUUID(),town))
            return "Старый баланс банковского счёта требует сверки физического резерва.";
        long owned=balance(player.getUUID());
        if(owned<amount)return "На счёте "+owned+" медных; для снятия требуется "+amount+".";
        var cash=KingdomPhysicalCash.townVault(player,town,AdministrativeBindingRole.BANK_VAULT);
        if(cash==null)return "Банковский сейф не подключён или не загружен.";
        var admin=KingdomAdministrationData.get(player.getServer());
        long physical=KingdomPhysicalCash.value(cash);
        if(!covered(physical,admin.bankReserve(town),admin.treasuryReserve(town)))
            return "В физическом хранилище меньше монет, чем зарегистрировано на счетах и в казне. Нужна сверка.";
        if(admin.bankReserve(town)<amount)return "Банковский резерв не подтверждает указанную сумму. Нужна сверка.";
        if(physical<amount)return "В сейфе физически недостаточно монет.";
        return "Не получилось выдать нужные монеты: проверьте номиналы в сейфе и место в кошельке.";
    }
    public boolean deposit(ServerPlayer player, long amount) {
        if(!atBranch(player)||amount<=0||amount>1_000_000)return false;
        long previous=balance(player.getUUID());
        UUID town=bankTown(player);
        // Old unbound lodestone branches cannot accept virtual deposits.
        if(town==null||!payableAt(player.getUUID(),town))return false;
        var vault=KingdomPhysicalCash.townVault(player,town,AdministrativeBindingRole.BANK_VAULT);
        var admin=KingdomAdministrationData.get(player.getServer());
        if(vault==null||previous>Long.MAX_VALUE-amount||admin.bankReserve(town)>Long.MAX_VALUE-amount
                ||!covered(KingdomPhysicalCash.value(vault),admin.bankReserve(town),admin.treasuryReserve(town))
                ||!KingdomPhysicalCash.deposit(player,vault,amount))return false;
        if(!admin.depositBankReserve(town,amount))
            throw new IllegalStateException("Bank reserve update failed after physical deposit");
        accounts.put(player.getUUID(),previous+amount);
        accountHomes.put(player.getUUID(),town);
        player.getInventory().setChanged();setDirty();return true;
    }
    public boolean withdraw(ServerPlayer player,long amount) {
        if(!atBranch(player)||amount<=0||amount>1_000_000||balance(player.getUUID())<amount)return false;
        UUID town=bankTown(player);
        if(town==null||!payableAt(player.getUUID(),town))return false;
        var admin=KingdomAdministrationData.get(player.getServer());
        var vault=KingdomPhysicalCash.townVault(player,town,AdministrativeBindingRole.BANK_VAULT);
        if(vault==null||admin.bankReserve(town)<amount
                ||!covered(KingdomPhysicalCash.value(vault),admin.bankReserve(town),admin.treasuryReserve(town))
                ||!KingdomPhysicalCash.withdraw(player,vault,amount))return false;
        if(!admin.withdrawBankReserve(town,amount))
            throw new IllegalStateException("Bank reserve update failed after physical withdrawal");
        accounts.put(player.getUUID(),balance(player.getUUID())-amount);
        player.getInventory().setChanged();setDirty();return true;
    }
    /** Coins physically placed in the bank but not booked to any citizen/city.
     * Never spend bank balances that are already owned by someone.
     */
    public static long freePhysicalCopper(long physical,long bankReserve,long townReserve) {
        if(physical<0||bankReserve<0||townReserve<0
                ||bankReserve>Long.MAX_VALUE-townReserve
                ||physical<bankReserve+townReserve)return -1;
        return physical-(bankReserve+townReserve);
    }

    /** A manager explicitly assigns unclaimed physical coins to the town account.
     * Does NOT withdraw coins or touch any personal receipts.
     */
    public boolean assignFreeVaultCoinsToTown(ServerPlayer manager,UUID town,long amount) {
        if(!mayUseMunicipalAccount(manager,town)||amount<1||amount>1_000_000)return false;
        var vault=KingdomPhysicalCash.townVault(manager,town,AdministrativeBindingRole.BANK_VAULT);
        if(vault==null)return false;
        var admin=KingdomAdministrationData.get(manager.getServer());
        long free=freePhysicalCopper(KingdomPhysicalCash.value(vault),admin.bankReserve(town),admin.treasuryReserve(town));
        if(free<amount)return false;
        return KingdomCivicsData.get(manager.getServer()).creditTownBankFunds(town,amount);
    }

    public String freeVaultCoinsProblem(ServerPlayer manager,UUID town,long amount) {
        if(!mayUseMunicipalAccount(manager,town))return "Нужны права управляющего банка и доступ к своему городскому банку.";
        var vault=KingdomPhysicalCash.townVault(manager,town,AdministrativeBindingRole.BANK_VAULT);
        if(vault==null)return "Хранилище банка не загружено или не привязано.";
        var admin=KingdomAdministrationData.get(manager.getServer());
        long free=freePhysicalCopper(KingdomPhysicalCash.value(vault),admin.bankReserve(town),admin.treasuryReserve(town));
        if(free<0)return "Физические монеты не покрывают зарегистрированные обязательства. Нужна сверка банка.";
        return "Свободных монет: "+free+". Вклады игроков и уже зачисленные средства города нельзя списывать повторно.";
    }

    /** Explicit founder-approved personal-to-city transfer from the expansion map.
     * Both receipts share a verified physical city vault. The player must own
     * the personal receipt; no bank desk proximity is needed for an account transfer.
     */
    public boolean fundExpansionFromPersonal(ServerPlayer founder,UUID town,long amount) {
        if(founder==null||town==null||amount<1||amount>1_000_000
                ||founder.getServer()==null||!founder.getServer().isSameThread())return false;
        var admin=KingdomAdministrationData.get(founder.getServer());
        var civics=KingdomCivicsData.get(founder.getServer());
        var city=civics.townOf(founder.getUUID());
        if(city==null||!town.equals(city.id())||!founder.getUUID().equals(admin.founder(town)))
            return false;
        return transferPersonalToTown(founder,town,amount);
    }

    private boolean transferPersonalToTown(ServerPlayer player,UUID town,long amount) {
        if(player==null||town==null||!payableAt(player.getUUID(),town))return false;
        var vault=KingdomPhysicalCash.townVault(player,town,AdministrativeBindingRole.BANK_VAULT);
        if(vault==null)return false;
        var admin=KingdomAdministrationData.get(player.getServer());
        var civics=KingdomCivicsData.get(player.getServer());
        var next=TownBankFunds.plan(municipalSnapshot(player,town,vault),
                TownBankFunds.Operation.DONATE,amount);
        if(next==null)return false;
        if(!civics.creditTownBankFunds(town,amount))return false;
        if(!admin.withdrawBankReserve(town,amount)){
            if(!civics.withdrawTownAuthorized(town,player.getUUID(),amount))
                throw new IllegalStateException("Bank transfer compensation failed");
            return false;
        }
        accounts.put(player.getUUID(),next.personal());
        setDirty();
        return true;
    }

    /** A mayor/founder/treasurer manages town funds, never other citizens' personal receipts. */
    public boolean mayUseMunicipalAccount(ServerPlayer player,UUID town) {
        if(player==null||town==null||!town.equals(bankTown(player)))return false;
        var city=KingdomCivicsData.get(player.getServer()).townById(town);
        var admin=KingdomAdministrationData.get(player.getServer());
        return city!=null&&ru.projectkingdom.administration.AdministrativePermissionService.mayManage(
                player.getUUID(),admin.founder(town),city.mayor(),admin.roles(town,player.getUUID()),AdministrativeKind.TREASURY);
    }
    private TownBankFunds.Balances municipalSnapshot(ServerPlayer player,UUID town,net.minecraft.world.Container vault) {
        var city=KingdomCivicsData.get(player.getServer()).townById(town);
        if(city==null)return null;
        var admin=KingdomAdministrationData.get(player.getServer());
        return new TownBankFunds.Balances(balance(player.getUUID()),admin.bankReserve(town),
                city.treasury(),admin.treasuryReserve(town),KingdomPhysicalCash.value(vault));
    }
    public boolean municipalOperation(ServerPlayer player,UUID town,long amount,TownBankFunds.Operation operation) {
        if(!mayUseMunicipalAccount(player,town)||operation==null)return false;
        if(operation==TownBankFunds.Operation.DONATE)
            return transferPersonalToTown(player,town,amount);
        var vault=KingdomPhysicalCash.townVault(player,town,AdministrativeBindingRole.BANK_VAULT);
        if(vault==null)return false;
        var next=TownBankFunds.plan(municipalSnapshot(player,town,vault),operation,amount);
        if(next==null)return false;
        var civics=KingdomCivicsData.get(player.getServer());
        var admin=KingdomAdministrationData.get(player.getServer());
        if(operation==TownBankFunds.Operation.DEPOSIT){
            if(!KingdomPhysicalCash.deposit(player,vault,amount))return false;
            if(!civics.creditTownBankFunds(town,amount)){
                if(!KingdomPhysicalCash.withdraw(player,vault,amount))throw new IllegalStateException("Town deposit compensation failed");
                return false;
            }
        }else if(operation==TownBankFunds.Operation.WITHDRAW){
            if(!KingdomPhysicalCash.withdraw(player,vault,amount))return false;
            if(!civics.withdrawTownAuthorized(town,player.getUUID(),amount)){
                if(!KingdomPhysicalCash.deposit(player,vault,amount))throw new IllegalStateException("Town withdrawal compensation failed");
                return false;
            }
        }
        player.getInventory().setChanged();return true;
    }
    public String municipalProblem(ServerPlayer player,UUID town,long amount,TownBankFunds.Operation operation) {
        if(!mayUseMunicipalAccount(player,town))return "Счёт поселения доступен мэру, основателю и казначею в банке своего города.";
        if(amount<1||amount>1_000_000)return "Сумма: от 1 до 1 000 000 медных.";
        var vault=KingdomPhysicalCash.townVault(player,town,AdministrativeBindingRole.BANK_VAULT);
        if(vault==null)return "Банковская комната не подключена или не загружена.";
        var b=municipalSnapshot(player,town,vault);
        if(b==null)return "Поселение не найдено.";
        if(operation==TownBankFunds.Operation.DONATE&&!payableAt(player.getUUID(),town))
            return "Личный счёт находится в другом банке или требует сверки.";
        if(operation==TownBankFunds.Operation.DEPOSIT&&KingdomCurrency.balance(player)<amount)
            return "В личном кошельке "+KingdomCurrency.balance(player)+" медных. Деньги банковского счёта передаются кнопкой «Из личного — поселению».";
        if(operation==TownBankFunds.Operation.DONATE&&b.personal()<amount)
            return "На вашем личном счёте "+b.personal()+" медных; передать нужно "+amount+".";
        if(operation==TownBankFunds.Operation.WITHDRAW&&Math.min(b.municipal(),b.municipalReserve())<amount)
            return "На счёте поселения "+Math.min(b.municipal(),b.municipalReserve())+" медных. Личные вклады жителей не расходуются.";
        if(TownBankFunds.plan(b,operation,amount)==null)return "Банковские резервы не подтверждают операцию; нужна сверка остатков.";
        return "Проверьте место в кошельке и хранилище и доступность нужных номиналов.";
    }

    /** Cash paid over a physical bank counter for a named lease. No bank-balance transfers.
     * The recipient receives a backed storage receipt and must visit a branch to withdraw.
     */
    public boolean payLeaseCash(ServerPlayer payer, String dimension, int x, int z, boolean renewal) {
        if(!atBranch(payer))return false;
        UUID bank=bankTown(payer);
        var physical=KingdomPhysicalCash.townVault(payer,bank,AdministrativeBindingRole.BANK_VAULT);
        if(physical==null)return false;
        var financial=KingdomAdministrationData.get(payer.getServer());
        if(!covered(KingdomPhysicalCash.value(physical),financial.bankReserve(bank),
                financial.treasuryReserve(bank)))return false;
        var civics=KingdomCivicsData.get(payer.getServer());
        long now=System.currentTimeMillis();
        civics.clearExpiredLease(dimension,x,z,now);
        var offer=civics.leaseOffer(dimension,x,z);
        var contract=civics.leaseContract(dimension,x,z);
        if(renewal ? !civics.canRenewLease(payer.getUUID(),dimension,x,z,now)
                   : !civics.canAcceptLease(payer.getUUID(),dimension,x,z,now))return false;
        UUID recipient=renewal?contract.landlord():offer.landlord();
        long amount=renewal?contract.paidCopper():offer.priceCopper();
        long prior=balance(recipient);
        if(!payableAt(recipient,bank))return false;
        if(amount<=0||amount>1_000_000
                ||KingdomCurrency.balance(payer)<amount
                ||prior>Long.MAX_VALUE-amount
                ||KingdomAdministrationData.get(payer.getServer()).bankReserve(bank)>Long.MAX_VALUE-amount)return false;
        if(!KingdomPhysicalCash.deposit(payer,physical,amount))return false;
        boolean recorded=renewal
                ? civics.renewLease(payer.getUUID(),dimension,x,z,now,amount,7L*24*60*60*1000)
                : civics.acceptLease(payer.getUUID(),dimension,x,z,now);
        if(!recorded){
            if(!KingdomPhysicalCash.withdraw(payer,physical,amount))
                throw new IllegalStateException("Bank lease payment compensation failed");
            return false;
        }
        if(!KingdomAdministrationData.get(payer.getServer()).depositBankReserve(bank,amount))
            throw new IllegalStateException("Bank lease reserve update failed after payment");
        accounts.put(recipient,prior+amount);
        accountHomes.put(recipient,bank);
        payer.getInventory().setChanged();setDirty();return true;
    }
    /** Settle an assessed rent debt using coins presented at a real bank branch.
     * Never debit the tenant's bank account; the landlord receives a backed receipt.
     * The caller may pay less than the outstanding amount.
     */
    public boolean payLeaseDebtCash(ServerPlayer payer,String dimension,int x,int z,long amount) {
        if(!atBranch(payer)||amount<=0||amount>1_000_000)return false;
        UUID bank=bankTown(payer);
        var physical=KingdomPhysicalCash.townVault(payer,bank,AdministrativeBindingRole.BANK_VAULT);
        if(physical==null)return false;
        var financial=KingdomAdministrationData.get(payer.getServer());
        if(!covered(KingdomPhysicalCash.value(physical),financial.bankReserve(bank),
                financial.treasuryReserve(bank)))return false;
        var civics=KingdomCivicsData.get(payer.getServer());
        civics.clearExpiredLease(dimension,x,z,System.currentTimeMillis());
        if(!civics.canPayLeaseDebt(payer.getUUID(),dimension,x,z,amount))return false;
        var debt=civics.leaseDebt(dimension,x,z);
        UUID landlord=debt.landlord();
        long previous=balance(landlord);
        if(!payableAt(landlord,bank))return false;
        if(KingdomCurrency.balance(payer)<amount
                ||previous>Long.MAX_VALUE-amount
                ||KingdomAdministrationData.get(payer.getServer()).bankReserve(bank)>Long.MAX_VALUE-amount)return false;
        if(!KingdomPhysicalCash.deposit(payer,physical,amount))return false;
        if(!civics.recordLeaseDebtPayment(payer.getUUID(),dimension,x,z,amount)) {
            if(!KingdomPhysicalCash.withdraw(payer,physical,amount))
                throw new IllegalStateException("Bank debt payment compensation failed");
            return false;
        }
        if(!KingdomAdministrationData.get(payer.getServer()).depositBankReserve(bank,amount))
            throw new IllegalStateException("Bank debt reserve update failed after payment");
        accounts.put(landlord,previous+amount);
        accountHomes.put(landlord,bank);
        payer.getInventory().setChanged();setDirty();return true;
    }
    /** Buyer brings coins to a real bank counter. Seller receives a backed deposit receipt. */
    public boolean payLandSaleCash(ServerPlayer buyer,String dimension,int x,int z) {
        if(!atBranch(buyer))return false;
        UUID bank=bankTown(buyer);
        var physical=KingdomPhysicalCash.townVault(buyer,bank,AdministrativeBindingRole.BANK_VAULT);
        if(physical==null)return false;
        var financial=KingdomAdministrationData.get(buyer.getServer());
        if(!covered(KingdomPhysicalCash.value(physical),financial.bankReserve(bank),
                financial.treasuryReserve(bank)))return false;
        var civics=KingdomCivicsData.get(buyer.getServer());
        if(!civics.canBuySale(buyer.getUUID(),dimension,x,z))return false;
        var offer=civics.saleOffer(dimension,x,z);
        var parcel=civics.plot(buyer.serverLevel(),new BlockPos(x<<4,64,z<<4));
        var city=parcel==null?null:civics.townById(parcel.town());
        // Public municipal title transfers only via the local hall's reservation.
        if(city!=null&&offer.seller().equals(city.mayor()))return false;
        UUID seller=offer.seller();long amount=offer.priceCopper();
        long previous=balance(seller);
        if(!payableAt(seller,bank))return false;
        if(amount<=0||amount>1_000_000
                ||KingdomCurrency.balance(buyer)<amount
                ||previous>Long.MAX_VALUE-amount
                ||KingdomAdministrationData.get(buyer.getServer()).bankReserve(bank)>Long.MAX_VALUE-amount)return false;
        if(!KingdomPhysicalCash.deposit(buyer,physical,amount))return false;
        if(!civics.completeSale(buyer.getUUID(),dimension,x,z)) {
            if(!KingdomPhysicalCash.withdraw(buyer,physical,amount))
                throw new IllegalStateException("Bank land payment compensation failed");
            return false;
        }
        if(!KingdomAdministrationData.get(buyer.getServer()).depositBankReserve(bank,amount))
            throw new IllegalStateException("Bank land reserve update failed after payment");
        accounts.put(seller,previous+amount);
        accountHomes.put(seller,bank);
        buyer.getInventory().setChanged();setDirty();return true;
    }
    /** A bank branch must actually exist within interaction distance. */
    public boolean atBranch(ServerPlayer player) {
        if(bankTown(player)!=null)return true;
        ServerLevel level=player.serverLevel();
        BlockPos pos=player.blockPosition();
        for(int dx=-5;dx<=5;dx++)for(int dy=-4;dy<=4;dy++)for(int dz=-5;dz<=5;dz++){
            if(branch(level,pos.offset(dx,dy,dz)))return true;
        }
        return false;
    }
    public UUID bankTown(ServerPlayer player){
        ServerLevel level=player.serverLevel();BlockPos from=player.blockPosition();KingdomAdministrationData data=KingdomAdministrationData.get(player.getServer());
        for(var town:KingdomCivicsData.get(player.getServer()).towns()){
            var binding=data.core(town.id(),AdministrativeKind.BANK);if(binding==null||!binding.dimension().equals(level.dimension().location().toString()))continue;
            BlockPos pos=BlockPos.of(binding.packedPos());
            if(pos.distSqr(from)>64||!level.hasChunkAt(pos))continue;
            if(level.getBlockEntity(pos) instanceof AdministrativeCoreBlockEntity core&&core.bound()&&town.id().equals(core.townId())&&core.kind()==AdministrativeKind.BANK)return town.id();
        }
        return null;
    }
    private static KingdomBankData load(CompoundTag root) {
        KingdomBankData data=new KingdomBankData();
        ListTag list=root.getList("Accounts",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++) {
            CompoundTag row=list.getCompound(i);
            if(row.hasUUID("Owner"))data.accounts.put(row.getUUID("Owner"),Math.max(0,row.getLong("Amount")));
        }
        for(Tag value:root.getList("LegacyClaims",Tag.TAG_COMPOUND)){
            CompoundTag row=(CompoundTag)value;
            if(row.hasUUID("Owner"))data.legacyAccounts.put(row.getUUID("Owner"),Math.max(0,row.getLong("Amount")));
        }
        for(Tag value:root.getList("AccountHomes",Tag.TAG_COMPOUND)) {
            CompoundTag row=(CompoundTag)value;
            if(row.hasUUID("Owner")&&row.hasUUID("Bank"))
                data.accountHomes.put(row.getUUID("Owner"),row.getUUID("Bank"));
        }
        if(root.contains("LegacyVault",Tag.TAG_LONG))data.vault=Math.max(0,root.getLong("LegacyVault"));
        else for(long amount:data.accounts.values())data.vault=Math.addExact(data.vault,amount);
        ListTag locations=root.getList("Branches",Tag.TAG_COMPOUND);
        for(int i=0;i<locations.size();i++) {
            CompoundTag row=locations.getCompound(i);
            if(row.hasUUID("Owner"))data.branches.put(row.getString("Location"),row.getUUID("Owner"));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root) {
        ListTag list=new ListTag();
        for(var entry:accounts.entrySet()) {
            CompoundTag row=new CompoundTag();row.putUUID("Owner",entry.getKey());row.putLong("Amount",entry.getValue());list.add(row);
        }
        root.put("Accounts",list);
        ListTag homeTags=new ListTag();
        for(var entry:accountHomes.entrySet()) {
            CompoundTag row=new CompoundTag();row.putUUID("Owner",entry.getKey());row.putUUID("Bank",entry.getValue());homeTags.add(row);
        }
        root.put("AccountHomes",homeTags);
        ListTag oldAccounts=new ListTag();
        for(var entry:legacyAccounts.entrySet()){
            CompoundTag row=new CompoundTag();row.putUUID("Owner",entry.getKey());
            row.putLong("Amount",entry.getValue());oldAccounts.add(row);
        }
        root.put("LegacyClaims",oldAccounts);
        ListTag locations=new ListTag();
        for(var entry:branches.entrySet()) {
            CompoundTag row=new CompoundTag();row.putString("Location",entry.getKey());row.putUUID("Owner",entry.getValue());locations.add(row);
        }
        root.put("Branches",locations);root.putLong("LegacyVault",vault);return root;
    }
}
