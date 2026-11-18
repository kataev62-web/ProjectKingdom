package net.minecraft.world.entity.player;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class Player {
    public BlockPos pos = new BlockPos(0, 60, 0);
    private ItemStack mainHandItem = ItemStack.EMPTY;
    
    public BlockPos blockPosition() { return pos; }
    public void setItemInHand(InteractionHand hand, ItemStack stack) { 
        if (hand == InteractionHand.MAIN_HAND) mainHandItem = stack;
    }
    public ItemStack getItemInHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? mainHandItem : ItemStack.EMPTY;
    }
}