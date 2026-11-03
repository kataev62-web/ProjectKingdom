package ru.projectkingdom.cityarchitect.plan;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

public final class CityArchitectRoadPlanner {
    private static final int MAX_CANDIDATES = 3;
    private static final int STEP_COST = 10;
    private static final int ELEVATION_COST = 8;
    private static final int WATER_COST = 40;
    private static final int CLEAR_COST = 3;
    private static final int TURN_COST = 4;
    private static final int ALTERNATIVE_PENALTY = 50;
    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    public List<RoadCandidate> plan(RoadRequest request) {
        Objects.requireNonNull(request, "request");
        SurfaceCell start = request.cells().get(cellKey(request.start().getX(), request.start().getZ()));
        SurfaceCell goal = request.cells().get(cellKey(request.goal().getX(), request.goal().getZ()));
        if (!usable(start) || !usable(goal)) {
            return List.of();
        }

        List<RoadCandidate> candidates = new ArrayList<>();
        Set<List<Long>> signatures = new HashSet<>();
        Set<Long> discouraged = new HashSet<>();
        for (int index = 0; index < MAX_CANDIDATES; index++) {
            Optional<List<BlockPos>> path = search(request, start, goal, discouraged);
            if (path.isEmpty()) {
                break;
            }
            List<BlockPos> centerline = path.orElseThrow();
            List<Long> signature = centerline.stream().map(BlockPos::asLong).toList();
            if (!signatures.add(signature)) {
                break;
            }
            candidates.add(new RoadCandidate(
                    "road-" + (index + 1), centerline, metrics(centerline, request.cells())));
            centerline.stream()
                    .skip(1)
                    .limit(Math.max(0, centerline.size() - 2L))
                    .mapToLong(pos -> cellKey(pos.getX(), pos.getZ()))
                    .forEach(discouraged::add);
        }
        candidates.sort(Comparator.comparingLong((RoadCandidate value) -> value.metrics().totalCost())
                .thenComparing(RoadCandidate::id));
        return List.copyOf(candidates);
    }

    private Optional<List<BlockPos>> search(
            RoadRequest request,
            SurfaceCell start,
            SurfaceCell goal,
            Set<Long> discouraged
    ) {
        Comparator<Node> order = Comparator.comparingLong(Node::estimatedTotal)
                .thenComparingLong(Node::cost)
                .thenComparingInt(node -> node.position().getX())
                .thenComparingInt(node -> node.position().getZ())
                .thenComparingInt(Node::direction);
        PriorityQueue<Node> open = new PriorityQueue<>(order);
        Map<StateKey, Long> best = new HashMap<>();
        Node initial = new Node(start.position(), -1, 0,
                heuristic(start.position(), goal.position()), null);
        open.add(initial);
        best.put(new StateKey(cellKey(start.position().getX(), start.position().getZ()), -1), 0L);

        int expanded = 0;
        while (!open.isEmpty()) {
            Node current = open.remove();
            StateKey currentKey = new StateKey(
                    cellKey(current.position().getX(), current.position().getZ()), current.direction());
            if (current.cost() != best.getOrDefault(currentKey, Long.MAX_VALUE)) {
                continue;
            }
            if (++expanded > request.maxNodes()) {
                return Optional.empty();
            }
            if (sameColumn(current.position(), goal.position())) {
                return Optional.of(reconstruct(current));
            }

            for (int direction = 0; direction < DIRECTIONS.length; direction++) {
                int nextX = current.position().getX() + DIRECTIONS[direction][0];
                int nextZ = current.position().getZ() + DIRECTIONS[direction][1];
                long nextCellKey = cellKey(nextX, nextZ);
                SurfaceCell next = request.cells().get(nextCellKey);
                if (!usable(next) || Math.abs(next.position().getY() - current.position().getY()) > 1) {
                    continue;
                }
                long nextCost = current.cost() + movementCost(current, direction, next,
                        discouraged.contains(nextCellKey));
                StateKey nextKey = new StateKey(nextCellKey, direction);
                if (nextCost >= best.getOrDefault(nextKey, Long.MAX_VALUE)) {
                    continue;
                }
                best.put(nextKey, nextCost);
                open.add(new Node(next.position(), direction, nextCost,
                        nextCost + heuristic(next.position(), goal.position()), current));
            }
        }
        return Optional.empty();
    }

    private static long movementCost(Node current, int direction, SurfaceCell next, boolean discouraged) {
        long result = STEP_COST;
        result += (long) Math.abs(next.position().getY() - current.position().getY()) * ELEVATION_COST;
        result += next.water() ? WATER_COST : 0;
        result += (long) next.clearCost() * CLEAR_COST;
        result += current.direction() >= 0 && current.direction() != direction ? TURN_COST : 0;
        result += discouraged ? ALTERNATIVE_PENALTY : 0;
        return result;
    }

    private static long heuristic(BlockPos current, BlockPos goal) {
        return (long) (Math.abs(goal.getX() - current.getX())
                + Math.abs(goal.getZ() - current.getZ())) * STEP_COST;
    }

    private static List<BlockPos> reconstruct(Node goal) {
        List<BlockPos> reversed = new ArrayList<>();
        for (Node node = goal; node != null; node = node.previous()) {
            reversed.add(node.position());
        }
        java.util.Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    private static RoadMetrics metrics(List<BlockPos> path, Map<Long, SurfaceCell> cells) {
        int elevation = 0;
        int water = 0;
        int clearing = 0;
        int turns = 0;
        int previousDirection = -1;
        for (int index = 1; index < path.size(); index++) {
            BlockPos before = path.get(index - 1);
            BlockPos current = path.get(index);
            elevation += Math.abs(current.getY() - before.getY());
            SurfaceCell cell = cells.get(cellKey(current.getX(), current.getZ()));
            if (cell.water()) {
                water++;
            }
            clearing += cell.clearCost();
            int direction = direction(before, current);
            if (previousDirection >= 0 && previousDirection != direction) {
                turns++;
            }
            previousDirection = direction;
        }
        int length = Math.max(0, path.size() - 1);
        long total = (long) length * STEP_COST
                + (long) elevation * ELEVATION_COST
                + (long) water * WATER_COST
                + (long) clearing * CLEAR_COST
                + (long) turns * TURN_COST;
        return new RoadMetrics(length, elevation, water, clearing, turns, total);
    }

    private static int direction(BlockPos before, BlockPos current) {
        int dx = current.getX() - before.getX();
        int dz = current.getZ() - before.getZ();
        for (int index = 0; index < DIRECTIONS.length; index++) {
            if (DIRECTIONS[index][0] == dx && DIRECTIONS[index][1] == dz) {
                return index;
            }
        }
        throw new IllegalArgumentException("Non-adjacent road cells");
    }

    private static boolean usable(SurfaceCell cell) {
        return cell != null && !cell.blocked();
    }

    private static boolean sameColumn(BlockPos first, BlockPos second) {
        return first.getX() == second.getX() && first.getZ() == second.getZ();
    }

    public static long cellKey(int x, int z) {
        return BlockPos.asLong(x, 0, z);
    }

    public record SurfaceCell(BlockPos position, boolean water, boolean blocked, int clearCost) {
        public SurfaceCell {
            position = Objects.requireNonNull(position, "position").immutable();
            if (clearCost < 0) {
                throw new IllegalArgumentException("clearCost must be non-negative");
            }
        }
    }

    public record RoadRequest(BlockPos start, BlockPos goal, Map<Long, SurfaceCell> cells, int maxNodes) {
        public RoadRequest {
            start = Objects.requireNonNull(start, "start").immutable();
            goal = Objects.requireNonNull(goal, "goal").immutable();
            cells = Map.copyOf(Objects.requireNonNull(cells, "cells"));
            if (maxNodes < 1 || maxNodes > 65_536) {
                throw new IllegalArgumentException("maxNodes must be between 1 and 65536");
            }
        }
    }

    public record RoadMetrics(
            int length,
            int elevationCost,
            int waterCost,
            int clearCost,
            int turns,
            long totalCost
    ) {
    }

    public record RoadCandidate(String id, List<BlockPos> centerline, RoadMetrics metrics) {
        public RoadCandidate {
            id = Objects.requireNonNull(id, "id");
            centerline = List.copyOf(Objects.requireNonNull(centerline, "centerline"));
            metrics = Objects.requireNonNull(metrics, "metrics");
        }
    }

    private record StateKey(long cellKey, int direction) {
    }

    private record Node(
            BlockPos position,
            int direction,
            long cost,
            long estimatedTotal,
            Node previous
    ) {
    }
}
