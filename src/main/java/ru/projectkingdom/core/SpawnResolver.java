package ru.projectkingdom.core;

import java.util.Objects;
import java.util.Optional;

/** Deterministic spawn policy. First entry prefers Avalon, but never blocks world admission. */
public final class SpawnResolver {
    public record SpawnPoint(String dimension, int x, int y, int z, String source) {
        public SpawnPoint {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(source, "source");
        }
    }

    private SpawnResolver() {}

    public static SpawnPoint resolve(boolean firstJoin, Optional<SpawnPoint> avalon,
                                     Optional<SpawnPoint> characterHome, SpawnPoint worldSpawn) {
        Objects.requireNonNull(avalon, "avalon");
        Objects.requireNonNull(characterHome, "characterHome");
        Objects.requireNonNull(worldSpawn, "worldSpawn");
        if (firstJoin && avalon.isPresent()) return avalon.get();
        return characterHome.orElse(worldSpawn);
    }
}
