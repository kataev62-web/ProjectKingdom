package net.minecraft.world.item;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

public class ItemStack {
    public static final ItemStack EMPTY = new ItemStack();
    private int count = 1;
    private Item item = null;
    
    public ItemStack() {}
    public ItemStack(ItemStack other) { this.count = other.count; this.item = other.item; }
    public ItemStack(Item item) { this.item = item; }
    
    public boolean isEmpty() { return this == EMPTY || count == 0; }
    public int getCount() { return count; }
    public Item getItem() { return item; }
}