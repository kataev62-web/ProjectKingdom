package ru.projectkingdom.cityarchitect;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import ru.projectkingdom.cityarchitect.data.CityArchitectPlanData;
import ru.projectkingdom.cityarchitect.plan.CityArchitectRoadPlanner;
import ru.projectkingdom.cityarchitect.plan.CityArchitectReasoner;
import ru.projectkingdom.cityarchitect.plan.DisabledCityArchitectModelClient;
import ru.projectkingdom.cityarchitect.world.CityArchitectLandmarks;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CityArchitectCoordinatorTest {
    @Test
    void missingAmbiguousAndUnauthorizedRequestsDoNotMutatePlansOrMarkers() throws Exception {
        Fixture missing = fixture(List.of(hall()), List.of());
        assertEquals(CityArchitectCoordinator.ResultCode.LANDMARK_MISSING,
                missing.request(4).code());
        assertTrue(missing.data.allPlans().isEmpty());
        assertTrue(missing.markers.isEmpty());

        Fixture ambiguous = fixture(List.of(hall(), smithy(20), smithy(30)), List.of());
        assertEquals(CityArchitectCoordinator.ResultCode.LANDMARK_AMBIGUOUS,
                ambiguous.request(4).code());
        assertTrue(ambiguous.data.allPlans().isEmpty());

        Fixture unauthorized = fixture(List.of(hall(), smithy(20)), List.of());
        assertEquals(CityArchitectCoordinator.ResultCode.NO_PERMISSION,
                unauthorized.request(3).code());
        assertTrue(unauthorized.data.allPlans().isEmpty());
        assertTrue(unauthorized.markers.isEmpty());
    }

    @Test
    void wrongDimensionAndNoSafePathDoNotMutateWorld() throws Exception {
        var netherSmithy = new CityArchitectLandmarks.Landmark(
                UUID.randomUUID(), CityArchitectLandmarks.LandmarkType.SMITHY, "Кузница",
                "minecraft:the_nether", new BlockPos(20, 64, 0));
        Fixture wrongDimension = fixture(List.of(hall(), netherSmithy), List.of());
        assertEquals(CityArchitectCoordinator.ResultCode.LANDMARK_MISSING,
                wrongDimension.request(4).code());
        assertTrue(wrongDimension.data.allPlans().isEmpty());

        Fixture noPath = fixture(List.of(hall(), smithy(20)), List.of());
        noPath.snapshot = (start, goal) -> new CityArchitectRoadPlanner.RoadRequest(
                start, goal, Map.of(), 32);
        assertEquals(CityArchitectCoordinator.ResultCode.NO_SAFE_PATH, noPath.request(4).code());
        assertTrue(noPath.data.allPlans().isEmpty());
        assertTrue(noPath.markers.isEmpty());
    }

    @Test
    void acceptedCandidateCreatesMarkedRoadPlanAndQueuesYellowSurfaceMarkers() throws Exception {
        Fixture fixture = fixture(List.of(hall(), smithy(8)), List.of());

        var result = fixture.request(4);

        assertEquals(CityArchitectCoordinator.ResultCode.MARKED, result.code());
        assertTrue(result.explanation().contains("без модели"));
        var plan = fixture.data.plan(result.planId()).orElseThrow();
        assertEquals(CityArchitectPlanData.PlanStatus.MARKED, plan.status());
        assertEquals(1, plan.stages().get(0).roads().size());
        assertEquals(3, plan.stages().get(0).roads().get(0).width());
        assertFalse(fixture.markers.isEmpty());
        assertTrue(fixture.markers.stream().allMatch(
                marker -> marker.kind() == CityArchitectPlanData.MarkerKind.ROAD));
    }

    private static Fixture fixture(
            List<CityArchitectLandmarks.Landmark> landmarks,
            List<CityArchitectPlanData.MarkerCell> ignored
    ) {
        CityArchitectPlanData data = new CityArchitectPlanData();
        List<CityArchitectPlanData.MarkerCell> markers = new ArrayList<>();
        Fixture fixture = new Fixture(data, markers);
        fixture.landmarks = new CityArchitectLandmarks(() -> landmarks);
        fixture.snapshot = Fixture::flatSnapshot;
        return fixture;
    }

    private static CityArchitectLandmarks.Landmark hall() {
        return new CityArchitectLandmarks.Landmark(
                UUID.randomUUID(), CityArchitectLandmarks.LandmarkType.TOWN_HALL, "Ратуша",
                "minecraft:overworld", new BlockPos(0, 64, 0));
    }

    private static CityArchitectLandmarks.Landmark smithy(int x) {
        return new CityArchitectLandmarks.Landmark(
                UUID.randomUUID(), CityArchitectLandmarks.LandmarkType.SMITHY, "Кузница",
                "minecraft:overworld", new BlockPos(x, 64, 0));
    }

    private static final class Fixture {
        private final CityArchitectPlanData data;
        private final List<CityArchitectPlanData.MarkerCell> markers;
        private CityArchitectLandmarks landmarks;
        private CityArchitectCoordinator.SurfaceSnapshotProvider snapshot;

        private Fixture(CityArchitectPlanData data, List<CityArchitectPlanData.MarkerCell> markers) {
            this.data = data;
            this.markers = markers;
        }

        private CityArchitectCoordinator.RoadResult request(int permission) throws Exception {
            CityArchitectCoordinator coordinator = new CityArchitectCoordinator(
                    data,
                    landmarks,
                    new CityArchitectRoadPlanner(),
                    new CityArchitectReasoner(new DisabledCityArchitectModelClient(), Duration.ofMillis(50)),
                    snapshot,
                    (planId, cells) -> markers.addAll(cells),
                    Runnable::run);
            var context = new CityArchitectCoordinator.RequestContext(
                    UUID.randomUUID(), UUID.randomUUID(), "minecraft:overworld", permission);
            return coordinator.requestRoad(context, "от ратуши до кузницы").get(1, TimeUnit.SECONDS);
        }

        private static CityArchitectRoadPlanner.RoadRequest flatSnapshot(BlockPos start, BlockPos goal) {
            Map<Long, CityArchitectRoadPlanner.SurfaceCell> cells = new HashMap<>();
            int minX = Math.min(start.getX(), goal.getX());
            int maxX = Math.max(start.getX(), goal.getX());
            for (int x = minX; x <= maxX; x++) {
                BlockPos position = new BlockPos(x, 64, 0);
                cells.put(CityArchitectRoadPlanner.cellKey(x, 0),
                        new CityArchitectRoadPlanner.SurfaceCell(position, false, false, 0));
            }
            return new CityArchitectRoadPlanner.RoadRequest(start, goal, cells, 256);
        }
    }
}
