package net.minecraft.core;

public class Direction {
    public final int x, y, z;
    
    private Direction(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    
    public static final Direction UP = new Direction(0, 1, 0);
    public static final Direction DOWN = new Direction(0, -1, 0);
    public static final Direction NORTH = new Direction(0, 0, -1);
    public static final Direction SOUTH = new Direction(0, 0, 1);
    public static final Direction EAST = new Direction(1, 0, 0);
    public static final Direction WEST = new Direction(-1, 0, 0);
    
    public static class Plane {
        public static final Direction[] HORIZONTAL = {NORTH, SOUTH, EAST, WEST};
    }
}