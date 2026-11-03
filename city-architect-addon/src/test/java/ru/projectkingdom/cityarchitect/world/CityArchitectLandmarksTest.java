package ru.projectkingdom.cityarchitect.world;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CityArchitectLandmarksTest {
    @Test
    void resolvesUniqueRussianTownHallAndSmithyNames() {
        CityArchitectLandmarks landmarks = landmarks(List.of(
                landmark(CityArchitectLandmarks.LandmarkType.TOWN_HALL, "Ратуша Авалона", "minecraft:overworld", 0),
                landmark(CityArchitectLandmarks.LandmarkType.SMITHY, "Кузница Брана", "minecraft:overworld", 20)));

        var hall = landmarks.resolve("ратуши", "minecraft:overworld");
        var smithy = landmarks.resolve("кузницы", "minecraft:overworld");

        assertEquals(CityArchitectLandmarks.Status.FOUND, hall.status());
        assertEquals(CityArchitectLandmarks.LandmarkType.TOWN_HALL, hall.unique().orElseThrow().type());
        assertEquals(CityArchitectLandmarks.Status.FOUND, smithy.status());
        assertEquals(new BlockPos(20, 64, 0), smithy.unique().orElseThrow().center());
    }

    @Test
    void reportsMissingAmbiguousAndWrongDimensionWithoutGuessing() {
        CityArchitectLandmarks landmarks = landmarks(List.of(
                landmark(CityArchitectLandmarks.LandmarkType.TOWN_HALL, "Ратуша", "minecraft:overworld", 0),
                landmark(CityArchitectLandmarks.LandmarkType.SMITHY, "Кузница северная", "minecraft:overworld", 20),
                landmark(CityArchitectLandmarks.LandmarkType.SMITHY, "Кузница южная", "minecraft:overworld", 30),
                landmark(CityArchitectLandmarks.LandmarkType.TAVERN, "Таверна", "minecraft:the_nether", 40)));

        assertEquals(CityArchitectLandmarks.Status.MISSING,
                landmarks.resolve("банк", "minecraft:overworld").status());
        assertEquals(CityArchitectLandmarks.Status.AMBIGUOUS,
                landmarks.resolve("кузницы", "minecraft:overworld").status());
        assertEquals(CityArchitectLandmarks.Status.MISSING,
                landmarks.resolve("таверны", "minecraft:overworld").status());
    }

    private static CityArchitectLandmarks landmarks(List<CityArchitectLandmarks.Landmark> values) {
        return new CityArchitectLandmarks(() -> values);
    }

    private static CityArchitectLandmarks.Landmark landmark(
            CityArchitectLandmarks.LandmarkType type, String name, String dimension, int x
    ) {
        return new CityArchitectLandmarks.Landmark(
                UUID.randomUUID(), type, name, dimension, new BlockPos(x, 64, 0));
    }
}
