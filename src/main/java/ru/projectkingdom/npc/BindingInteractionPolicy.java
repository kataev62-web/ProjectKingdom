package ru.projectkingdom.npc;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** Never swallow ordinary block placement while waiting for a binding target. */
public final class BindingInteractionPolicy {
    private BindingInteractionPolicy() {}

    public static boolean allowsPlacement(ItemStack held) {
        return held != null && !held.isEmpty() && held.getItem() instanceof BlockItem;
    }
}
