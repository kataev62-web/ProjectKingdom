package ru.projectkingdom.map;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Small, non-generating terrain sample for the graphical atlas.
 * Reads ONLY chunks currently loaded by the server. Unknown terrain remains
 * parchment; looking at the map never triggers expensive chunk generation.
 *
 * This is a real surface scan (including TFC blocks tagged as leaves/logs),
 * NOT a replacement worldgen / ore map.
 */
public final class KingdomAtlasTerrain {
    public enum Type { UNKNOWN, PLAIN, FOREST, WATER, MOUNTAIN, ROAD }
    public record Cell(int chunkX, int chunkZ, Type type, int elevation) {}

    private KingdomAtlasTerrain() {}

    public static List<Cell> sampleLoaded(ServerLevel level, int centerX, int centerZ, int scale) {
        if (!KingdomGraphicAtlasData.supportedScale(scale))
            throw new IllegalArgumentException("Invalid atlas scale");
        List<Cell> result = new ArrayList<>(KingdomGraphicAtlasData.MAX_CELLS);
        long minX = (long)centerX - (KingdomGraphicAtlasData.COLUMNS / 2L) * scale;
        long minZ = (long)centerZ - (KingdomGraphicAtlasData.ROWS / 2L) * scale;
        for (int row = 0; row < KingdomGraphicAtlasData.ROWS; row++) {
            for (int col = 0; col < KingdomGraphicAtlasData.COLUMNS; col++) {
                long chunkX = minX + (long)col * scale + scale / 2;
                long chunkZ = minZ + (long)row * scale + scale / 2;
                if (chunkX < -1_875_000 || chunkX > 1_875_000
                        || chunkZ < -1_875_000 || chunkZ > 1_875_000) continue;
                LevelChunk chunk = level.getChunkSource().getChunkNow((int)chunkX, (int)chunkZ);
                if (chunk == null) continue;
                result.add(sample(chunk, (int)chunkX, (int)chunkZ, level.getSeaLevel()));
            }
        }
        return List.copyOf(result);
    }

    private static Cell sample(LevelChunk chunk, int cx, int cz, int seaLevel) {
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
        if (top < chunk.getMinBuildHeight() || top >= chunk.getMaxBuildHeight())
            return new Cell(cx, cz, Type.UNKNOWN, 0);
        BlockPos pos = new BlockPos((cx << 4) + 8, top, (cz << 4) + 8);
        var state = chunk.getBlockState(pos);
        // Some heightmap implementations report the first available (air) Y.
        if (state.isAir() && top > chunk.getMinBuildHeight()) {
            pos = pos.below();
            state = chunk.getBlockState(pos);
            top--;
        }
        if (state.getFluidState().is(FluidTags.WATER))
            return new Cell(cx, cz, Type.WATER, top - seaLevel);
        if (state.is(Blocks.DIRT_PATH))
            return new Cell(cx, cz, Type.ROAD, top - seaLevel);

        int foliage = 0;
        int[] offsets = {3, 8, 12};
        for (int dx : offsets) for (int dz : offsets) {
            int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, dx, dz);
            if (y < chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight()) continue;
            BlockPos at = new BlockPos((cx << 4) + dx, y, (cz << 4) + dz);
            var surface = chunk.getBlockState(at);
            if (surface.isAir() && y > chunk.getMinBuildHeight())
                surface = chunk.getBlockState(at.below());
            if (surface.is(BlockTags.LEAVES) || surface.is(BlockTags.LOGS)) foliage++;
        }
        if (foliage >= 3) return new Cell(cx, cz, Type.FOREST, top - seaLevel);
        if (top >= seaLevel + 42) return new Cell(cx, cz, Type.MOUNTAIN, top - seaLevel);
        return new Cell(cx, cz, Type.PLAIN, top - seaLevel);
    }
}
