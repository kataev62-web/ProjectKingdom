package ru.projectkingdom.cityarchitect.plan;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CityArchitectRoadPlannerTest {
    private final CityArchitectRoadPlanner planner = new CityArchitectRoadPlanner();

    @Test
    void findsShortestFlatPathAndReportsMetrics() {
        Map<Long, CityArchitectRoadPlanner.SurfaceCell> cells = flatGrid(0, 4, 0, 2, 64);

        List<CityArchitectRoadPlanner.RoadCandidate> result = planner.plan(request(
                new BlockPos(0, 64, 1), new BlockPos(4, 64, 1), cells, 128));

        assertFalse(result.isEmpty());
        var primary = result.get(0);
        assertEquals(new BlockPos(0, 64, 1), primary.centerline().get(0));
        assertEquals(new BlockPos(4, 64, 1), primary.centerline().get(primary.centerline().size() - 1));
        assertEquals(5, primary.centerline().size());
        assertEquals(4, primary.metrics().length());
        assertEquals(0, primary.metrics().elevationCost());
        assertEquals(0, primary.metrics().waterCost());
    }

    @Test
    void choosesDryDetourWhenWaterIsMoreExpensive() {
        Map<Long, CityArchitectRoadPlanner.SurfaceCell> cells = flatGrid(0, 4, 0, 2, 64);
        for (int x = 1; x < 4; x++) {
            put(cells, new BlockPos(x, 64, 1), true, false, 0);
        }

        var primary = planner.plan(request(
                new BlockPos(0, 64, 1), new BlockPos(4, 64, 1), cells, 128)).get(0);

        assertEquals(0, primary.metrics().waterCost());
        assertTrue(primary.centerline().stream().anyMatch(pos -> pos.getZ() != 1));
    }

    @Test
    void treatsSteepBlockedAndMissingSurfaceCellsAsImpassable() {
        Map<Long, CityArchitectRoadPlanner.SurfaceCell> steep = new HashMap<>();
        put(steep, new BlockPos(0, 64, 0), false, false, 0);
        put(steep, new BlockPos(1, 66, 0), false, false, 0);
        put(steep, new BlockPos(2, 66, 0), false, false, 0);
        assertTrue(planner.plan(request(new BlockPos(0, 64, 0), new BlockPos(2, 66, 0), steep, 32)).isEmpty());

        Map<Long, CityArchitectRoadPlanner.SurfaceCell> blocked = flatGrid(0, 2, 0, 0, 64);
        put(blocked, new BlockPos(1, 64, 0), false, true, 0);
        assertTrue(planner.plan(request(new BlockPos(0, 64, 0), new BlockPos(2, 64, 0), blocked, 32)).isEmpty());

        Map<Long, CityArchitectRoadPlanner.SurfaceCell> missing = new HashMap<>();
        put(missing, new BlockPos(0, 64, 0), false, false, 0);
        put(missing, new BlockPos(2, 64, 0), false, false, 0);
        assertTrue(planner.plan(request(new BlockPos(0, 64, 0), new BlockPos(2, 64, 0), missing, 32)).isEmpty());
    }

    @Test
    void returnsAtMostThreeCandidatesInStableOrder() {
        Map<Long, CityArchitectRoadPlanner.SurfaceCell> cells = flatGrid(0, 6, 0, 4, 64);
        var request = request(new BlockPos(0, 64, 2), new BlockPos(6, 64, 2), cells, 256);

        var first = planner.plan(request);
        var second = planner.plan(request);

        assertFalse(first.isEmpty());
        assertTrue(first.size() <= 3);
        assertEquals(first, second);
        assertEquals(first.size(), first.stream().map(CityArchitectRoadPlanner.RoadCandidate::id).distinct().count());
    }

    @Test
    void stopsWhenNodeBudgetIsExhausted() {
        Map<Long, CityArchitectRoadPlanner.SurfaceCell> cells = flatGrid(0, 40, 0, 0, 64);

        assertTrue(planner.plan(request(
                new BlockPos(0, 64, 0), new BlockPos(40, 64, 0), cells, 32)).isEmpty());
    }

    private static CityArchitectRoadPlanner.RoadRequest request(
            BlockPos start,
            BlockPos goal,
            Map<Long, CityArchitectRoadPlanner.SurfaceCell> cells,
            int maxNodes
    ) {
        return new CityArchitectRoadPlanner.RoadRequest(start, goal, cells, maxNodes);
    }

    private static Map<Long, CityArchitectRoadPlanner.SurfaceCell> flatGrid(
            int minX, int maxX, int minZ, int maxZ, int y
    ) {
        Map<Long, CityArchitectRoadPlanner.SurfaceCell> cells = new HashMap<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                put(cells, new BlockPos(x, y, z), false, false, 0);
            }
        }
        return cells;
    }

    private static void put(
            Map<Long, CityArchitectRoadPlanner.SurfaceCell> cells,
            BlockPos position,
            boolean water,
            boolean blocked,
            int clearCost
    ) {
        cells.put(CityArchitectRoadPlanner.cellKey(position.getX(), position.getZ()),
                new CityArchitectRoadPlanner.SurfaceCell(position, water, blocked, clearCost));
    }
}
