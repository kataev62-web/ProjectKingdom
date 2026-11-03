package ru.projectkingdom.assistant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Bounded evidence inventory used by functional and modpack audits. */
public record FeatureInventory(
        Map<String, Feature> features,
        List<Link> links,
        Map<String, List<String>> accessPaths,
        Map<String, Integer> manualSteps,
        Set<String> requiredCapabilities
) {
    public record Feature(String id, boolean implemented, String evidence) {
        public Feature {
            id = text(id, "id");
            evidence = text(evidence, "evidence");
        }
    }

    public record Link(String from, String to, boolean present, String evidence) {
        public Link {
            from = text(from, "from");
            to = text(to, "to");
            evidence = text(evidence, "evidence");
        }
    }

    public FeatureInventory {
        features = Map.copyOf(features == null ? Map.of() : features);
        links = List.copyOf(links == null ? List.of() : links);
        Map<String, List<String>> paths = new LinkedHashMap<>();
        if (accessPaths != null) accessPaths.forEach((key, value) ->
                paths.put(text(key, "path id"), List.copyOf(value)));
        accessPaths = Map.copyOf(paths);
        manualSteps = Map.copyOf(manualSteps == null ? Map.of() : manualSteps);
        requiredCapabilities = Set.copyOf(
                requiredCapabilities == null ? Set.of() : requiredCapabilities);
        if (features.size() > 256 || links.size() > 512 || accessPaths.size() > 256
                || manualSteps.size() > 256 || requiredCapabilities.size() > 64)
            throw new IllegalArgumentException("feature inventory is too large");
    }

    public boolean linked(String from, String to) {
        return links.stream().anyMatch(link -> link.present()
                && link.from().equals(from) && link.to().equals(to));
    }

    private static String text(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty() || normalized.length() > 512)
            throw new IllegalArgumentException(field);
        return normalized;
    }
}
