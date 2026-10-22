package ru.projectkingdom.npc;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** Capability boundary for an explicitly enrolled enterprise inventory.
 * All automation must receive this wrapper, never the backing handler.
 * This does not protect a backing inventory exposed by another capability.
 */
public final class TradeGuardedItemHandler implements IItemHandler {
    private final IItemHandler backing;
    private final TradeManagedInventory managed;
    private final TradeProtectedLocations.Location location;
    private final UUID account;

    public TradeGuardedItemHandler(IItemHandler backing, TradeManagedInventory managed,
                                   TradeProtectedLocations.Location location, UUID account) {
        this.backing = Objects.requireNonNull(backing);
        this.managed = Objects.requireNonNull(managed);
        this.location = Objects.requireNonNull(location);
        this.account = Objects.requireNonNull(account);
        if (!account.equals(managed.owner(location))) {
            throw new IllegalArgumentException("unregistered or foreign enterprise inventory");
        }
    }

    @Override public int getSlots() { return backing.getSlots(); }
    @Override public ItemStack getStackInSlot(int slot) { return backing.getStackInSlot(slot).copy(); }
    @Override public int getSlotLimit(int slot) { return backing.getSlotLimit(slot); }
    @Override public boolean isItemValid(int slot, ItemStack stack) {
        return managed.canMutate(location, account) && backing.isItemValid(slot, stack);
    }

    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        Objects.requireNonNull(stack);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        final ItemStack[] remainder = {stack.copy()};
        // Even simulations must honor locks, otherwise automation may plan a forbidden insertion.
        managed.mutate(location, account, () -> remainder[0] = backing.insertItem(slot, stack.copy(), simulate).copy());
        return remainder[0];
    }

    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;
        final ItemStack[] extracted = {ItemStack.EMPTY};
        managed.mutate(location, account, () -> extracted[0] = backing.extractItem(slot, amount, simulate).copy());
        return extracted[0];
    }
}
