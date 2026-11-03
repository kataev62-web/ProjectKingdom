package ru.projectkingdom.cityarchitect.world;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CityArchitectRoadDesignFactory {
    public static final int MAX_CELLS = 8_192;
    public static final String ROAD_STATE = "minecraft:cobblestone";

    public RoadDesign create(List<BlockPos> input) {
        List<BlockPos> centerline = List.copyOf(Objects.requireNonNull(input, "centerline"));
        if (centerline.isEmpty()) {
            throw new IllegalArgumentException("Road centerline must not be empty");
        }
        for (int index = 1; index < centerline.size(); index++) {
            BlockPos previous = centerline.get(index - 1);
            BlockPos current = centerline.get(index);
            int horizontal = Math.abs(current.getX() - previous.getX())
                    + Math.abs(current.getZ() - previous.getZ());
            if (horizontal != 1 || Math.abs(current.getY() - previous.getY()) > 1) {
                throw new IllegalArgumentException("Road centerline contains a non-adjacent step");
            }
        }

        Map<BlockPos, RoadCell> cells = new LinkedHashMap<>();
        for (int index = 0; index < centerline.size(); index++) {
            BlockPos current = centerline.get(index);
            BlockPos neighbor = index + 1 < centerline.size()
                    ? centerline.get(index + 1)
                    : index > 0 ? centerline.get(index - 1) : current.east();
            int dx = Integer.signum(neighbor.getX() - current.getX());
            int dz = Integer.signum(neighbor.getZ() - current.getZ());
            add(cells, current);
            add(cells, current.offset(-dz, 0, dx));
            add(cells, current.offset(dz, 0, -dx));
            if (cells.size() > MAX_CELLS) {
                throw new IllegalArgumentException("Road design exceeds " + MAX_CELLS + " cells");
            }
        }
        return new RoadDesign(List.copyOf(cells.values()));
    }

    private static void add(Map<BlockPos, RoadCell> cells, BlockPos position) {
        BlockPos immutable = position.immutable();
        cells.putIfAbsent(immutable, new RoadCell(immutable, ROAD_STATE));
    }

    public record RoadCell(BlockPos position, String stateId) {
        public RoadCell {
            position = Objects.requireNonNull(position, "position").immutable();
            stateId = Objects.requireNonNull(stateId, "stateId").trim();
            if (stateId.isEmpty()) {
                throw new IllegalArgumentException("stateId must not be blank");
            }
        }
    }

    public record RoadDesign(List<RoadCell> cells) {
        public RoadDesign {
            cells = List.copyOf(Objects.requireNonNull(cells, "cells"));
        }
    }
}
