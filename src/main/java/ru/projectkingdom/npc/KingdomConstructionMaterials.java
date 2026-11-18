package ru.projectkingdom.npc;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One physical building item, debited only AFTER the target block is verified.
 * Never mutate a stack returned by getItem(): Forge/TFC wrappers may return copies.
 *
 * A tagged block item cannot be safely represented by raw setBlock because its
 * BlockEntityTag/capabilities would be discarded. Such inputs remain in storage.
 */
final class KingdomConstructionMaterials {
    private KingdomConstructionMaterials() {}

    enum Debit { PAID, REJECTED, UNCERTAIN }
    record Prepared(int slot, ItemStack original) {}

    static Prepared prepare(Container depot, int slot, Item item) {
        if (depot == null || item == null || slot < 0 || slot >= depot.getContainerSize())
            return null;
        ItemStack stack = depot.getItem(slot).copy();
        if (stack.isEmpty() || !stack.is(item) || stack.getCount() < 1
                || stack.hasTag() || !depot.canTakeItem(depot, slot, stack)) return null;
        ItemStack after = stack.copy();
        after.shrink(1);
        if (!after.isEmpty() && !depot.canPlaceItem(slot, after)) return null;
        return new Prepared(slot, stack);
    }

    static Debit debit(Container depot, Prepared plan) {
        if (depot == null || plan == null || plan.slot() < 0
                || plan.slot() >= depot.getContainerSize()) return Debit.REJECTED;
        ItemStack before = plan.original().copy();
        if (!ItemStack.matches(before, depot.getItem(plan.slot())))
            return Debit.REJECTED;
        ItemStack after = before.copy();
        after.shrink(1);
        // setItem must persist the exact new count + metadata; getItem may be defensive.
        // Unlike removeItem, a rejected write does not take an item out of the depot.
        try {
            depot.setItem(plan.slot(), after.copy());
            depot.setChanged();
            if (ItemStack.matches(after, depot.getItem(plan.slot())))
                return Debit.PAID;
        } catch (RuntimeException ignored) {
            // Treat a failing/partially applied modded handler as untrusted.
        }
        // If a handler wrote a partial/incorrect result, restore the exact source
        // snapshot through the actual setter and reread it. No in-place grow().
        try {
            depot.setItem(plan.slot(), before.copy());
            depot.setChanged();
            return ItemStack.matches(before, depot.getItem(plan.slot()))
                ? Debit.REJECTED : Debit.UNCERTAIN;
        } catch (RuntimeException ignored) {
            return Debit.UNCERTAIN;
        }
    }
}
