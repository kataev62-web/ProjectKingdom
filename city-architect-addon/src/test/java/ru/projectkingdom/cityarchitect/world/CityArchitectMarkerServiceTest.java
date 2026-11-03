package ru.projectkingdom.cityarchitect.world;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import ru.projectkingdom.cityarchitect.data.CityArchitectPlanData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CityArchitectMarkerServiceTest {
    @Test
    void placesAtMostSixteenMarkersPerTickAboveTheSurface() {
        Fixture fixture = fixture();
        List<CityArchitectPlanData.MarkerCell> cells = new ArrayList<>();
        for (int x = 0; x < 20; x++) {
            BlockPos surface = new BlockPos(x, 64, 0);
            fixture.world.force(surface, "minecraft:stone", false, false);
            cells.add(new CityArchitectPlanData.MarkerCell(surface, CityArchitectPlanData.MarkerKind.ROAD));
        }
        fixture.service.queue(fixture.planId, cells);

        var first = fixture.service.tick(100);

        assertEquals(16, first.changed());
        assertEquals(16, fixture.data.plan(fixture.planId).orElseThrow().markerChanges().size());
        assertEquals("minecraft:stone", fixture.world.state(new BlockPos(0, 64, 0)));
        assertEquals("minecraft:yellow_wool", fixture.world.state(new BlockPos(0, 65, 0)));
        assertTrue(fixture.service.hasWork(fixture.planId));
        assertEquals(4, fixture.service.tick(16).changed());
        assertFalse(fixture.service.hasWork(fixture.planId));
    }

    @Test
    void refusesUnsafeTargetsAndPausesOnUnloadedChunks() {
        Fixture fixture = fixture();
        BlockPos unsafeSurface = new BlockPos(0, 64, 0);
        BlockPos unloadedSurface = new BlockPos(1, 64, 0);
        fixture.world.force(unsafeSurface.above(), "minecraft:chest", true, false);
        fixture.world.unloaded.add(unloadedSurface.above());
        fixture.service.queue(fixture.planId, List.of(
                new CityArchitectPlanData.MarkerCell(unsafeSurface, CityArchitectPlanData.MarkerKind.ADMINISTRATION),
                new CityArchitectPlanData.MarkerCell(unloadedSurface, CityArchitectPlanData.MarkerKind.ROAD)));

        var result = fixture.service.tick(16);

        assertEquals(0, result.changed());
        assertEquals(1, result.refused());
        assertEquals(1, result.paused());
        assertEquals("minecraft:chest", fixture.world.state(unsafeSurface.above()));
        assertTrue(fixture.service.hasWork(fixture.planId));

        fixture.world.unloaded.clear();
        assertEquals(1, fixture.service.tick(16).changed());
    }

    @Test
    void restoresExactBeforeImageButPreservesPlayerEditsAsConflicts() {
        Fixture exact = fixture();
        BlockPos exactSurface = new BlockPos(4, 64, 4);
        exact.service.queue(exact.planId, List.of(new CityArchitectPlanData.MarkerCell(
                exactSurface, CityArchitectPlanData.MarkerKind.PRODUCTION)));
        exact.service.tick(16);
        var restored = exact.service.restore(exact.planId);
        assertEquals(1, restored.restored());
        assertEquals("minecraft:air", exact.world.state(exactSurface.above()));
        var exactChange = exact.data.plan(exact.planId).orElseThrow().markerChanges().get(0);
        assertTrue(exactChange.restored());
        assertFalse(exactChange.conflict());

        Fixture conflict = fixture();
        BlockPos conflictSurface = new BlockPos(8, 64, 8);
        conflict.service.queue(conflict.planId, List.of(new CityArchitectPlanData.MarkerCell(
                conflictSurface, CityArchitectPlanData.MarkerKind.HOUSING)));
        conflict.service.tick(16);
        conflict.world.force(conflictSurface.above(), "minecraft:gold_block", false, false);
        var conflictResult = conflict.service.restore(conflict.planId);
        assertEquals(1, conflictResult.conflicts());
        assertEquals("minecraft:gold_block", conflict.world.state(conflictSurface.above()));
        assertTrue(conflict.data.plan(conflict.planId).orElseThrow().markerChanges().get(0).conflict());
    }

    @Test
    void restartResumeDoesNotOverwriteTheOriginalBeforeImage() {
        Fixture fixture = fixture();
        List<CityArchitectPlanData.MarkerCell> cells = List.of(
                new CityArchitectPlanData.MarkerCell(new BlockPos(0, 64, 0), CityArchitectPlanData.MarkerKind.ROAD),
                new CityArchitectPlanData.MarkerCell(new BlockPos(1, 64, 0), CityArchitectPlanData.MarkerKind.ROAD));
        fixture.service.queue(fixture.planId, cells);
        assertEquals(1, fixture.service.tick(1).changed());

        CityArchitectMarkerService restarted = new CityArchitectMarkerService(fixture.data, fixture.world);
        restarted.queue(fixture.planId, cells);
        restarted.resume(fixture.planId);
        assertEquals(1, restarted.tick(16).changed());

        var journal = fixture.data.plan(fixture.planId).orElseThrow().markerChanges();
        assertEquals(2, journal.size());
        assertTrue(journal.stream().allMatch(change -> change.beforeState().equals("minecraft:air")));
    }

    @Test
    void usesFixedPaletteAndEnforcesMarkerCap() {
        Fixture fixture = fixture();
        Map<CityArchitectPlanData.MarkerKind, String> expected = Map.of(
                CityArchitectPlanData.MarkerKind.ROAD, "minecraft:yellow_wool",
                CityArchitectPlanData.MarkerKind.ADMINISTRATION, "minecraft:red_wool",
                CityArchitectPlanData.MarkerKind.PRODUCTION, "minecraft:orange_wool",
                CityArchitectPlanData.MarkerKind.COMMERCE_STORAGE, "minecraft:green_wool",
                CityArchitectPlanData.MarkerKind.HOUSING, "minecraft:light_blue_wool",
                CityArchitectPlanData.MarkerKind.WATER_PORT, "minecraft:blue_wool");
        int x = 0;
        for (var entry : expected.entrySet()) {
            BlockPos surface = new BlockPos(x++, 64, 0);
            fixture.service.queue(fixture.planId, List.of(
                    new CityArchitectPlanData.MarkerCell(surface, entry.getKey())));
            fixture.service.tick(16);
            assertEquals(entry.getValue(), fixture.world.state(surface.above()));
        }

        List<CityArchitectPlanData.MarkerCell> tooMany = new ArrayList<>();
        for (int i = 0; i <= CityArchitectPlanData.MAX_MARKERS; i++) {
            tooMany.add(new CityArchitectPlanData.MarkerCell(
                    new BlockPos(i, 64, 10), CityArchitectPlanData.MarkerKind.ROAD));
        }
        assertThrows(IllegalArgumentException.class, () -> fixture.service.queue(fixture.planId, tooMany));
    }

    private static Fixture fixture() {
        CityArchitectPlanData data = new CityArchitectPlanData();
        var plan = data.create(UUID.randomUUID(), UUID.randomUUID(), "minecraft:overworld", BlockPos.ZERO,
                List.of(CityArchitectPlanData.StageKind.ROADS_SQUARE));
        FakeWorld world = new FakeWorld();
        return new Fixture(data, world, new CityArchitectMarkerService(data, world), plan.id());
    }

    private record Fixture(
            CityArchitectPlanData data,
            FakeWorld world,
            CityArchitectMarkerService service,
            UUID planId
    ) {
    }

    private static final class FakeWorld implements CityArchitectMarkerService.WorldAccess {
        private final Map<BlockPos, CityArchitectMarkerService.WorldCell> cells = new HashMap<>();
        private final Set<BlockPos> unloaded = new HashSet<>();

        @Override
        public boolean loaded(BlockPos position) {
            return !unloaded.contains(position);
        }

        @Override
        public CityArchitectMarkerService.WorldCell inspect(BlockPos position) {
            return cells.getOrDefault(position,
                    new CityArchitectMarkerService.WorldCell("minecraft:air", false, false));
        }

        @Override
        public boolean safeReplaceable(BlockPos position, CityArchitectMarkerService.WorldCell cell) {
            return !cell.hasBlockEntity() && !cell.unbreakable()
                    && Set.of("minecraft:air", "minecraft:snow", "minecraft:short_grass").contains(cell.stateId());
        }

        @Override
        public boolean setExact(BlockPos position, String expectedState, String replacementState) {
            if (!state(position).equals(expectedState)) {
                return false;
            }
            force(position, replacementState, false, false);
            return true;
        }

        private String state(BlockPos position) {
            return inspect(position).stateId();
        }

        private void force(BlockPos position, String state, boolean blockEntity, boolean unbreakable) {
            cells.put(position.immutable(), new CityArchitectMarkerService.WorldCell(
                    state, blockEntity, unbreakable));
        }
    }
}
