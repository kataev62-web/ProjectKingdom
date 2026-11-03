package ru.projectkingdom.assistant;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Size-bounded metadata only; no mod files or classes are exposed to the audit. */
public record InstalledMod(
        String modId,
        String displayName,
        String description,
        List<String> dependencies,
        Set<String> capabilities
) {
    public InstalledMod {
        modId = bounded(modId, "modId", 128);
        displayName = bounded(displayName, "displayName", 128);
        description = bounded(description == null ? "" : description, "description", 2_048);
        dependencies = List.copyOf(dependencies == null ? List.of() : dependencies).stream()
                .limit(64).map(value -> bounded(value, "dependency", 128)).toList();
        capabilities = Set.copyOf(capabilities == null ? Set.of() : capabilities);
    }

    private static String bounded(String value, String field, int max) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty() && !field.equals("description") || normalized.length() > max)
            throw new IllegalArgumentException(field);
        return normalized;
    }
}
