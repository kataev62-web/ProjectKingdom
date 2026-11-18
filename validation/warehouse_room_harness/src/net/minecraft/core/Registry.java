package net.minecraft.core;

import net.minecraft.resources.ResourceLocation;

public class Registry<T> {
    public ResourceLocation getKey(T value) { return new ResourceLocation("test:" + value.getClass().getSimpleName().toLowerCase()); }
}