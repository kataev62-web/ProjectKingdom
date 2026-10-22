package ru.projectkingdom.npc;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

/** Server-thread-only physical four-container implementation. A transaction MUST be
 * coordinated by DemandDeliveryCoordinator's durable quarantine and exclusive lock.
 * Do not expose these containers to automation during settlement. No rollback is
 * attempted after an uncertain write: the coordinator must quarantine the delivery.
 * This adapter supports a single physical copper coin item (value = 1); other
 * denominations need an explicit change-making policy before enabling them.
 */
public final class DemandMinecraftContainerPorts implements DemandPhysicalInventoryExchange.Ports {
    private final UUID enterprise;
    private final Container supplier, warehouse, cashbox, wallet;
    private final Item copperCoin;
    private final Thread ownerThread;
    public DemandMinecraftContainerPorts(UUID enterprise, Container supplier, Container warehouse,
                                          Container cashbox, Container wallet, Item copperCoin) {
        this.enterprise = Objects.requireNonNull(enterprise);
        this.supplier = Objects.requireNonNull(supplier);
        this.warehouse = Objects.requireNonNull(warehouse);
        this.cashbox = Objects.requireNonNull(cashbox);
        this.wallet = Objects.requireNonNull(wallet);
        this.copperCoin = Objects.requireNonNull(copperCoin);
        if (supplier == warehouse || supplier == cashbox || supplier == wallet ||
            warehouse == cashbox || warehouse == wallet || cashbox == wallet)
            throw new IllegalArgumentException("four distinct containers required");
        this.ownerThread = Thread.currentThread();
    }
    private void checkThread() throws IOException {
        if (Thread.currentThread() != ownerThread) throw new IOException("settlement outside owner server thread");
    }
    private void checkEnterprise(UUID id) throws IOException {
        if (!enterprise.equals(id)) throw new IOException("wrong enterprise");
    }
    private static ItemStack prototype(String id) throws IOException {
        var key = new net.minecraft.resources.ResourceLocation(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) throw new IOException("unknown item: " + id);
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(key));
        if (stack.isEmpty()) throw new IOException("empty item: " + id);
        return stack;
    }
    private static boolean matches(ItemStack stack, ItemStack sample) {
        return !stack.isEmpty() && ItemStack.isSameItemSameTags(stack, sample);
    }
    private static long count(Container c, ItemStack item) {
        long total = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (matches(c.getItem(i), item))
            total += c.getItem(i).getCount();
        return total;
    }
    private static long space(Container c, ItemStack item) {
        long total = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack slot = c.getItem(i);
            if (slot.isEmpty() && c.canPlaceItem(i, item)) total += Math.min(c.getMaxStackSize(), item.getMaxStackSize());
            else if (matches(slot, item) && c.canPlaceItem(i, item)) total += Math.max(0, Math.min(c.getMaxStackSize(), slot.getMaxStackSize()) - slot.getCount());
        }
        return total;
    }
    private static void remove(Container c, ItemStack sample, long amount) throws IOException {
        if (amount < 0 || count(c, sample) < amount) throw new IOException("insufficient physical items");
        for (int i = 0; i < c.getContainerSize() && amount > 0; i++) {
            ItemStack slot = c.getItem(i);
            if (!matches(slot, sample)) continue;
            int take = (int)Math.min(amount, slot.getCount());
            ItemStack next = slot.copy(); next.shrink(take);
            c.setItem(i, next); amount -= take;
        }
        if (amount != 0) throw new IOException("incomplete removal");
        c.setChanged();
    }
    private static void insert(Container c, ItemStack sample, long amount) throws IOException {
        if (amount < 0 || space(c, sample) < amount) throw new IOException("insufficient physical space");
        for (int i = 0; i < c.getContainerSize() && amount > 0; i++) {
            ItemStack slot = c.getItem(i);
            if ((!slot.isEmpty() && !matches(slot, sample)) || !c.canPlaceItem(i, sample)) continue;
            int capacity = Math.min(c.getMaxStackSize(), sample.getMaxStackSize());
            int free = capacity - (slot.isEmpty() ? 0 : slot.getCount());
            if (free <= 0) continue;
            int add = (int)Math.min(amount, free);
            ItemStack next = slot.isEmpty() ? sample.copy() : slot.copy();
            next.setCount((slot.isEmpty() ? 0 : slot.getCount()) + add);
            c.setItem(i, next); amount -= add;
        }
        if (amount != 0) throw new IOException("incomplete insertion");
        c.setChanged();
    }
    private ItemStack coin() { return new ItemStack(copperCoin); }
    @Override public long cashboxCopper(UUID id) throws IOException {
        checkThread(); checkEnterprise(id); return count(cashbox, coin());
    }
    @Override public DemandPhysicalTransferPlan.Snapshot snapshot(EnterpriseDemandBoard.Listing listing,
            EnterpriseDemandBoard.Delivery delivery) throws IOException {
        checkThread(); checkEnterprise(listing.key().enterprise());
        ItemStack goods = prototype(listing.key().item());
        return new DemandPhysicalTransferPlan.Snapshot(listing.key().item(), count(supplier, goods),
                space(warehouse, goods), count(cashbox, coin()), space(wallet, coin()));
    }
    @Override public void moveGoods(EnterpriseDemandBoard.Listing listing, int quantity) throws IOException {
        checkThread(); checkEnterprise(listing.key().enterprise());
        ItemStack goods = prototype(listing.key().item());
        if (count(supplier, goods) < quantity || space(warehouse, goods) < quantity)
            throw new IOException("goods preflight changed");
        remove(supplier, goods, quantity); insert(warehouse, goods, quantity);
    }
    @Override public void moveCopper(UUID id, long copper) throws IOException {
        checkThread(); checkEnterprise(id);
        if (count(cashbox, coin()) < copper || space(wallet, coin()) < copper)
            throw new IOException("cash preflight changed");
        remove(cashbox, coin(), copper); insert(wallet, coin(), copper);
    }
}
