package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Server-thread exchange of actual stacks. The caller proves distinct backing inventories.
 * Planning never mutates inventories; a durable quarantine must precede commit.
 */
final class ResidentPhysicalPurchase {
    private ResidentPhysicalPurchase() {}

    static Plan prepare(List<ItemStack> purse, Container seller, Container bag,
            Predicate<ItemStack> goods, int amount, long price, ToLongFunction<ItemStack> value) {
        if (purse == null || seller == null || bag == null || seller == bag
                || amount < 1 || amount > 2304 || price < 1 || price > 1_000_000) return null;
        var beforePurse = copy(purse);
        var beforeSeller = snapshot(seller);
        var beforeBag = snapshot(bag);
        var nextPurse = copy(beforePurse);
        var nextSeller = copy(beforeSeller);
        var nextBag = copy(beforeBag);
        long[] available = new long[3];
        long[] denominations = {10_000, 100, 1};
        for (var stack : nextPurse) if (!stack.isEmpty())
            for (int k = 0; k < 3; k++) if (value.applyAsLong(stack) == denominations[k])
                available[k] += stack.getCount();
        long[] selected = null;
        for (long gold = Math.min(available[0], price / 10_000); gold >= 0; gold--) {
            long rest = price - gold * 10_000;
            long silver = Math.min(available[1], rest / 100);
            long copper = rest - silver * 100;
            if (copper <= available[2]) { selected = new long[]{gold, silver, copper}; break; }
        }
        if (selected == null) return null;
        // Remove goods in the planned seller image before inserting the exact selected coins.
        int remaining = amount;
        for (int slot = 0; slot < nextSeller.size() && remaining > 0; slot++) {
            var stack = nextSeller.get(slot);
            if (stack.isEmpty() || !goods.test(stack) || !seller.canTakeItem(bag, slot, stack)) continue;
            int count = Math.min(remaining, stack.getCount());
            var moved = stack.copy(); moved.setCount(count);
            if (!insert(bag, nextBag, moved)) return null;
            stack.shrink(count); remaining -= count;
        }
        if (remaining != 0) return null;
        for (int k = 0; k < 3; k++) {
            long need = selected[k];
            for (var stack : nextPurse) {
                if (need == 0) break;
                if (stack.isEmpty() || value.applyAsLong(stack) != denominations[k]) continue;
                int count = (int)Math.min(need, stack.getCount());
                var moved = stack.copy(); moved.setCount(count);
                if (!insert(seller, nextSeller, moved)) return null;
                stack.shrink(count); need -= count;
            }
            if (need != 0) return null;
        }
        return new Plan(purse, seller, bag, beforePurse, beforeSeller, beforeBag,
                nextPurse, nextSeller, nextBag);
    }

    static Plan cashWage(List<ItemStack> purse, Container cashbox, long amount,
            ToLongFunction<ItemStack> value) {
        if (amount < 1 || amount > 10_000 || purse.size() > 36) return null;
        var beforePurse = copy(purse);
        for (var stack : beforePurse) if (!stack.isEmpty() && value.applyAsLong(stack) <= 0) return null;
        var beforeCash = snapshot(cashbox);
        var nextCash = copy(beforeCash);
        var nextPurse = copy(beforePurse);
        while (nextPurse.size() < 36) nextPurse.add(ItemStack.EMPTY);
        var capacity = new net.minecraft.world.SimpleContainer(36);
        long[] denominations = {10_000, 100, 1};
        long[] available = new long[3];
        for (var stack : nextCash) for (int k=0;k<3;k++)
            if (!stack.isEmpty() && value.applyAsLong(stack)==denominations[k]) available[k]+=stack.getCount();
        long[] selection=null;
        for (long gold=Math.min(available[0],amount/10_000);gold>=0;gold--) {
            long rest=amount-gold*10_000;
            long silver=Math.min(available[1],rest/100), copper=rest-silver*100;
            if(copper<=available[2]) {selection=new long[]{gold,silver,copper};break;}
        }
        if(selection==null)return null;
        for(int k=0;k<3;k++) {
            long need=selection[k];
            for(int i=0;i<nextCash.size()&&need>0;i++) {
                var stack=nextCash.get(i);
                if(stack.isEmpty()||value.applyAsLong(stack)!=denominations[k]
                        ||!cashbox.canTakeItem(capacity,i,stack))continue;
                int n=(int)Math.min(need,stack.getCount());
                var moved=stack.copy();moved.setCount(n);
                if(!insert(capacity,nextPurse,moved))return null;
                stack.shrink(n);need-=n;
            }
            if(need!=0)return null;
        }
        nextPurse.removeIf(ItemStack::isEmpty);
        var unused=new net.minecraft.world.SimpleContainer(0);
        return new Plan(purse,cashbox,unused,beforePurse,beforeCash,List.of(),nextPurse,nextCash,List.of());
    }

    record WagePlan(Plan plan, long paid) {}
    static WagePlan wage(List<ItemStack> purse, Container payer, long due,
            Predicate<ItemStack> copper, ToLongFunction<ItemStack> value) {
        if (due <= 0 || purse.size() > 36) return null;
        for (var stack : purse) if (!stack.isEmpty() && value.applyAsLong(stack) <= 0) return null;
        var beforePurse = copy(purse);
        var beforePayer = snapshot(payer);
        var nextPayer = copy(beforePayer);
        var nextPurse = copy(beforePurse);
        while (nextPurse.size() < 36) nextPurse.add(ItemStack.EMPTY);
        var capacity = new net.minecraft.world.SimpleContainer(36);
        long remaining = Math.min(64, due);
        for (var stack : nextPayer) {
            if (remaining == 0) break;
            if (stack.isEmpty() || !copper.test(stack)) continue;
            int n = (int)Math.min(remaining, stack.getCount());
            var moved = stack.copy(); moved.setCount(n);
            if (!insert(capacity, nextPurse, moved)) return null;
            stack.shrink(n); remaining -= n;
        }
        long paid = Math.min(64, due) - remaining;
        if (paid == 0) return null;
        nextPurse.removeIf(ItemStack::isEmpty);
        var unused = new net.minecraft.world.SimpleContainer(0);
        return new WagePlan(new Plan(purse, payer, unused, beforePurse, beforePayer, List.of(),
                nextPurse, nextPayer, List.of()), paid);
    }

    static Plan inheritance(List<ItemStack> extra, Container donor, Container heir) {
        if (donor == null || heir == null || donor == heir) return null;
        var beforeExtra = copy(extra);
        var beforeDonor = snapshot(donor);
        var beforeHeir = snapshot(heir);
        var nextHeir = copy(beforeHeir);
        for (var stack : beforeDonor) if (!stack.isEmpty() && !insert(heir, nextHeir, stack)) return null;
        for (var stack : beforeExtra) if (!stack.isEmpty() && !insert(heir, nextHeir, stack)) return null;
        var empty = new ArrayList<ItemStack>();
        for (int i = 0; i < beforeDonor.size(); i++) empty.add(ItemStack.EMPTY);
        return new Plan(extra, donor, heir, beforeExtra, beforeDonor, beforeHeir,
                List.of(), empty, nextHeir);
    }

    private static boolean insert(Container target, List<ItemStack> slots, ItemStack incoming) {
        int remaining = incoming.getCount();
        for (int pass = 0; pass < 2; pass++) for (int i = 0; i < slots.size() && remaining > 0; i++) {
            ItemStack old = slots.get(i);
            if (pass == 0 ? old.isEmpty() || !ItemStack.isSameItemSameTags(old, incoming) : !old.isEmpty()) continue;
            int limit = Math.min(target.getMaxStackSize(), incoming.getMaxStackSize());
            int count = Math.min(remaining, limit - old.getCount());
            if (count <= 0) continue;
            ItemStack next = incoming.copy(); next.setCount(old.getCount() + count);
            if (!target.canPlaceItem(i, next)) continue;
            slots.set(i, next); remaining -= count;
        }
        return remaining == 0;
    }

    static List<ItemStack> copy(List<ItemStack> stacks) {
        var result = new ArrayList<ItemStack>();
        for (var stack : stacks) result.add(stack.copy());
        return result;
    }
    static List<ItemStack> snapshot(Container container) {
        var result = new ArrayList<ItemStack>();
        for (int i = 0; i < container.getContainerSize(); i++) result.add(container.getItem(i).copy());
        return result;
    }
    static boolean matches(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        return true;
    }
    private static void write(Container container, List<ItemStack> slots) {
        for (int i = 0; i < slots.size(); i++)
            if (!ItemStack.matches(container.getItem(i), slots.get(i))) container.setItem(i, slots.get(i).copy());
        container.setChanged();
        if (!matches(snapshot(container), slots)) throw new IllegalStateException("Physical purchase write refused");
    }
    static final class Plan {
        private final List<ItemStack> purse;
        private final Container seller, bag;
        private final List<ItemStack> beforePurse, beforeSeller, beforeBag, nextPurse, nextSeller, nextBag;
        private boolean attempted;
        Plan(List<ItemStack> purse, Container seller, Container bag,
                List<ItemStack> beforePurse, List<ItemStack> beforeSeller, List<ItemStack> beforeBag,
                List<ItemStack> nextPurse, List<ItemStack> nextSeller, List<ItemStack> nextBag) {
            this.purse=purse; this.seller=seller; this.bag=bag;
            this.beforePurse=beforePurse; this.beforeSeller=beforeSeller; this.beforeBag=beforeBag;
            this.nextPurse=nextPurse; this.nextSeller=nextSeller; this.nextBag=nextBag;
        }
        CompoundTag evidence() {
            CompoundTag result = new CompoundTag();
            result.put("BeforePurse", tags(beforePurse)); result.put("BeforeSeller", tags(beforeSeller));
            result.put("BeforeBag", tags(beforeBag)); result.put("AfterPurse", tags(nextPurse));
            result.put("AfterSeller", tags(nextSeller)); result.put("AfterBag", tags(nextBag));
            return result;
        }
        private static ListTag tags(List<ItemStack> stacks) {
            ListTag result = new ListTag();
            for (var stack : stacks) result.add(stack.save(new CompoundTag()));
            return result;
        }
        boolean commit() {
            if (attempted) return false;
            attempted = true;
            if (!matches(purse, beforePurse) || !matches(snapshot(seller), beforeSeller)
                    || !matches(snapshot(bag), beforeBag)) return false;
            try {
                write(seller, nextSeller); write(bag, nextBag);
                purse.clear(); purse.addAll(copy(nextPurse));
                if (!matches(snapshot(seller), nextSeller) || !matches(snapshot(bag), nextBag)
                        || !matches(purse, nextPurse)) throw new IllegalStateException("Physical purchase changed during commit");
                return true;
            } catch (RuntimeException failure) {
                // Attempt every restoration even when an earlier container refuses it.
                try { write(seller, beforeSeller); } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
                try { write(bag, beforeBag); } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
                purse.clear(); purse.addAll(copy(beforePurse));
                if (!matches(snapshot(seller), beforeSeller) || !matches(snapshot(bag), beforeBag)
                        || !matches(purse, beforePurse)) throw new IllegalStateException("Purchase requires reconciliation", failure);
                return false;
            }
        }
    }
}
