package ru.projectkingdom.npc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import ru.projectkingdom.administration.AdministrativeBindingRole;
import ru.projectkingdom.administration.KingdomAdministrationData;

/** Bank-only durable safety gate. Separate from MiniMax delivery SettlementAuthority. */
public final class BankOperations {
    private static final Map<MinecraftServer,BankOperationLedger> JOURNALS=
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<MinecraftServer> FAILED=
            Collections.newSetFromMap(new WeakHashMap<>());
    private BankOperations() {}

    public static BankOperationLedger journal(MinecraftServer server) {
        if(server==null)return null;
        synchronized(JOURNALS){
            if(FAILED.contains(server))return null;
            BankOperationLedger existing=JOURNALS.get(server);
            if(existing!=null)return existing;
            try {
                var path=server.getWorldPath(LevelResource.ROOT)
                        .resolve("data/kingdom_bank_transactions/bank-operations.log");
                BankOperationLedger opened=BankOperationLedger.open(path);
                JOURNALS.put(server,opened);
                return opened;
            }catch(IOException|RuntimeException ex){
                FAILED.add(server);
                System.err.println("[Kingdom bank] Journal unavailable, bank writes disabled: "+ex);
                return null;
            }
        }
    }

    public static boolean blocked(MinecraftServer server,UUID town) {
        BankOperationLedger log=journal(server);
        return log==null||log.blocked(town);
    }

    /**
     * All mutation callbacks run on the Minecraft server thread, after
     * authorization and financial preflight. Every PREPARED is fsynced first.
     */
    public static boolean execute(ServerPlayer actor,UUID town,UUID recipient,
                                  BankOperationLedger.Kind kind,long amount,
                                  BooleanSupplier mutation) {
        if(actor==null||actor.getServer()==null||mutation==null
                ||!actor.getServer().isSameThread()||town==null||recipient==null||kind==null)
            return false;
        boolean atBank=town.equals(KingdomBankData.get(actor.getServer()).bankTown(actor));
        var currentCity=KingdomCivicsData.get(actor.getServer()).townOf(actor.getUUID());
        boolean remoteFounderDonation=kind==BankOperationLedger.Kind.DONATE
                &&currentCity!=null&&town.equals(currentCity.id())
                &&actor.getUUID().equals(
                        KingdomAdministrationData.get(actor.getServer()).founder(town));
        if(!atBank&&!remoteFounderDonation)return false;
        BankOperationLedger log=journal(actor.getServer());
        if(log==null||log.blocked(town))return false;
        var before=snapshot(actor,actor,town,recipient,remoteFounderDonation);
        if(before==null)return false;
        var tx=log.prepare(town,actor.getUUID(),recipient,kind,amount,before);
        if(tx==null)return false;
        boolean success;
        try {success=mutation.getAsBoolean();}
        catch(RuntimeException failure) {
            System.err.println("[Kingdom bank] Operation "+tx.id()+" failed; reconcile bank: "+failure);
            success=false;
        }
        var after=snapshot(actor,actor,town,recipient,remoteFounderDonation);
        if(!success) {
            if(!log.abortUnchanged(tx,after))
                System.err.println("[Kingdom bank] Unresolved transaction "+tx.id());
            return false;
        }
        if(!log.receipt(tx,after)) {
            System.err.println("[Kingdom bank] Unsafe bank completion; quarantine "+tx.id());
            return false;
        }
        return true;
    }

    /** OP-only recovery, with physical inventory and account witness; never replays coins. */
    public static boolean reconcile(ServerPlayer operator,UUID id) {
        if(operator==null||operator.getServer()==null||!operator.getServer().isSameThread()
                ||!operator.hasPermissions(4)||id==null)return false;
        BankOperationLedger log=journal(operator.getServer());
        if(log==null)return false;
        BankOperationLedger.Entry entry=log.find(id);
        if(entry==null||!entry.town().equals(KingdomBankData.get(operator.getServer()).bankTown(operator)))
            return false;
        ServerPlayer actor=operator.getServer().getPlayerList().getPlayer(entry.actor());
        if(actor==null)return false;
        return log.reconcile(id,snapshot(actor,operator,entry.town(),entry.recipient(),false));
    }

    static BankOperationLedger.Snapshot snapshot(ServerPlayer walletOwner,ServerPlayer bankViewer,
                                                 UUID town,UUID recipient,boolean remoteFounderDonation) {
        if(walletOwner==null||bankViewer==null||town==null||recipient==null
                ||(!remoteFounderDonation&&!town.equals(KingdomBankData.get(bankViewer.getServer()).bankTown(bankViewer))))
            return null;
        Container vault=KingdomPhysicalCash.townVault(bankViewer,town,AdministrativeBindingRole.BANK_VAULT);
        if(vault==null)return null;
        var bank=KingdomBankData.get(bankViewer.getServer());
        var admin=KingdomAdministrationData.get(bankViewer.getServer());
        var city=KingdomCivicsData.get(bankViewer.getServer()).townById(town);
        if(city==null)return null;
        long cash=KingdomPhysicalCash.value(vault);
        long wallet=KingdomCurrency.balance(walletOwner);
        if(cash<0||wallet==Long.MAX_VALUE)return null;
        try {
            return new BankOperationLedger.Snapshot(wallet,cash,
                    bank.balance(walletOwner.getUUID()),bank.balance(recipient),
                    admin.bankReserve(town),admin.treasuryReserve(town),city.treasury(),
                    admin.heldExpansionProceeds(town),
                    fingerprint(walletOwner.getInventory()),fingerprint(vault));
        }catch(IllegalArgumentException invalid){return null;}
    }

    private static String fingerprint(Container container) {
        try {
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            for(int i=0;i<container.getContainerSize();i++){
                ItemStack stack=container.getItem(i);
                md.update(Integer.toString(i).getBytes(StandardCharsets.UTF_8));
                md.update((byte)0);
                md.update(stack.save(new CompoundTag()).toString().getBytes(StandardCharsets.UTF_8));
                md.update((byte)0x7f);
            }
            return HexFormat.of().formatHex(md.digest());
        }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
