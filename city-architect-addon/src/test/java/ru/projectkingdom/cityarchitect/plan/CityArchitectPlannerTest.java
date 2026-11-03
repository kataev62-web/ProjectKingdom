package ru.projectkingdom.cityarchitect.plan;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import ru.projectkingdom.cityarchitect.data.CityArchitectPlanData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CityArchitectPlannerTest {
    private final CityArchitectPlanner planner = new CityArchitectPlanner();

    @Test
    void createsStableSixStageLayoutInsideSurveyWithoutOverlaps() {
        var request = request(blueprints());

        var first = planner.plan(request);
        var second = planner.plan(request);

        assertEquals(first, second);
        assertFalse(first.candidates().isEmpty());
        var city = first.candidates().get(0);
        assertTrue(city.stages().size() <= 6);
        assertEquals(new BlockPos(0, 64, 0), city.ratHall());
        assertTrue(city.stages().get(0).roads().stream().allMatch(road -> road.width() == 3));

        List<CityArchitectPlanner.BuildingPlacement> buildings = city.stages().stream()
                .flatMap(stage -> stage.buildings().stream()).toList();
        for (int i = 0; i < buildings.size(); i++) {
            assertTrue(withinRadius(buildings.get(i), 64));
            for (int j = i + 1; j < buildings.size(); j++) {
                assertFalse(overlaps(buildings.get(i), buildings.get(j)));
            }
        }

        var market = building(buildings, "market");
        assertTrue(distance(market.origin(), BlockPos.ZERO) <= 32);
        var smithy = building(buildings, "smithy");
        var house = building(buildings, "house");
        assertTrue(distance(smithy.origin(), house.origin()) >= 24);
    }

    @Test
    void reportsMissingRequiredBlueprintInsteadOfInventingBuilding() {
        List<CityArchitectPlanner.BlueprintFootprint> withoutBank = blueprints().stream()
                .filter(value -> !value.role().equals("bank"))
                .toList();

        var result = planner.plan(request(withoutBank));

        assertTrue(result.candidates().isEmpty());
        assertTrue(result.findings().stream().anyMatch(finding ->
                finding.code() == CityArchitectPlanner.FindingCode.MISSING_BLUEPRINT
                        && finding.message().contains("bank")));
    }

    private static CityArchitectPlanner.CityPlanningRequest request(
            List<CityArchitectPlanner.BlueprintFootprint> blueprints
    ) {
        Map<Long, CityArchitectPlanner.SurfaceCell> surface = new HashMap<>();
        for (int x = -64; x <= 64; x++) {
            for (int z = -64; z <= 64; z++) {
                surface.put(CityArchitectPlanner.cellKey(x, z),
                        new CityArchitectPlanner.SurfaceCell(new BlockPos(x, 64, z), true, false));
            }
        }
        return new CityArchitectPlanner.CityPlanningRequest(
                new BlockPos(0, 64, 0), surface,
                List.of(new CityArchitectPlanner.Outline(-5, -5, 5, 5)),
                blueprints, 64);
    }

    private static List<CityArchitectPlanner.BlueprintFootprint> blueprints() {
        return List.of(
                blueprint("bank", 7, 7, CityArchitectPlanData.StageKind.ADMINISTRATION),
                blueprint("warehouse", 9, 9, CityArchitectPlanData.StageKind.ADMINISTRATION),
                blueprint("market", 9, 9, CityArchitectPlanData.StageKind.ADMINISTRATION),
                blueprint("smithy", 9, 9, CityArchitectPlanData.StageKind.PRODUCTION),
                blueprint("tavern", 9, 9, CityArchitectPlanData.StageKind.HOUSING_HOSPITALITY),
                blueprint("house", 7, 7, CityArchitectPlanData.StageKind.HOUSING_HOSPITALITY));
    }

    private static CityArchitectPlanner.BlueprintFootprint blueprint(
            String role, int width, int length, CityArchitectPlanData.StageKind stage
    ) {
        return new CityArchitectPlanner.BlueprintFootprint(
                "kingdomnpc:" + role, role, width, length, stage);
    }

    private static CityArchitectPlanner.BuildingPlacement building(
            List<CityArchitectPlanner.BuildingPlacement> values, String role
    ) {
        return values.stream().filter(value -> value.role().equals(role)).findFirst().orElseThrow();
    }

    private static boolean withinRadius(CityArchitectPlanner.BuildingPlacement value, int radius) {
        return Math.max(Math.abs(value.origin().getX()), Math.abs(value.origin().getZ())) <= radius
                && Math.max(Math.abs(value.origin().getX() + value.width() - 1),
                Math.abs(value.origin().getZ() + value.length() - 1)) <= radius;
    }

    private static boolean overlaps(
            CityArchitectPlanner.BuildingPlacement first,
            CityArchitectPlanner.BuildingPlacement second
    ) {
        return first.origin().getX() <= second.origin().getX() + second.width() - 1
                && first.origin().getX() + first.width() - 1 >= second.origin().getX()
                && first.origin().getZ() <= second.origin().getZ() + second.length() - 1
                && first.origin().getZ() + first.length() - 1 >= second.origin().getZ();
    }

    private static int distance(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX()) + Math.abs(first.getZ() - second.getZ());
    }
}
