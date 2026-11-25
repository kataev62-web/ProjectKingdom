package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * The physical inventory of a resident.
 *
 * <p>The physical container of the villager is the only place where the
 * belongings of a resident really live: MCA saves it with the entity, the
 * durability and the NBT of every item are part of it, and the player sees it
 * in the window of the resident. Nothing of the Kingdom may exist beside it as
 * a second copy of the same items.
 *
 * <p>The Kingdom keeps a working view of the belongings in the record of the
 * worker, because the production algorithms were written against it. That view
 * is transient: it is filled from the container before the work of the
 * resident and written back into the container afterwards. The view is never
 * saved, so a restart can never duplicate an item and can never lose one.
 * Everything that does not fit into the container stays in the world as a real
 * item.
 */
public final class KingdomResidentInventory {
    private KingdomResidentInventory() {}

    private static final java.util.Set<Container> QUARANTINED =
            java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(
                    new java.util.IdentityHashMap<>()));

    private static void ensureWritable(Container container) {
        if (QUARANTINED.contains(container))
            throw new IllegalStateException("Resident inventory requires recovery after rejected rollback");
    }

    private static List<ItemStack> snapshot(Container container) {
        List<ItemStack> original = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++)
            original.add(container.getItem(slot).copy());
        return original;
    }

    private static boolean restore(Container container, List<ItemStack> original,
                                   RuntimeException failure) {
        boolean restored = true;
        for (int slot = 0; slot < original.size(); slot++) {
            try { container.setItem(slot, original.get(slot).copy()); }
            catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            try {
                if (!ItemStack.matches(original.get(slot), container.getItem(slot))) restored = false;
            } catch (RuntimeException verification) {
                failure.addSuppressed(verification);
                restored = false;
            }
        }
        if (!restored) {
            QUARANTINED.add(container);
            failure.addSuppressed(new IllegalStateException("Rollback rejected; inventory quarantined for recovery"));
        }
        return restored;
    }

    /** The personal inventory of a resident, null when there is none. */
    public static Container personal(Villager resident) {
        return KingdomMcaAdapter.personal(resident);
    }

    /** The amount of an item in the container. */
    public static int count(Container container, net.minecraft.world.item.Item item) {
        if (container == null || item == null) return 0;
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) total += stack.getCount();
        }
        return total;
    }

    /** The amount of an item in the container that matches the predicate. */
    public static int count(Container container, java.util.function.Predicate<ItemStack> match) {
        if (container == null || match == null) return 0;
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && match.test(stack)) total += stack.getCount();
        }
        return total;
    }

    /**
     * Inserts a stack into the container.
     *
     * <p>The stack is reduced by the amount accepted by the container. The container is
     * never overfilled and nothing is created: the caller keeps the rest.
     *
     * @return true when the stack was fully stored.
     */
    public static boolean insert(Container container, ItemStack stack) {
        if (container == null || stack == null || stack.isEmpty()) return false;
        int before = countOf(stack);
        return insertCount(container, stack) == before;
    }

    /**
     * Inserts a stack into the container and reports how much really went in.
     *
     * <p>The caller learns the exact amount, so that a partial insertion is
     * never counted as a full one.
     *
     * @return the number of items that entered the container.
     */
    public static int insertCount(Container container, ItemStack stack) {
        if (container == null || stack == null || stack.isEmpty()) return 0;
        ensureWritable(container);
        int before = countOf(stack);
        // Fill the stacks of the same item first, then the empty slots.
        for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
            ItemStack current = container.getItem(slot);
            if (current.isEmpty() || !ItemStack.isSameItemSameTags(current, stack)) continue;
            if (!container.canPlaceItem(slot, stack)) continue;
            int limit = Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
            int free = limit - current.getCount();
            if (free <= 0) continue;
            int add = Math.min(free, stack.getCount());
            ItemStack next = current.copy();
            next.setCount(current.getCount() + add);
            writeInsertion(container, slot, current.getCount(), next, stack);
        }
        for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
            if (!container.getItem(slot).isEmpty()) continue;
            if (!container.canPlaceItem(slot, stack)) continue;
            int limit = Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
            int add = Math.min(limit, stack.getCount());
            ItemStack next = stack.copy();
            next.setCount(add);
            writeInsertion(container, slot, 0, next, stack);
        }
        container.setChanged();
        return before - countOf(stack);
    }

    // A container may clamp/reject a write or return copies. Account only the
    // read-back delta, including a setter which throws after accepting items.
    private static void writeInsertion(Container container, int slot, int before,
                                       ItemStack next, ItemStack source) {
        try {
            container.setItem(slot, next);
        } finally {
            ItemStack actual = container.getItem(slot);
            int accepted = ItemStack.isSameItemSameTags(actual, source)
                    ? Math.max(0, actual.getCount() - before) : 0;
            source.shrink(Math.min(source.getCount(), accepted));
        }
    }

    /**
     * Inserts a stack and returns what did not fit.
     *
     * <p>The caller gets the remainder back and can decide where it goes.
     */
    public static ItemStack insertAndRemainder(Container container, ItemStack stack) {
        if (stack == null) return ItemStack.EMPTY;
        if (container == null) return stack;
        insertCount(container, stack);
        return stack;
    }

    private static int countOf(ItemStack stack) {
        return stack == null || stack.isEmpty() ? 0 : stack.getCount();
    }

    /**
     * Takes the items that match out of the container.
     *
     * @param amount the maximum amount to take.
     * @return the taken items, never more than the requested amount.
     */
    public static List<ItemStack> extract(Container container,
                                           java.util.function.Predicate<ItemStack> match,
                                           int amount) {
        List<ItemStack> taken = new ArrayList<>();
        if (container == null || match == null || amount <= 0) return taken;
        ensureWritable(container);
        List<ItemStack> original = snapshot(container);
        try {
        int left = amount;
        for (int slot = 0; slot < container.getContainerSize() && left > 0; slot++) {
            ItemStack current = container.getItem(slot).copy();
            if (current.isEmpty() || !match.test(current)) continue;
            int take = Math.min(left, current.getCount());
            ItemStack part = current.copy();
            part.setCount(take);
            ItemStack next = current.copy();
            next.shrink(take);
            container.setItem(slot, next);
            ItemStack actual = container.getItem(slot);
            int remaining = actual.isEmpty() ? 0
                    : ItemStack.isSameItemSameTags(actual, current) ? actual.getCount() : current.getCount();
            int removed = Math.min(take, Math.max(0, current.getCount() - remaining));
            if (removed > 0) {
                part.setCount(removed);
                taken.add(part);
                left -= removed;
            }
        }
        if (!taken.isEmpty()) container.setChanged();
        } catch (RuntimeException failure) {
            restore(container, original, failure);
            throw failure;
        }
        return taken;
    }

    /** The first stack of the container that matches, a copy. */
    public static ItemStack find(Container container,
                                  java.util.function.Predicate<ItemStack> match) {
        if (container == null || match == null) return ItemStack.EMPTY;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && match.test(stack)) return stack.copy();
        }
        return ItemStack.EMPTY;
    }

    /** The tool of the given kind that the resident carries. */
    public static ItemStack findTool(Container container, KingdomMineTools.Tool needed) {
        return find(container, stack -> KingdomMineTools.matches(stack, needed));
    }

    /** Whether the container has room for the given amount of the stack. */
    public static boolean hasRoom(Container container, ItemStack stack) {
        if (container == null || stack == null || stack.isEmpty()) return false;
        int needed = stack.getCount();
        int room = 0;
        for (int slot = 0; slot < container.getContainerSize() && room < needed; slot++) {
            ItemStack current = container.getItem(slot);
            if (current.isEmpty()) {
                if (!container.canPlaceItem(slot, stack)) continue;
                room += Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
            } else if (ItemStack.isSameItemSameTags(current, stack)
                && container.canPlaceItem(slot, stack)) {
                room += Math.max(0, Math.min(container.getMaxStackSize(), stack.getMaxStackSize())
                        - current.getCount());
            }
        }
        return room >= needed;
    }

    // --- the working view of the worker --------------------------------------

    /**
     * Fills the working view of the worker from the real container.
     *
     * <p>The tool of the job is the tool of the view; every other item of the
     * container stays in the cargo of the view. Nothing is lost and nothing is
     * created: the view is only a copy for the working algorithms.
     */
    public static void syncToWorker(KingdomNpcData.Worker worker, Container personal) {
        if (worker == null) return;
        List<ItemStack> cargo = new ArrayList<>();
        ItemStack tool = ItemStack.EMPTY;
        if (personal != null) {
            for (int slot = 0; slot < personal.getContainerSize(); slot++) {
                ItemStack stack = personal.getItem(slot);
                if (stack.isEmpty()) continue;
                if (tool.isEmpty() && isTool(stack)) {
                    tool = stack.copy();
                    continue;
                }
                cargo.add(stack.copy());
            }
        }
        worker.fieldTool = tool;
        worker.fieldInventory = new ArrayList<>(cargo);
    }

    /** Whether the item is a tool at all. */
    public static boolean isTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof DiggerItem
            || stack.is(net.minecraft.tags.ItemTags.PICKAXES)
            || stack.is(net.minecraft.tags.ItemTags.SHOVELS)
            || stack.is(net.minecraft.tags.ItemTags.AXES)
            || stack.is(net.minecraft.tags.ItemTags.HOES);
    }

    /**
     * Writes the working view of the worker into the real container.
     *
     * <p>The container is rebuilt from the view, so that the items of the view
     * and the items of the container can never be the same item twice. What
     * does not fit stays in the world as a real item: nothing disappears.
     *
     * @return the number of items that stayed in the world.
     */
    public static int flush(net.minecraft.server.level.ServerLevel level, Villager resident,
                             KingdomNpcData.Worker worker) {
        if (worker == null) return 0;
        Container personal = personal(resident);
        if (personal == null) return 0;
        List<ItemStack> left = flushToContainer(personal, worker);
        worker.pendingMigration.addAll(left);
        if (level == null || resident == null) return 0;
        return releasePending(worker, stack -> {
            ItemEntity item = new ItemEntity(level, resident.getX(), resident.getY(), resident.getZ(), stack);
            item.setDefaultPickUpDelay();
            try {
                return level.addFreshEntity(item);
            } catch (RuntimeException failure) {
                // Some event listeners throw after insertion. The entity's UUID
                // establishes whether the world already owns this exact stack.
                if (level.getEntity(item.getUUID()) == item) return true;
                throw failure;
            }
        });
    }

    static int releasePending(KingdomNpcData.Worker worker,
                              java.util.function.Predicate<ItemStack> spawn) {
        int dropped = 0;
        var iterator = worker.pendingMigration.iterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            if (!spawn.test(stack.copy())) continue;
            dropped += countOf(stack);
            iterator.remove();
        }
        return dropped;
    }

    /**
     * Writes the working view of the worker into a container.
     *
     * <p>The container is rebuilt from the view, so that the items of the view
     * and the items of the container can never be the same item twice.
     *
     * @return the items that did not fit and that stay in the world.
     */
    public static List<ItemStack> flushToContainer(Container personal,
                                                    KingdomNpcData.Worker worker) {
        List<ItemStack> left = new ArrayList<>();
        if (worker == null || personal == null) return left;
        if (worker.inventoryRecoveryRequired)
            throw new IllegalStateException("Resident inventory requires manual recovery");
        ensureWritable(personal);
        List<ItemStack> content = new ArrayList<>();
        if (!worker.fieldTool.isEmpty()) content.add(worker.fieldTool.copy());
        for (ItemStack stack : worker.fieldInventory)
            if (stack != null && !stack.isEmpty()) content.add(stack.copy());
        List<ItemStack> original = snapshot(personal);
        worker.pendingInventoryFlush = true;
        try {
            for (int slot = 0; slot < personal.getContainerSize(); slot++) {
                personal.setItem(slot, ItemStack.EMPTY);
                if (!personal.getItem(slot).isEmpty())
                    throw new IllegalStateException("Resident inventory rejected clearing slot " + slot);
            }
            for (ItemStack stack : content) {
                insertCount(personal, stack);
                if (!stack.isEmpty()) left.add(stack);
            }
            personal.setChanged();
        } catch (RuntimeException failure) {
            // Keep the work view for retry only after verified restoration.
            if (!restore(personal, original, failure)) {
                worker.inventoryRecoveryRequired = true;
                worker.mineStatus = "Инвентарь заблокирован: требуется проверка сохранённых предметов после сбоя отката";
            }
            throw failure;
        }
        worker.pendingInventoryFlush = false;
        try {
            syncToWorker(worker, personal);
        } catch (RuntimeException failure) {
            worker.pendingMigration.addAll(left);
            throw failure;
        }
        return left;
    }

    /**
     * Binds the physical container of a resident and fills the working view.
     *
     * <p>The belongings of an old world are moved into the container here, the
     * first time the resident is seen again. The working view is then filled
     * from the container, so that the two can never drift apart.
     *
     * @return true when a real container is bound.
     */
    public static boolean bind(KingdomNpcData.Worker worker, Villager resident) {
        if (worker == null || resident == null) return false;
        return bindToContainer(worker, personal(resident));
    }

    /**
     * Migrate only residual legacy items into the physical bag once.
     * Already accepted stacks stay exclusively in the bag; overflow remains
     * durably owned by pendingMigration until insertion or a verified world drop.
     */
    static boolean bindToContainer(KingdomNpcData.Worker worker, Container personal) {
        if (worker == null || personal == null) return false;
        if (worker.inventoryRecoveryRequired) return false;
        ensureWritable(personal);
        if (worker.pendingInventoryFlush) {
            worker.pendingMigration.addAll(flushToContainer(personal, worker));
        }
        if (!worker.physicalBag) {
            loadFromWorker(worker, personal);
            if (!worker.fieldTool.isEmpty()) worker.pendingMigration.add(worker.fieldTool);
            worker.pendingMigration.addAll(worker.fieldInventory);
            worker.fieldTool = ItemStack.EMPTY;
            worker.fieldInventory = new ArrayList<>();
            worker.physicalBag = true;
        }
        // Overflow belongs to the worker until it has actually entered the bag.
        for (ItemStack pending : worker.pendingMigration) insertCount(personal, pending);
        worker.pendingMigration.removeIf(ItemStack::isEmpty);
        syncToWorker(worker, personal);
        return true;
    }

    /** The number of items in the real container of a resident. */
    public static int realCount(Villager resident) {
        Container personal = personal(resident);
        if (personal == null) return -1;
        int total = 0;
        for (int slot = 0; slot < personal.getContainerSize(); slot++) {
            ItemStack stack = personal.getItem(slot);
            if (!stack.isEmpty()) total += stack.getCount();
        }
        return total;
    }

    /**
     * Writes the belongings of an old world into the real container.
     *
     * <p>Used by the migration of an old resident: the items that the Kingdom
     * kept are moved into the personal inventory of the villager. The items
     * that do not fit stay in the working view, so nothing is lost.
     */
    public static int loadFromWorker(KingdomNpcData.Worker worker, Container personal) {
        if (worker == null || personal == null) return 0;
        int stored = 0;
        if (!worker.fieldTool.isEmpty()) {
            insertCount(personal, worker.fieldTool);
            if (worker.fieldTool.isEmpty()) { worker.fieldTool = ItemStack.EMPTY; stored++; }
        }
        // Mutate the owned source itself so interruption cannot replay accepted items.
        for (ItemStack stack : worker.fieldInventory) {
            if (stack == null || stack.isEmpty()) continue;
            insertCount(personal, stack);
            if (stack.isEmpty()) stored++;
        }
        worker.fieldInventory.removeIf(stack -> stack == null || stack.isEmpty());
        return stored;
    }
}
