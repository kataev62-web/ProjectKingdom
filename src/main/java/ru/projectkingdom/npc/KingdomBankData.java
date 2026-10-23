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
 * Accounts are storage receipts, never a payment method. */
public final class KingdomBankData extends SavedData {
    private static final String KEY = "kingdom_bank_storage_v1";
    private final Map<UUID, Long> accounts = new LinkedHashMap<>();
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
    public boolean deposit(ServerPlayer player, long amount) {
        if(!atBranch(player)||amount<=0||amount>1_000_000)return false;
        long previous=balance(player.getUUID());
        UUID town=bankTown(player);
        // Old unbound lodestone branches cannot accept virtual deposits.
        if(town==null)return false;
        var vault=KingdomPhysicalCash.townVault(player,town,AdministrativeBindingRole.BANK_VAULT);
        var admin=KingdomAdministrationData.get(player.getServer());
        if(vault==null||previous>Long.MAX_VALUE-amount||admin.bankReserve(town)>Long.MAX_VALUE-amount
                ||!KingdomPhysicalCash.deposit(player,vault,amount))return false;
        if(!admin.depositBankReserve(town,amount))
            throw new IllegalStateException("Bank reserve update failed after physical deposit");
        accounts.put(player.getUUID(),previous+amount);
        player.getInventory().setChanged();setDirty();return true;
    }
    public boolean withdraw(ServerPlayer player,long amount) {
        if(!atBranch(player)||amount<=0||amount>1_000_000||balance(player.getUUID())<amount)return false;
        UUID town=bankTown(player);
        if(town==null)return false;
        var admin=KingdomAdministrationData.get(player.getServer());
        var vault=KingdomPhysicalCash.townVault(player,town,AdministrativeBindingRole.BANK_VAULT);
        if(vault==null||admin.bankReserve(town)<amount
                ||KingdomPhysicalCash.value(vault)<admin.bankReserve(town)
                ||!KingdomPhysicalCash.withdraw(player,vault,amount))return false;
        if(!admin.withdrawBankReserve(town,amount))
            throw new IllegalStateException("Bank reserve update failed after physical withdrawal");
        accounts.put(player.getUUID(),balance(player.getUUID())-amount);
        player.getInventory().setChanged();setDirty();return true;
    }
    /** Cash paid over a physical bank counter for a named lease. No bank-balance transfers.
     * The recipient receives a backed storage receipt and must visit a branch to withdraw.
     */
    public boolean payLeaseCash(ServerPlayer payer, String dimension, int x, int z, boolean renewal) {
        if(!atBranch(payer))return false;
        UUID bank=bankTown(payer);
        var physical=KingdomPhysicalCash.townVault(payer,bank,AdministrativeBindingRole.BANK_VAULT);
        if(physical==null)return false;
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
        var civics=KingdomCivicsData.get(payer.getServer());
        civics.clearExpiredLease(dimension,x,z,System.currentTimeMillis());
        if(!civics.canPayLeaseDebt(payer.getUUID(),dimension,x,z,amount))return false;
        var debt=civics.leaseDebt(dimension,x,z);
        UUID landlord=debt.landlord();
        long previous=balance(landlord);
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
        payer.getInventory().setChanged();setDirty();return true;
    }
    /** Buyer brings coins to a real bank counter. Seller receives a backed deposit receipt. */
    public boolean payLandSaleCash(ServerPlayer buyer,String dimension,int x,int z) {
        if(!atBranch(buyer))return false;
        UUID bank=bankTown(buyer);
        var physical=KingdomPhysicalCash.townVault(buyer,bank,AdministrativeBindingRole.BANK_VAULT);
        if(physical==null)return false;
        var civics=KingdomCivicsData.get(buyer.getServer());
        if(!civics.canBuySale(buyer.getUUID(),dimension,x,z))return false;
        var offer=civics.saleOffer(dimension,x,z);
        UUID seller=offer.seller();long amount=offer.priceCopper();
        long previous=balance(seller);
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
        ListTag locations=new ListTag();
        for(var entry:branches.entrySet()) {
            CompoundTag row=new CompoundTag();row.putString("Location",entry.getKey());row.putUUID("Owner",entry.getValue());locations.add(row);
        }
        root.put("Branches",locations);root.putLong("LegacyVault",vault);return root;
    }
}
