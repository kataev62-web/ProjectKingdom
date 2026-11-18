package ru.projectkingdom.npc;

import java.io.IOException;
import java.util.Objects;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Server-thread-only adapter between a Minecraft container and the settlement
 * authority.
 *
 * <p>The adapter only moves items. It decides nothing: the authority owns the
 * decision, the durable log and the recovery. The adapter refuses to work
 * outside the thread that created it, so a settlement can never run in
 * parallel with another write of the same container.
 *
 * <p>The count based check of the authority cannot detect a change that leaves
 * the same amount in the container. The authority quarantines such a delivery
 * instead of applying it, but an operator still has to confirm the state.
 */
public final class SettlementMinecraftPorts {
    private SettlementMinecraftPorts() {}

    /** One physical container of the world. */
    public static final class Port implements SettlementAuthority.Container {
        private final String id;
        private final Container container;
        private final Thread ownerThread;

        public Port(String id, Container container) {
            this.id = Objects.requireNonNull(id, "id");
            this.container = Objects.requireNonNull(container, "container");
            this.ownerThread = Thread.currentThread();
        }

        private void checkThread() throws IOException {
            if (Thread.currentThread() != ownerThread)
                throw new IOException("settlement outside the owner server thread");
        }

        public String id() { return id; }

        public long count(String item) {
            return countOf(container, prototype(item));
        }

        public long capacity(String item) {
            return spaceOf(container, prototype(item));
        }

        /** Moves the amount in or out of the container. */
        public void apply(String item, long delta) throws IOException {
            checkThread();
            if (delta == 0) return;
            ItemStack sample = prototype(item);
            if (delta < 0) remove(sample, -delta);
            else insert(sample, delta);
        }

        private void remove(ItemStack sample, long amount) throws IOException {
            if (countOf(container, sample) < amount) throw new IOException("insufficient physical items");
            for (int slot = 0; slot < container.getContainerSize() && amount > 0; slot++) {
                ItemStack current = container.getItem(slot);
                if (!matches(current, sample)) continue;
                int take = (int) Math.min(amount, current.getCount());
                ItemStack next = current.copy();
                next.shrink(take);
                container.setItem(slot, next);
                amount -= take;
            }
            if (amount != 0) throw new IOException("incomplete removal");
            container.setChanged();
        }

        private void insert(ItemStack sample, long amount) throws IOException {
            if (spaceOf(container, sample) < amount) throw new IOException("insufficient physical space");
            for (int slot = 0; slot < container.getContainerSize() && amount > 0; slot++) {
                ItemStack current = container.getItem(slot);
                if ((!current.isEmpty() && !matches(current, sample)) || !container.canPlaceItem(slot, sample)) continue;
                int limit = Math.min(container.getMaxStackSize(), sample.getMaxStackSize());
                int free = limit - (current.isEmpty() ? 0 : current.getCount());
                if (free <= 0) continue;
                int add = (int) Math.min(amount, free);
                ItemStack next = current.isEmpty() ? sample.copy() : current.copy();
                next.setCount((current.isEmpty() ? 0 : current.getCount()) + add);
                container.setItem(slot, next);
                amount -= add;
            }
            if (amount != 0) throw new IOException("incomplete insertion");
            container.setChanged();
        }
    }

    private static ItemStack prototype(String item) {
        var key = new net.minecraft.resources.ResourceLocation(item);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key))
            throw new IllegalArgumentException("unknown item: " + item);
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(key));
        if (stack.isEmpty()) throw new IllegalArgumentException("empty item: " + item);
        return stack;
    }

    private static boolean matches(ItemStack stack, ItemStack sample) {
        return !stack.isEmpty() && ItemStack.isSameItemSameTags(stack, sample);
    }

    private static long countOf(Container container, ItemStack sample) {
        long total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++)
            if (matches(container.getItem(slot), sample)) total += container.getItem(slot).getCount();
        return total;
    }

    private static long spaceOf(Container container, ItemStack sample) {
        long total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack current = container.getItem(slot);
            if (current.isEmpty() && container.canPlaceItem(slot, sample))
                total += Math.min(container.getMaxStackSize(), sample.getMaxStackSize());
            else if (matches(current, sample) && container.canPlaceItem(slot, sample))
                total += Math.max(0, Math.min(container.getMaxStackSize(), current.getMaxStackSize()) - current.getCount());
        }
        return total;
    }
}
