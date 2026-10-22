package ru.projectkingdom.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Physical, conserved money. Values are expressed in copper coins. */
public final class KingdomCurrency {
    private KingdomCurrency() {}
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, KingdomNpcMod.MOD_ID);
    public static final RegistryObject<Item> COPPER = ITEMS.register("copper_coin", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SILVER = ITEMS.register("silver_coin", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> GOLD = ITEMS.register("gold_coin", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> WALLET = ITEMS.register("wallet", () -> new KingdomWalletItem(new Item.Properties().stacksTo(1)));
    private static final String BALANCE = "KingdomCopperBalance";
    public static void register() { ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus()); }
    public static long value(ItemStack s) {
        if(s.is(COPPER.get())) return 1;
        if(s.is(SILVER.get())) return 100;
        if(s.is(GOLD.get())) return 10000;
        return 0;
    }
    public static long balance(ItemStack wallet) {
        return wallet.is(WALLET.get()) ? Math.max(0,wallet.getOrCreateTag().getLong(BALANCE)) : 0;
    }
    public static boolean credit(ItemStack wallet,long amount) {
        if(!wallet.is(WALLET.get())||amount<=0||balance(wallet)>Long.MAX_VALUE-amount)return false;
        wallet.getOrCreateTag().putLong(BALANCE,balance(wallet)+amount);return true;
    }
    public static boolean debit(ItemStack wallet,long amount) {
        if(!wallet.is(WALLET.get())||amount<=0||balance(wallet)<amount)return false;
        wallet.getOrCreateTag().putLong(BALANCE,balance(wallet)-amount);return true;
    }
    public static ItemStack wallet(ServerPlayer player) {
        for(int i=0;i<player.getInventory().getContainerSize();i++) {
            ItemStack stack=player.getInventory().getItem(i);
            if(stack.is(WALLET.get()))return stack;
        }
        return ItemStack.EMPTY;
    }
    /** Exact wallet-to-wallet transfer. Server thread only; neither wallet is minted or destroyed. */
    public static boolean transfer(ServerPlayer sender, ServerPlayer receiver, long amount) {
        if(sender==null||receiver==null||sender==receiver||amount<=0||amount>1_000_000)return false;
        ItemStack from=wallet(sender),to=wallet(receiver);
        if(from.isEmpty()||to.isEmpty()||balance(from)<amount||balance(to)>Long.MAX_VALUE-amount)return false;
        if(!debit(from,amount))return false;
        if(!credit(to,amount)) {credit(from,amount);return false;}
        sender.getInventory().setChanged();receiver.getInventory().setChanged();return true;
    }
    /** Deposit all physical coins from player inventory into their wallet, no money creation. */
    public static long collect(ServerPlayer player) {
        ItemStack wallet=wallet(player);if(wallet.isEmpty())return 0;
        Inventory inv=player.getInventory();long total=0;
        for(int i=0;i<inv.getContainerSize();i++) {
            ItemStack stack=inv.getItem(i);long unit=value(stack);
            if(unit==0)continue;
            long amount=unit*stack.getCount();
            if(credit(wallet,amount)){total+=amount;stack.setCount(0);}
        }
        inv.setChanged();return total;
    }
    /** Exchange an exact value from wallet into physical coins. Refuses if insufficient inventory space. */
    public static boolean withdraw(ServerPlayer player,long amount) {
        if(amount<=0||amount>1000000)return false;
        ItemStack wallet=wallet(player);if(balance(wallet)<amount)return false;
        // Ensure inventory has space before debiting. Only one denomination at a time, max stack 64.
        Item coin=amount==10000?GOLD.get():amount==100?SILVER.get():amount==1?COPPER.get():null;
        if(coin==null)return false;
        ItemStack out=new ItemStack(coin);
        if(!player.getInventory().add(out))return false;
        if(!debit(wallet,amount)) { // Defensive rollback.
            for(int i=0;i<player.getInventory().getContainerSize();i++) {
                ItemStack stack=player.getInventory().getItem(i);
                if(stack.is(coin)&&stack.getCount()>0){stack.shrink(1);break;}
            }
            return false;
        }
        player.getInventory().setChanged();return true;
    }
}
