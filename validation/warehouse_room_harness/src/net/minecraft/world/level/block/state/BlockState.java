package net.minecraft.world.level.block.state;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.ChestType;

public class BlockState {
    private final boolean hasBlockEntity;
    
    public BlockState(boolean hasBlockEntity) { this.hasBlockEntity = hasBlockEntity; }
    public boolean hasBlockEntity() { return hasBlockEntity; }
    public Block getBlock() { return new Block(); }
    
    @SuppressWarnings("unchecked")
    public <T extends Comparable<T>> T getValue(Property<T> property) {
        if (property == ChestBlock.TYPE) return (T) ChestType.SINGLE;
        return null;
    }
}