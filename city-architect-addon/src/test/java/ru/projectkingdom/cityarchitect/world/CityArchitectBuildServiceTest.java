package ru.projectkingdom.cityarchitect.world;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CityArchitectBuildServiceTest {
    @Test
    void buildsAtMostSixteenSafeLoadedCellsPerTickAndResumesFromStore() {
        FakeWorld world = new FakeWorld();
        CityArchitectBuildService.MemoryStore store = new CityArchitectBuildService.MemoryStore();
        UUID plan = UUID.randomUUID();
        UUID stage = UUID.randomUUID();
        List<CityArchitectRoadDesignFactory.RoadCell> cells = java.util.stream.IntStream.range(0, 20)
                .mapToObj(x -> new CityArchitectRoadDesignFactory.RoadCell(
                        new BlockPos(x, 64, 0), "minecraft:cobblestone"))
                .toList();
        CityArchitectBuildService service = new CityArchitectBuildService(store, world);
        service.queue(plan, stage, cells);

        assertEquals(16, service.tick(100).changed());
        assertFalse(service.complete(plan));

        CityArchitectBuildService restarted = new CityArchitectBuildService(store, world);
        restarted.resume(plan);
        assertEquals(4, restarted.tick(16).changed());
        assertTrue(restarted.complete(plan));
    }

    @Test
    void pausesUnloadedCellsRefusesProtectedBlocksAndRollsBackExactWrites() {
        FakeWorld world = new FakeWorld();
        CityArchitectBuildService.MemoryStore store = new CityArchitectBuildService.MemoryStore();
        UUID plan = UUID.randomUUID();
        UUID stage = UUID.randomUUID();
        BlockPos safe = new BlockPos(0, 64, 0);
        BlockPos unloaded = new BlockPos(1, 64, 0);
        BlockPos chest = new BlockPos(2, 64, 0);
        world.unloaded.add(unloaded);
        world.force(chest, "minecraft:chest", true, false);
        CityArchitectBuildService service = new CityArchitectBuildService(store, world);
        service.queue(plan, stage, List.of(
                new CityArchitectRoadDesignFactory.RoadCell(safe, "minecraft:cobblestone"),
                new CityArchitectRoadDesignFactory.RoadCell(unloaded, "minecraft:cobblestone"),
                new CityArchitectRoadDesignFactory.RoadCell(chest, "minecraft:cobblestone")));

        var first = service.tick(16);
        assertEquals(1, first.changed());
        assertEquals(1, first.paused());
        assertEquals("minecraft:chest", world.state(chest));

        world.unloaded.clear();
        var second = service.tick(16);
        assertEquals(1, second.refused());
        assertEquals("minecraft:chest", world.state(chest));

        var rollback = service.rollback(plan);
        assertEquals(2, rollback.restored());
        assertEquals("minecraft:air", world.state(safe));
        assertEquals("minecraft:air", world.state(unloaded));
        assertEquals("minecraft:chest", world.state(chest));
    }

    @Test
    void rollbackPreservesPlayerEditAsConflict() {
        FakeWorld world = new FakeWorld();
        CityArchitectBuildService.MemoryStore store = new CityArchitectBuildService.MemoryStore();
        UUID plan = UUID.randomUUID();
        BlockPos position = new BlockPos(3, 64, 3);
        CityArchitectBuildService service = new CityArchitectBuildService(store, world);
        service.queue(plan, UUID.randomUUID(), List.of(
                new CityArchitectRoadDesignFactory.RoadCell(position, "minecraft:cobblestone")));
        service.tick(16);
        world.force(position, "minecraft:gold_block", false, false);

        assertEquals(1, service.rollback(plan).conflicts());
        assertEquals("minecraft:gold_block", world.state(position));
    }

    private static final class FakeWorld implements CityArchitectBuildService.WorldAccess {
        private final Map<BlockPos, CityArchitectBuildService.WorldCell> cells = new HashMap<>();
        private final Set<BlockPos> unloaded = new HashSet<>();

        @Override
        public boolean loaded(BlockPos position) {
            return !unloaded.contains(position);
        }

        @Override
        public CityArchitectBuildService.WorldCell inspect(BlockPos position) {
            return cells.getOrDefault(position,
                    new CityArchitectBuildService.WorldCell("minecraft:air", false, false));
        }

        @Override
        public boolean safeReplaceable(BlockPos position, CityArchitectBuildService.WorldCell cell) {
            return !cell.hasBlockEntity() && !cell.unbreakable();
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
            cells.put(position.immutable(), new CityArchitectBuildService.WorldCell(
                    state, blockEntity, unbreakable));
        }
    }
}
