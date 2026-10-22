package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.projectkingdom.economy.MoneyDenomination;
import ru.projectkingdom.economy.MoneyPurse;
import ru.projectkingdom.economy.MoneyPurseNbt;
import ru.projectkingdom.economy.PurseTransaction;

/** Physical, conserved, slot-ordered multi-purse money. Values use copper units. */
public final class KingdomCurrency {
    private KingdomCurrency() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, KingdomNpcMod.MOD_ID);
    public static final RegistryObject<Item> COPPER = ITEMS.register("copper_coin", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SILVER = ITEMS.register("silver_coin", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> GOLD = ITEMS.register("gold_coin", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> WALLET = ITEMS.register("wallet",
            () -> new KingdomWalletItem(new Item.Properties().stacksTo(16)));
    private static final long MAX_OPERATION = 1_000_000;

    public record PurseSlot(int slot, ItemStack stack, MoneyPurse purse) {}
    private record CreditPlan(List<PurseSlot> existing, PurseTransaction.Result result,
                              ItemStack splitSource, int splitTarget) {}

    public static void register() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    public static long value(ItemStack stack) {
        if (stack.is(COPPER.get())) return MoneyDenomination.COPPER.copperValue();
        if (stack.is(SILVER.get())) return MoneyDenomination.SILVER.copperValue();
        if (stack.is(GOLD.get())) return MoneyDenomination.GOLD.copperValue();
        return 0;
    }

    public static Item coin(MoneyDenomination denomination) {
        return switch (denomination) {
            case COPPER -> COPPER.get();
            case SILVER -> SILVER.get();
            case GOLD -> GOLD.get();
        };
    }

    /** Compatibility accessor: new operations should use player-level overloads. */
    public static ItemStack wallet(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(WALLET.get())) return stack;
        }
        return ItemStack.EMPTY;
    }

    public static long balance(ItemStack wallet) {
        return wallet.is(WALLET.get()) ? safeValue(MoneyPurseNbt.read(wallet)) : 0;
    }

    public static long balance(ServerPlayer player) {
        long total = 0;
        for (PurseSlot slot : purseSlots(player)) {
            try { total = Math.addExact(total, slot.purse().valueCopper()); }
            catch (ArithmeticException overflow) { return Long.MAX_VALUE; }
        }
        return total;
    }

    public static boolean credit(ItemStack wallet, long amount) {
        if (!wallet.is(WALLET.get()) || wallet.getCount() != 1 || !validAmount(amount)) return false;
        PurseTransaction.Result result = PurseTransaction.credit(List.of(MoneyPurseNbt.read(wallet)), amount);
        if (!result.committed()) return false;
        MoneyPurseNbt.write(wallet, result.purses().get(0));
        return true;
    }

    public static boolean debit(ItemStack wallet, long amount) {
        if (!wallet.is(WALLET.get()) || wallet.getCount() != 1 || !validAmount(amount)) return false;
        PurseTransaction.Result result = PurseTransaction.debit(List.of(MoneyPurseNbt.read(wallet)), amount);
        if (!result.committed()) return false;
        MoneyPurseNbt.write(wallet, result.purses().get(0));
        return true;
    }

    public static boolean credit(ServerPlayer player, long amount) {
        CreditPlan plan = planCredit(player, amount);
        if (plan == null) return false;
        applyCredit(player, plan);
        return true;
    }

    public static boolean debit(ServerPlayer player, long amount) {
        if (!validAmount(amount)) return false;
        List<PurseSlot> slots = purseSlots(player);
        PurseTransaction.Result result = PurseTransaction.debit(states(slots), amount);
        if (!result.committed()) return false;
        apply(slots, result.purses());
        player.getInventory().setChanged();
        return true;
    }

    /** Exact transfer planned on snapshots; neither player mutates unless both sides fit. */
    public static boolean transfer(ServerPlayer sender, ServerPlayer receiver, long amount) {
        if (sender == null || receiver == null || sender == receiver || !validAmount(amount)) return false;
        List<PurseSlot> senderSlots = purseSlots(sender);
        PurseTransaction.Result debited = PurseTransaction.debit(states(senderSlots), amount);
        if (!debited.committed()) return false;
        CreditPlan credited = planCredit(receiver, amount);
        if (credited == null) return false;
        apply(senderSlots, debited.purses());
        applyCredit(receiver, credited);
        sender.getInventory().setChanged();
        receiver.getInventory().setChanged();
        return true;
    }

    /** Separates exactly one empty purse from a stack before a slot-bound menu opens. */
    public static int separateForUse(ServerPlayer player, ItemStack held) {
        Inventory inventory = player.getInventory();
        int heldSlot = identitySlot(inventory, held);
        if (heldSlot < 0 || !held.is(WALLET.get())) return -1;
        if (held.getCount() == 1) return heldSlot;
        if (MoneyPurseNbt.read(held).coinCount() != 0) return -1;
        int free = inventory.getFreeSlot();
        if (free < 0) return -1;
        held.shrink(1);
        inventory.setItem(free, new ItemStack(WALLET.get()));
        inventory.setChanged();
        return free;
    }

    /** Collects loose coins into the selected purse until its 1000-coin capacity is reached. */
    public static long collect(ServerPlayer player, int purseSlot) {
        Inventory inventory = player.getInventory();
        if (purseSlot < 0 || purseSlot >= inventory.getContainerSize()) return 0;
        ItemStack stack = inventory.getItem(purseSlot);
        if (!stack.is(WALLET.get()) || stack.getCount() != 1) return 0;
        MoneyPurse purse = MoneyPurseNbt.read(stack);
        if (purse.legacyOverflow()) return 0;
        long collected = 0;
        for (int slot = 0; slot < inventory.getContainerSize() && purse.coinCount() < MoneyPurse.MAX_COINS; slot++) {
            ItemStack coins = inventory.getItem(slot);
            MoneyDenomination denomination = denomination(coins);
            if (denomination == null) continue;
            long accepted = Math.min(coins.getCount(), MoneyPurse.MAX_COINS - purse.coinCount());
            if (accepted <= 0) continue;
            purse = purse.add(denomination, accepted);
            coins.shrink((int) accepted);
            collected = Math.addExact(collected, Math.multiplyExact(accepted, denomination.copperValue()));
        }
        MoneyPurseNbt.write(stack, purse);
        inventory.setChanged();
        return collected;
    }

    public static long collect(ServerPlayer player) {
        for (PurseSlot slot : purseSlots(player)) return collect(player, slot.slot());
        return 0;
    }

    /** Withdraws one physical coin from a specific purse, making conserved change if required. */
    public static boolean withdraw(ServerPlayer player, int purseSlot, MoneyDenomination denomination) {
        Inventory inventory = player.getInventory();
        if (denomination == null || purseSlot < 0 || purseSlot >= inventory.getContainerSize()) return false;
        ItemStack stack = inventory.getItem(purseSlot);
        if (!stack.is(WALLET.get()) || stack.getCount() != 1) return false;
        PurseTransaction.Result result = PurseTransaction.debit(
                List.of(MoneyPurseNbt.read(stack)), denomination.copperValue());
        if (!result.committed()) return false;
        if (!inventory.add(new ItemStack(coin(denomination)))) return false;
        MoneyPurseNbt.write(stack, result.purses().get(0));
        inventory.setChanged();
        return true;
    }

    public static boolean withdraw(ServerPlayer player, long amount) {
        MoneyDenomination denomination = amount == 1 ? MoneyDenomination.COPPER
                : amount == 100 ? MoneyDenomination.SILVER
                : amount == 10_000 ? MoneyDenomination.GOLD : null;
        if (denomination == null) return false;
        for (PurseSlot slot : purseSlots(player)) {
            if (withdraw(player, slot.slot(), denomination)) return true;
        }
        return false;
    }

    private static CreditPlan planCredit(ServerPlayer player, long amount) {
        if (!validAmount(amount)) return null;
        List<PurseSlot> existing = purseSlots(player);
        PurseTransaction.Result direct = PurseTransaction.credit(states(existing), amount);
        if (direct.committed()) return new CreditPlan(existing, direct, ItemStack.EMPTY, -1);
        Inventory inventory = player.getInventory();
        int free = inventory.getFreeSlot();
        if (free < 0) return null;
        ItemStack source = ItemStack.EMPTY;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (candidate.is(WALLET.get()) && candidate.getCount() > 1
                    && MoneyPurseNbt.read(candidate).coinCount() == 0) {
                source = candidate;
                break;
            }
        }
        if (source.isEmpty()) return null;
        ArrayList<MoneyPurse> withSeparated = new ArrayList<>(states(existing));
        withSeparated.add(MoneyPurse.empty());
        PurseTransaction.Result separated = PurseTransaction.credit(withSeparated, amount);
        return separated.committed() ? new CreditPlan(existing, separated, source, free) : null;
    }

    private static void applyCredit(ServerPlayer player, CreditPlan plan) {
        int existingCount = plan.existing().size();
        apply(plan.existing(), plan.result().purses().subList(0, existingCount));
        if (!plan.splitSource().isEmpty()) {
            plan.splitSource().shrink(1);
            ItemStack separated = new ItemStack(WALLET.get());
            MoneyPurseNbt.write(separated, plan.result().purses().get(existingCount));
            player.getInventory().setItem(plan.splitTarget(), separated);
        }
        player.getInventory().setChanged();
    }

    private static List<PurseSlot> purseSlots(ServerPlayer player) {
        ArrayList<PurseSlot> result = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(WALLET.get()) && stack.getCount() == 1) {
                result.add(new PurseSlot(slot, stack, MoneyPurseNbt.read(stack)));
            }
        }
        return List.copyOf(result);
    }

    private static List<MoneyPurse> states(List<PurseSlot> slots) {
        return slots.stream().map(PurseSlot::purse).toList();
    }

    private static void apply(List<PurseSlot> slots, List<MoneyPurse> purses) {
        if (slots.size() != purses.size()) throw new IllegalArgumentException("Purse snapshot size changed");
        for (int i = 0; i < slots.size(); i++) MoneyPurseNbt.write(slots.get(i).stack(), purses.get(i));
    }

    private static int identitySlot(Inventory inventory, ItemStack target) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot) == target) return slot;
        }
        return -1;
    }

    private static MoneyDenomination denomination(ItemStack stack) {
        if (stack.is(COPPER.get())) return MoneyDenomination.COPPER;
        if (stack.is(SILVER.get())) return MoneyDenomination.SILVER;
        if (stack.is(GOLD.get())) return MoneyDenomination.GOLD;
        return null;
    }

    private static long safeValue(MoneyPurse purse) {
        try { return purse.valueCopper(); }
        catch (ArithmeticException overflow) { return Long.MAX_VALUE; }
    }

    private static boolean validAmount(long amount) {
        return amount > 0 && amount <= MAX_OPERATION;
    }
}
