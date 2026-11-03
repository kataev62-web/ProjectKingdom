package ru.projectkingdom.cityarchitect.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CityArchitectPlanDataTest {
    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ASSISTANT = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test
    void enforcesLegalStageTransitionsAndOneActivePlanPerOwner() {
        CityArchitectPlanData data = new CityArchitectPlanData();
        var plan = data.create(OWNER, ASSISTANT, "minecraft:overworld", new BlockPos(10, 64, 20),
                List.of(CityArchitectPlanData.StageKind.ROADS_SQUARE,
                        CityArchitectPlanData.StageKind.ADMINISTRATION));
        UUID first = plan.stages().get(0).id();
        UUID second = plan.stages().get(1).id();

        assertThrows(IllegalStateException.class, () -> data.create(
                OWNER, ASSISTANT, "minecraft:overworld", BlockPos.ZERO,
                List.of(CityArchitectPlanData.StageKind.ROADS_SQUARE)));
        assertThrows(IllegalStateException.class, () -> data.approve(plan.id(), first));

        data.marking(plan.id(), first, "road-1", "короткий сухой маршрут", List.of(), List.of());
        assertEquals(CityArchitectPlanData.PlanStatus.MARKED, data.plan(plan.id()).orElseThrow().status());
        data.approve(plan.id(), first);
        data.building(plan.id(), first);
        data.completeStage(plan.id(), first);

        var afterFirst = data.plan(plan.id()).orElseThrow();
        assertEquals(CityArchitectPlanData.PlanStatus.DRAFT, afterFirst.status());
        assertEquals(CityArchitectPlanData.StageStatus.COMPLETE, afterFirst.stages().get(0).status());
        assertEquals(CityArchitectPlanData.StageStatus.PENDING, afterFirst.stages().get(1).status());

        data.marking(plan.id(), second, "admin-1", "место у площади", List.of(), List.of());
        data.approve(plan.id(), second);
        data.building(plan.id(), second);
        data.completeStage(plan.id(), second);
        assertEquals(CityArchitectPlanData.PlanStatus.COMPLETE, data.plan(plan.id()).orElseThrow().status());
        assertTrue(data.ownedBy(OWNER).isEmpty());

        assertTrue(data.create(OWNER, ASSISTANT, "minecraft:overworld", BlockPos.ZERO,
                List.of(CityArchitectPlanData.StageKind.ROADS_SQUARE)).id() != null);
    }

    @Test
    void cancellationAndFailureAreTerminal() {
        CityArchitectPlanData data = new CityArchitectPlanData();
        var cancelled = createSingle(data, OWNER);
        data.cancel(cancelled.id());
        assertEquals(CityArchitectPlanData.PlanStatus.CANCELLED,
                data.plan(cancelled.id()).orElseThrow().status());
        assertThrows(IllegalStateException.class,
                () -> data.marking(cancelled.id(), cancelled.stages().get(0).id(), "x", "x", List.of(), List.of()));

        var failed = createSingle(data, UUID.fromString("10000000-0000-0000-0000-000000000003"));
        data.failStage(failed.id(), failed.stages().get(0).id(), "нет безопасного пути");
        assertEquals(CityArchitectPlanData.PlanStatus.FAILED, data.plan(failed.id()).orElseThrow().status());
        assertEquals("нет безопасного пути", data.plan(failed.id()).orElseThrow().failure());
    }

    @Test
    void roundTripsEveryPlanFieldAndPartialMarkerJournal() {
        CityArchitectPlanData data = new CityArchitectPlanData();
        var plan = createSingle(data, OWNER);
        UUID stageId = plan.stages().get(0).id();
        var building = new CityArchitectPlanData.BuildingPlacement(
                UUID.randomUUID(), "kingdomnpc:smithy", new BlockPos(8, 65, 9), 1,
                CityArchitectPlanData.StageKind.ROADS_SQUARE);
        var road = new CityArchitectPlanData.RoadPlacement(
                UUID.randomUUID(), List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0)), 3,
                CityArchitectPlanData.StageKind.ROADS_SQUARE);
        data.marking(plan.id(), stageId, "road-2", "обходит воду", List.of(building), List.of(road));
        data.recordMarkerChange(plan.id(), new CityArchitectPlanData.MarkerChange(
                new BlockPos(1, 65, 0), CityArchitectPlanData.MarkerKind.ROAD,
                "minecraft:air", "minecraft:yellow_wool", false, false));
        data.approve(plan.id(), stageId);
        data.building(plan.id(), stageId);
        data.recordCompletedPlacement(plan.id(), new CityArchitectPlanData.CompletedPlacement(
                "road", "road-2", new BlockPos(0, 64, 0), 1234L));

        CityArchitectPlanData loaded = CityArchitectPlanData.load(data.save(new CompoundTag()));

        assertEquals(data.plan(plan.id()), loaded.plan(plan.id()));
        assertEquals(1, loaded.plan(plan.id()).orElseThrow().markerChanges().size());
        assertFalse(loaded.plan(plan.id()).orElseThrow().markerChanges().get(0).restored());
        assertEquals(0, loaded.quarantinedRows());
    }

    @Test
    void quarantinesCorruptRowsWithoutLosingValidPlans() {
        CityArchitectPlanData data = new CityArchitectPlanData();
        var valid = createSingle(data, OWNER);
        CompoundTag saved = data.save(new CompoundTag());
        saved.getList("Plans", 10).add(new CompoundTag());

        CityArchitectPlanData loaded = CityArchitectPlanData.load(saved);

        assertTrue(loaded.plan(valid.id()).isPresent());
        assertEquals(1, loaded.quarantinedRows());
    }

    @Test
    void enforcesStageBuildingAndMarkerLimits() {
        CityArchitectPlanData data = new CityArchitectPlanData();
        assertThrows(IllegalArgumentException.class, () -> data.create(
                OWNER, ASSISTANT, "minecraft:overworld", BlockPos.ZERO,
                List.of(
                        CityArchitectPlanData.StageKind.ROADS_SQUARE,
                        CityArchitectPlanData.StageKind.ADMINISTRATION,
                        CityArchitectPlanData.StageKind.PRODUCTION,
                        CityArchitectPlanData.StageKind.HOUSING_HOSPITALITY,
                        CityArchitectPlanData.StageKind.SECONDARY_INFRASTRUCTURE,
                        CityArchitectPlanData.StageKind.EXPANSION,
                        CityArchitectPlanData.StageKind.EXPANSION)));

        var plan = createSingle(data, OWNER);
        List<CityArchitectPlanData.BuildingPlacement> tooMany = new ArrayList<>();
        for (int i = 0; i <= CityArchitectPlanData.MAX_BUILDINGS; i++) {
            tooMany.add(new CityArchitectPlanData.BuildingPlacement(
                    UUID.randomUUID(), "test:" + i, new BlockPos(i, 64, 0), 0,
                    CityArchitectPlanData.StageKind.ROADS_SQUARE));
        }
        assertThrows(IllegalArgumentException.class, () -> data.marking(
                plan.id(), plan.stages().get(0).id(), "candidate", "explanation", tooMany, List.of()));

        for (int i = 0; i < CityArchitectPlanData.MAX_MARKERS; i++) {
            data.recordMarkerChange(plan.id(), new CityArchitectPlanData.MarkerChange(
                    new BlockPos(i, 70, 0), CityArchitectPlanData.MarkerKind.ROAD,
                    "minecraft:air", "minecraft:yellow_wool", false, false));
        }
        assertThrows(IllegalStateException.class, () -> data.recordMarkerChange(
                plan.id(), new CityArchitectPlanData.MarkerChange(
                        new BlockPos(20_000, 70, 0), CityArchitectPlanData.MarkerKind.ROAD,
                        "minecraft:air", "minecraft:yellow_wool", false, false)));
    }

    private static CityArchitectPlanData.PlanView createSingle(CityArchitectPlanData data, UUID owner) {
        return data.create(owner, ASSISTANT, "minecraft:overworld", new BlockPos(0, 64, 0),
                List.of(CityArchitectPlanData.StageKind.ROADS_SQUARE));
    }
}
