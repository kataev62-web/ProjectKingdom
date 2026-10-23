package ru.projectkingdom.map;

/**
 * Converts the local player's precise world position and heading to the
 * graphical atlas grid. No network packet is needed for self-location.
 *
 * North is up (-Z), east is right (+X), as in vanilla Minecraft maps.
 */
public final class KingdomAtlasPlayerMarker {
    public record Projection(double gridX, double gridY, double facingX,
                             double facingY, boolean onMap) {}
    private KingdomAtlasPlayerMarker() {}

    public static Projection project(double worldX, double worldZ, float yawDegrees,
                                     int centerChunkX, int centerChunkZ, int scale) {
        if (!KingdomGraphicAtlasData.supportedScale(scale)
                || !Double.isFinite(worldX) || !Double.isFinite(worldZ)
                || !Float.isFinite(yawDegrees)) {
            throw new IllegalArgumentException("Invalid player marker coordinates");
        }
        long startX = (long)centerChunkX
                - (KingdomGraphicAtlasData.COLUMNS / 2L) * scale;
        long startZ = (long)centerChunkZ
                - (KingdomGraphicAtlasData.ROWS / 2L) * scale;
        double x = (worldX / 16.0 - startX) / scale;
        double y = (worldZ / 16.0 - startZ) / scale;
        double angle = Math.toRadians(yawDegrees);
        // Minecraft yaw=0 faces south (+Z), yaw=90 faces west (-X).
        double facingX = -Math.sin(angle), facingY = Math.cos(angle);
        boolean onMap = x >= 0 && x < KingdomGraphicAtlasData.COLUMNS
                && y >= 0 && y < KingdomGraphicAtlasData.ROWS;
        return new Projection(x, y, facingX, facingY, onMap);
    }
}
