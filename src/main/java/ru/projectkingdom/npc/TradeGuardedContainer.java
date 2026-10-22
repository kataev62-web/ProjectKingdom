package ru.projectkingdom.npc;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Container boundary for managed inventories. Vanilla menus and hoppers that
 * use this wrapper respect enterprise projection locks. Do not expose the
 * underlying Container to other inventory APIs or item capabilities.
 *
 * This is not a replacement for capability-level protection or durable commits.
 */
public final class TradeGuardedContainer implements Container {
    private final Container delegate;
    private final TradeManagedInventory managed;
    private final TradeProtectedLocations.Location location;
    private final UUID account;

    public TradeGuardedContainer(Container delegate, TradeManagedInventory managed,
                                 TradeProtectedLocations.Location location, UUID account) {
        this.delegate = Objects.requireNonNull(delegate);
        this.managed = Objects.requireNonNull(managed);
        this.location = Objects.requireNonNull(location);
        this.account = Objects.requireNonNull(account);
        if (!account.equals(managedOwner())) throw new IllegalArgumentException("container not enrolled for account");
    }

    private UUID managedOwner() { return managed.owner(location); }
    private boolean permitted() { return managed.canMutate(location, account); }

    @Override public int getContainerSize() { return delegate.getContainerSize(); }
    @Override public boolean isEmpty() { return delegate.isEmpty(); }
    @Override public ItemStack getItem(int slot) {
        // Never expose a mutable reference to the underlying stack.
        return delegate.getItem(slot).copy();
    }
    @Override public ItemStack removeItem(int slot, int amount) {
        if (!permitted()) return ItemStack.EMPTY;
        final ItemStack[] result = {ItemStack.EMPTY};
        if (!managed.mutate(location, account, () -> result[0] = delegate.removeItem(slot, amount))) return ItemStack.EMPTY;
        return result[0];
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        if (!permitted()) return ItemStack.EMPTY;
        final ItemStack[] result = {ItemStack.EMPTY};
        if (!managed.mutate(location, account, () -> result[0] = delegate.removeItemNoUpdate(slot))) return ItemStack.EMPTY;
        return result[0];
    }
    @Override public void setItem(int slot, ItemStack stack) {
        Objects.requireNonNull(stack);
        managed.mutate(location, account, () -> delegate.setItem(slot, stack.copy()));
    }
    @Override public void setChanged() { if (permitted()) delegate.setChanged(); }
    @Override public boolean stillValid(Player player) { return delegate.stillValid(player); }
    @Override public void clearContent() { managed.mutate(location, account, delegate::clearContent); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return permitted() && delegate.canPlaceItem(slot, stack);
    }
    @Override public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return permitted() && delegate.canTakeItem(target, slot, stack);
    }
    @Override public void startOpen(Player player) { delegate.startOpen(player); }
    @Override public void stopOpen(Player player) { delegate.stopOpen(player); }
    @Override public int getMaxStackSize() { return delegate.getMaxStackSize(); }
}
