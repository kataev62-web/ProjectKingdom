package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

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
     * <p>The stack is reduced by the amount that did not fit. The container is
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
            container.setItem(slot, next);
            stack.shrink(add);
        }
        for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
            if (!container.getItem(slot).isEmpty()) continue;
            if (!container.canPlaceItem(slot, stack)) continue;
            int limit = Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
            int add = Math.min(limit, stack.getCount());
            ItemStack next = stack.copy();
            next.setCount(add);
            container.setItem(slot, next);
            stack.shrink(add);
        }
        container.setChanged();
        return before - countOf(stack);
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
        int left = amount;
        for (int slot = 0; slot < container.getContainerSize() && left > 0; slot++) {
            ItemStack current = container.getItem(slot);
            if (current.isEmpty() || !match.test(current)) continue;
            int take = Math.min(left, current.getCount());
            ItemStack part = current.copy();
            part.setCount(take);
            taken.add(part);
            left -= take;
            ItemStack next = current.copy();
            next.shrink(take);
            container.setItem(slot, next);
        }
        if (!taken.isEmpty()) container.setChanged();
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
        int lost = 0;
        for (ItemStack stack : left) {
            lost += countOf(stack);
            if (level != null && resident != null)
                Block.popResource(level, resident.blockPosition(), stack);
        }
        return lost;
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
        List<ItemStack> content = new ArrayList<>();
        if (!worker.fieldTool.isEmpty()) content.add(worker.fieldTool.copy());
        for (ItemStack stack : worker.fieldInventory)
            if (stack != null && !stack.isEmpty()) content.add(stack.copy());
        for (int slot = 0; slot < personal.getContainerSize(); slot++)
            personal.setItem(slot, ItemStack.EMPTY);
        for (ItemStack stack : content) {
            ItemStack rest = stack.copy();
            insertCount(personal, rest);
            if (!rest.isEmpty()) left.add(rest);
        }
        personal.setChanged();
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
        Container personal = personal(resident);
        if (personal == null) {
            // A resident without a real container keeps the working view as
            // its own storage; it is saved with the worker.
            worker.physicalBag = false;
            return false;
        }
        if (!worker.physicalBag) {
            // The first time: the belongings of an old world move into the
            // real container. What does not fit stays in the working view.
            List<ItemStack> left = new ArrayList<>(worker.fieldInventory);
            loadFromWorker(worker, personal);
            left.addAll(worker.fieldInventory);
            worker.physicalBag = true;
            syncToWorker(worker, personal);
            // What did not fit into the container stays in the view, so that
            // the flush can put it back or leave it in the world.
            worker.fieldInventory.addAll(left);
            return true;
        }
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
            ItemStack copy = worker.fieldTool.copy();
            if (insert(personal, copy)) {
                worker.fieldTool = ItemStack.EMPTY;
                stored++;
            } else if (!copy.isEmpty()) {
                worker.fieldTool = copy;
            }
        }
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack stack : worker.fieldInventory) {
            if (stack == null || stack.isEmpty()) continue;
            ItemStack copy = stack.copy();
            if (insert(personal, copy)) stored++;
            else if (!copy.isEmpty()) left.add(copy);
        }
        worker.fieldInventory = left;
        return stored;
    }
}
