package ru.projectkingdom.core;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Ordered, fail-closed data migration gate executed before admitting a player. */
public final class MigrationGate {
    @FunctionalInterface public interface Step { void apply() throws Exception; }
    public record Result(boolean admitted, int version, String failure) {}

    private final int targetVersion;
    private final TreeMap<Integer, Step> steps = new TreeMap<>();

    public MigrationGate(int targetVersion) {
        if (targetVersion < 0) throw new IllegalArgumentException("targetVersion");
        this.targetVersion = targetVersion;
    }

    public MigrationGate step(int destinationVersion, Step step) {
        if (destinationVersion <= 0 || destinationVersion > targetVersion) throw new IllegalArgumentException("version");
        if (steps.putIfAbsent(destinationVersion, Objects.requireNonNull(step, "step")) != null)
            throw new IllegalStateException("duplicate migration");
        return this;
    }

    public Result migrate(int storedVersion) {
        if (storedVersion < 0 || storedVersion > targetVersion)
            return new Result(false, storedVersion, "unsupported_version");
        int version = storedVersion;
        try {
            for (Map.Entry<Integer, Step> entry : steps.tailMap(storedVersion + 1).entrySet()) {
                if (entry.getKey() != version + 1) return new Result(false, version, "missing_migration_" + (version + 1));
                entry.getValue().apply();
                version = entry.getKey();
            }
            if (version != targetVersion) return new Result(false, version, "missing_migration_" + (version + 1));
            return new Result(true, version, "");
        } catch (Exception failure) {
            return new Result(false, version, failure.getClass().getSimpleName());
        }
    }
}
