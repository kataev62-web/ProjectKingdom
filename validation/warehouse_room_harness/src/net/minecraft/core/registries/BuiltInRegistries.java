package net.minecraft.core.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class BuiltInRegistries {
    public static final Registry<Item> ITEM = new Registry<>();
    public static final Registry<Block> BLOCK = new Registry<>();
}