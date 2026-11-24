package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * The personal inventory of a resident.
 *
 * <p>For an MCA villager the personal inventory is the real MCA container: the
 * nine rows of three slots that MCA shows in its own menu and that MCA saves
 * with the entity. It is the single source of truth for the belongings of the
 * resident.
 *
 * <p>The Kingdom keeps a mirror of the tool and the cargo in the worker record
 * because the profession executors were written against it. The mirror is
 * refreshed from the real container after every physical action, so the two
 * views cannot drift apart silently.
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
        return stack.isEmpty();
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

    /**
     * Refreshes the Kingdom mirror of the worker from the real container.
     *
     * <p>The tool is the first stack that is a tool at all; the cargo keeps
     * the remaining items. The mirror never invents an item: it only copies
     * what the container really holds.
     */
    public static void syncToWorker(KingdomNpcData.Worker worker, Container personal) {
        if (worker == null) return;
        List<ItemStack> cargo = new ArrayList<>();
        ItemStack tool = ItemStack.EMPTY;
        if (personal != null) {
            for (int slot = 0; slot < personal.getContainerSize(); slot++) {
                ItemStack stack = personal.getItem(slot);
                if (stack.isEmpty()) continue;
                if (tool.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.TieredItem
                        && stack.getItem() != net.minecraft.world.item.Items.BREAD) {
                    tool = stack.copy();
                    continue;
                }
                cargo.add(stack.copy());
            }
        }
        worker.fieldTool = tool;
        worker.fieldInventory = new ArrayList<>(cargo);
    }

    /**
     * Writes the Kingdom mirror of the worker into the real container.
     *
     * <p>Used by the migration of an old resident: the items that the Kingdom
     * kept are moved into the personal inventory of MCA. The items that do not
     * fit stay in the Kingdom mirror, so nothing is lost.
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
