package ru.projectkingdom.cityarchitect.world;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CityArchitectRoadDesignFactoryTest {
    private final CityArchitectRoadDesignFactory factory = new CityArchitectRoadDesignFactory();

    @Test
    void createsThreeWideRoadFollowingSurfaceHeight() {
        var design = factory.create(List.of(
                new BlockPos(0, 64, 0),
                new BlockPos(1, 65, 0),
                new BlockPos(2, 65, 0)));

        assertEquals(9, design.cells().size());
        for (int x = 0; x <= 2; x++) {
            int expectedY = x == 0 ? 64 : 65;
            for (int z = -1; z <= 1; z++) {
                BlockPos expected = new BlockPos(x, expectedY, z);
                assertTrue(design.cells().stream().anyMatch(cell -> cell.position().equals(expected)));
            }
        }
    }

    @Test
    void fillsCornersWithoutDuplicateCellsAndRejectsUnsafeGeometry() {
        var corner = factory.create(List.of(
                new BlockPos(0, 64, 0),
                new BlockPos(1, 64, 0),
                new BlockPos(1, 64, 1)));
        assertEquals(corner.cells().size(), corner.cells().stream()
                .map(CityArchitectRoadDesignFactory.RoadCell::position).distinct().count());
        assertTrue(corner.cells().stream().anyMatch(cell -> cell.position().equals(new BlockPos(1, 64, 0))));

        assertThrows(IllegalArgumentException.class, () -> factory.create(List.of(
                new BlockPos(0, 64, 0), new BlockPos(2, 64, 0))));
        assertThrows(IllegalArgumentException.class, () -> factory.create(List.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 66, 0))));
    }

    @Test
    void capsRoadDesignAtEightThousandOneHundredNinetyTwoCells() {
        List<BlockPos> longPath = new ArrayList<>();
        for (int x = 0; x < 2_732; x++) {
            longPath.add(new BlockPos(x, 64, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> factory.create(longPath));
    }
}
