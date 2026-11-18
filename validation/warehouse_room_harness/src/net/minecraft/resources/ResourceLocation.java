package net.minecraft.resources;

public class ResourceLocation {
    private final String path;
    
    public ResourceLocation(String path) { this.path = path; }
    public String toString() { return path; }
}