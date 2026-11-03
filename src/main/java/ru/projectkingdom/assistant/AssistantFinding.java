package ru.projectkingdom.assistant;

import java.util.Objects;

/** One explainable, read-only observation produced by an assistant audit. */
public record AssistantFinding(
        Category category,
        Severity severity,
        String title,
        String reason,
        String recommendation,
        String verification
) {
    public enum Category {
        SETTLEMENT,
        HOUSING,
        FOOD_AND_LODGING,
        WORKFORCE,
        LOGISTICS,
        ROADS,
        CONSTRUCTION,
        WAREHOUSE,
        PROFESSION,
        FUNCTIONALITY,
        MODPACK
    }

    public enum Severity { INFO, WARNING, CRITICAL }

    public AssistantFinding {
        category = Objects.requireNonNull(category, "category");
        severity = Objects.requireNonNull(severity, "severity");
        title = requireText(title, "title");
        reason = requireText(reason, "reason");
        recommendation = requireText(recommendation, "recommendation");
        verification = requireText(verification, "verification");
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty() || normalized.length() > 512)
            throw new IllegalArgumentException(field + " must contain 1..512 characters");
        return normalized;
    }
}
