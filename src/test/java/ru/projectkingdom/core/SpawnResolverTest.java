package ru.projectkingdom.core;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpawnResolverTest {
    private static final SpawnResolver.SpawnPoint WORLD = new SpawnResolver.SpawnPoint("minecraft:overworld", 0, 64, 0, "world");

    @Test void firstJoinPrefersAvalon() {
        var avalon = new SpawnResolver.SpawnPoint("minecraft:overworld", 100, 80, 100, "avalon");
        assertEquals(avalon, SpawnResolver.resolve(true, Optional.of(avalon), Optional.empty(), WORLD));
    }

    @Test void missingAvalonFallsBackWithoutBlockingAdmission() {
        assertEquals(WORLD, SpawnResolver.resolve(true, Optional.empty(), Optional.empty(), WORLD));
    }

    @Test void returningCharacterUsesHomeBeforeWorldSpawn() {
        var home = new SpawnResolver.SpawnPoint("minecraft:overworld", 20, 70, -5, "home");
        assertEquals(home, SpawnResolver.resolve(false, Optional.empty(), Optional.of(home), WORLD));
    }
}
