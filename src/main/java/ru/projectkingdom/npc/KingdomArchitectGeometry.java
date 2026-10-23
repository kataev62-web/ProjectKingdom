package ru.projectkingdom.npc;

/**
 * Pure coordinate geometry for architect placement previews.
 * Minecraft BlockState orientation is transformed separately with Rotation
 * (facing, axis, stair half, mod properties must never be altered by string hacks).
 *
 * Positive quarterTurns are clockwise when looking down from above.
 * Source X is east and source Z is south.
 */
public final class KingdomArchitectGeometry {
    private KingdomArchitectGeometry() {}

    public record Offset(int x, int z) {}
    public record Footprint(int width, int length) {}

    public static int normalized(int quarterTurns) {
        return Math.floorMod(quarterTurns, 4);
    }

    public static Footprint footprint(int width, int length, int quarterTurns) {
        if (width <= 0 || length <= 0) throw new IllegalArgumentException("Invalid dimensions");
        return normalized(quarterTurns) % 2 == 0
            ? new Footprint(width, length) : new Footprint(length, width);
    }

    /**
     * Rotate a voxel around the north-west corner, then normalize the result
     * to nonnegative schematic-local offsets.
     *
     * Four rotations return the original cell exactly.
     */
    public static Offset rotate(int x, int z, int width, int length, int quarterTurns) {
        if (width <= 0 || length <= 0 || x < 0 || x >= width || z < 0 || z >= length)
            throw new IllegalArgumentException("Cell outside schematic footprint");
        return switch (normalized(quarterTurns)) {
            case 0 -> new Offset(x, z);
            case 1 -> new Offset(length - 1 - z, x);
            case 2 -> new Offset(width - 1 - x, length - 1 - z);
            case 3 -> new Offset(z, width - 1 - x);
            default -> throw new AssertionError();
        };
    }

    public static Offset world(int localX, int localZ, int width, int length,
                               int quarterTurns, int originX, int originZ) {
        Offset offset = rotate(localX, localZ, width, length, quarterTurns);
        return new Offset(Math.addExact(originX, offset.x()), Math.addExact(originZ, offset.z()));
    }
}
