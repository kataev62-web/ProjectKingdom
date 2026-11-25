package ru.projectkingdom.npc;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;

/** Owned recovery stacks are retired only after the world accepts the corresponding drop. */
final class ResidentOwnedDrops {
    private ResidentOwnedDrops() {}
    static boolean release(List<ItemStack> owned, Predicate<ItemStack> spawn) {
        for (int i=owned.size()-1;i>=0;i--) {
            var stack=owned.get(i);
            if(stack.isEmpty()) {owned.remove(i);continue;}
            if(spawn.test(stack.copy())) owned.remove(i);
        }
        return owned.isEmpty();
    }
}
