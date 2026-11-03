package ru.projectkingdom.assistant;

import java.util.Objects;

public record ModRecommendation(
        String modId,
        String displayName,
        Recommendation recommendation,
        String reason,
        String benefit,
        String risk,
        int priority,
        String verification
) {
    public enum Recommendation { KEEP, REMOVE, REPLACE, ADD, REVIEW }

    public ModRecommendation {
        modId = text(modId, "modId");
        displayName = text(displayName, "displayName");
        recommendation = Objects.requireNonNull(recommendation, "recommendation");
        reason = text(reason, "reason");
        benefit = text(benefit, "benefit");
        risk = text(risk, "risk");
        verification = text(verification, "verification");
        if (priority < 0 || priority > 100) throw new IllegalArgumentException("priority");
    }

    private static String text(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty() || normalized.length() > 1_024)
            throw new IllegalArgumentException(field);
        return normalized;
    }
}
