package ru.projectkingdom.assistant;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Objects;

/** Immutable, bounded description of already-loaded world data. */
public record WorldSurveySnapshot(
        String dimension,
        BlockPos center,
        int radius,
        List<TerrainColumn> columns,
        List<BuildingOutline> buildingOutlines,
        List<Plot> plots,
        List<EnterpriseCore> enterpriseCores,
        int skippedChunks
) {
    public record TerrainColumn(int x, int z, int y, boolean water, boolean road) {}

    public record BuildingOutline(
            String id, String kind, int minX, int minZ, int maxX, int maxZ) {
        public BuildingOutline {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
            if (minX > maxX || minZ > maxZ) throw new IllegalArgumentException("invalid outline");
        }
    }

    public record Plot(String townId, int chunkX, int chunkZ, String zone) {
        public Plot {
            Objects.requireNonNull(townId, "townId");
            Objects.requireNonNull(zone, "zone");
        }
    }

    public record EnterpriseCore(String enterpriseId, String kind, BlockPos position) {
        public EnterpriseCore {
            Objects.requireNonNull(enterpriseId, "enterpriseId");
            Objects.requireNonNull(kind, "kind");
            position = Objects.requireNonNull(position, "position").immutable();
        }
    }

    public WorldSurveySnapshot {
        dimension = Objects.requireNonNull(dimension, "dimension").trim();
        if (dimension.isEmpty()) throw new IllegalArgumentException("blank dimension");
        center = Objects.requireNonNull(center, "center").immutable();
        if (radius < 8 || radius > 96 || skippedChunks < 0)
            throw new IllegalArgumentException("invalid survey bounds");
        columns = List.copyOf(columns);
        buildingOutlines = List.copyOf(buildingOutlines);
        plots = List.copyOf(plots);
        enterpriseCores = List.copyOf(enterpriseCores);
    }
}
