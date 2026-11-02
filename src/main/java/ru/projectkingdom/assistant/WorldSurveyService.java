package ru.projectkingdom.assistant;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deterministic two-block sampling of loaded chunks only. */
public final class WorldSurveyService {
    public static final int MIN_RADIUS = 8;
    public static final int MAX_RADIUS = 96;
    public static final int DEFAULT_RADIUS = 48;
    public static final int MAX_SAMPLED_COLUMNS = 9_216;

    public record SurveyRequest(String dimension, BlockPos center, int radius) {
        public SurveyRequest {
            dimension = Objects.requireNonNull(dimension, "dimension").trim();
            center = Objects.requireNonNull(center, "center").immutable();
            if (dimension.isEmpty()) throw new IllegalArgumentException("blank dimension");
            if (radius < MIN_RADIUS || radius > MAX_RADIUS)
                throw new IllegalArgumentException("survey radius must be between 8 and 96");
        }

        public static SurveyRequest around(String dimension, BlockPos center) {
            return new SurveyRequest(dimension, center, DEFAULT_RADIUS);
        }
    }

    public record SurveyResult(WorldSurveySnapshot snapshot) {
        public SurveyResult {
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    private WorldSurveyService() {}

    public static SurveyResult scan(WorldSurveySource source, SurveyRequest request) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(request, "request");
        int minY = source.minBuildHeight();
        int maxY = source.maxBuildHeight();
        if (minY >= maxY || request.center().getY() < minY || request.center().getY() >= maxY)
            throw new IllegalArgumentException("survey center is outside world height");
        long minX = (long) request.center().getX() - request.radius();
        long minZ = (long) request.center().getZ() - request.radius();
        long endX = (long) request.center().getX() + request.radius();
        long endZ = (long) request.center().getZ() + request.radius();
        if (minX < Integer.MIN_VALUE || minZ < Integer.MIN_VALUE
                || endX > (long) Integer.MAX_VALUE + 1L || endZ > (long) Integer.MAX_VALUE + 1L)
            throw new IllegalArgumentException("survey exceeds world coordinates");

        List<WorldSurveySnapshot.TerrainColumn> columns = new ArrayList<>();
        Set<Long> skipped = new HashSet<>();
        for (long x = minX; x < endX; x += 2L) {
            for (long z = minZ; z < endZ; z += 2L) {
                int sampleX = (int) x;
                int sampleZ = (int) z;
                int chunkX = sampleX >> 4;
                int chunkZ = sampleZ >> 4;
                if (!source.isChunkLoaded(chunkX, chunkZ)) {
                    skipped.add(chunkKey(chunkX, chunkZ));
                    continue;
                }
                WorldSurveySnapshot.TerrainColumn column =
                        Objects.requireNonNull(source.sampleColumn(sampleX, sampleZ), "sample column");
                if (column.x() != sampleX || column.z() != sampleZ
                        || column.y() < minY || column.y() >= maxY)
                    throw new IllegalArgumentException("source returned an out-of-world column");
                columns.add(column);
                if (columns.size() > MAX_SAMPLED_COLUMNS)
                    throw new IllegalStateException("survey column limit exceeded");
            }
        }

        List<WorldSurveySnapshot.BuildingOutline> outlines = sortedOutlines(
                source.buildingOutlines(request));
        List<WorldSurveySnapshot.Plot> plots = sortedPlots(source.plots(request));
        List<WorldSurveySnapshot.EnterpriseCore> cores = sortedCores(source.enterpriseCores(request));
        return new SurveyResult(new WorldSurveySnapshot(request.dimension(), request.center(),
                request.radius(), columns, outlines, plots, cores, skipped.size()));
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static List<WorldSurveySnapshot.BuildingOutline> sortedOutlines(
            List<WorldSurveySnapshot.BuildingOutline> values) {
        var result = new ArrayList<>(Objects.requireNonNull(values, "building outlines"));
        result.sort(Comparator.comparing(WorldSurveySnapshot.BuildingOutline::id)
                .thenComparingInt(WorldSurveySnapshot.BuildingOutline::minX)
                .thenComparingInt(WorldSurveySnapshot.BuildingOutline::minZ));
        return List.copyOf(result);
    }

    private static List<WorldSurveySnapshot.Plot> sortedPlots(List<WorldSurveySnapshot.Plot> values) {
        var result = new ArrayList<>(Objects.requireNonNull(values, "plots"));
        result.sort(Comparator.comparingInt(WorldSurveySnapshot.Plot::chunkX)
                .thenComparingInt(WorldSurveySnapshot.Plot::chunkZ)
                .thenComparing(WorldSurveySnapshot.Plot::townId));
        return List.copyOf(result);
    }

    private static List<WorldSurveySnapshot.EnterpriseCore> sortedCores(
            List<WorldSurveySnapshot.EnterpriseCore> values) {
        var result = new ArrayList<>(Objects.requireNonNull(values, "enterprise cores"));
        result.sort(Comparator.comparing(WorldSurveySnapshot.EnterpriseCore::enterpriseId));
        return List.copyOf(result);
    }
}
