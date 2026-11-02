package ru.projectkingdom.assistant;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class WorldSurveyServiceTest {
    @Test void radiusOutsideEightToNinetySixIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorldSurveyService.SurveyRequest("minecraft:overworld", BlockPos.ZERO, 7));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldSurveyService.SurveyRequest("minecraft:overworld", BlockPos.ZERO, 97));
    }

    @Test void outOfWorldCenterIsRejectedBeforeAnyBlockRead() {
        Fixture source = new Fixture(true, -64, 320);

        assertThrows(IllegalArgumentException.class, () -> WorldSurveyService.scan(source,
                new WorldSurveyService.SurveyRequest(
                        "minecraft:overworld", new BlockPos(0, 320, 0), 8)));
        assertEquals(0, source.blockReads);
    }

    @Test void unloadedChunksAreSkippedWithoutBlockReads() {
        Fixture source = new Fixture(false, -64, 320);

        WorldSurveyService.SurveyResult result = WorldSurveyService.scan(source,
                new WorldSurveyService.SurveyRequest(
                        "minecraft:overworld", new BlockPos(0, 64, 0), 8));

        assertEquals(4, result.snapshot().skippedChunks());
        assertEquals(0, result.snapshot().columns().size());
        assertEquals(0, source.blockReads);
        assertEquals(0, source.blockWrites);
    }

    @Test void maximumRadiusSamplesAtMostNineThousandTwoHundredSixteenColumns() {
        Fixture source = new Fixture(true, -64, 320);

        WorldSurveySnapshot snapshot = WorldSurveyService.scan(source,
                new WorldSurveyService.SurveyRequest(
                        "minecraft:overworld", new BlockPos(0, 64, 0), 96)).snapshot();

        assertEquals(9_216, snapshot.columns().size());
        assertEquals(9_216, source.blockReads);
    }

    @Test void sameFixtureProducesDeterministicSnapshot() {
        Fixture first = new Fixture(true, -64, 320);
        Fixture second = new Fixture(true, -64, 320);
        var request = new WorldSurveyService.SurveyRequest(
                "minecraft:overworld", new BlockPos(15, 70, -9), 12);

        assertEquals(WorldSurveyService.scan(first, request), WorldSurveyService.scan(second, request));
    }

    private static final class Fixture implements WorldSurveySource {
        private final boolean loaded;
        private final int minY;
        private final int maxY;
        private int blockReads;
        private int blockWrites;

        private Fixture(boolean loaded, int minY, int maxY) {
            this.loaded = loaded;
            this.minY = minY;
            this.maxY = maxY;
        }

        @Override public int minBuildHeight() { return minY; }
        @Override public int maxBuildHeight() { return maxY; }
        @Override public boolean isChunkLoaded(int chunkX, int chunkZ) { return loaded; }
        @Override public WorldSurveySnapshot.TerrainColumn sampleColumn(int x, int z) {
            blockReads++;
            int height = 60 + Math.floorMod(x * 31 + z * 17, 9);
            return new WorldSurveySnapshot.TerrainColumn(x, z, height,
                    Math.floorMod(x + z, 11) == 0, Math.floorMod(x - z, 13) == 0);
        }
        @Override public List<WorldSurveySnapshot.BuildingOutline> buildingOutlines(
                WorldSurveyService.SurveyRequest request) { return List.of(); }
        @Override public List<WorldSurveySnapshot.Plot> plots(
                WorldSurveyService.SurveyRequest request) { return List.of(); }
        @Override public List<WorldSurveySnapshot.EnterpriseCore> enterpriseCores(
                WorldSurveyService.SurveyRequest request) { return List.of(); }
    }
}
