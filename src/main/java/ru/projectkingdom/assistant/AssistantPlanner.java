package ru.projectkingdom.assistant;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Produces advisory actions; callers remain responsible for authorization and confirmation. */
public interface AssistantPlanner {
    enum ActionType { REPORT, SUGGEST_SCHEMATIC, REQUEST_SURVEY, REQUEST_DIAGNOSTICS }

    record PlannedAction(ActionType type, String argument) {
        public PlannedAction {
            type = Objects.requireNonNull(type, "type");
            argument = requireText(argument, "argument", 4_096);
        }
    }

    record PlanningRequest(
            UUID requestId,
            UUID ownerId,
            String prompt,
            WorldSurveySnapshot survey,
            List<AssistantFinding> settlementFindings,
            List<AssistantFinding> diagnosticFindings,
            List<ModRecommendation> modRecommendations
    ) {
        public PlanningRequest {
            requestId = Objects.requireNonNull(requestId, "requestId");
            ownerId = Objects.requireNonNull(ownerId, "ownerId");
            prompt = requireText(prompt, "prompt", 4_096);
            settlementFindings = List.copyOf(settlementFindings);
            diagnosticFindings = List.copyOf(diagnosticFindings);
            modRecommendations = List.copyOf(modRecommendations);
        }
    }

    record PlanningResult(
            UUID requestId,
            List<PlannedAction> actions,
            String text,
            boolean modelUsed
    ) {
        public PlanningResult {
            requestId = Objects.requireNonNull(requestId, "requestId");
            actions = List.copyOf(actions);
            text = requireText(text, "text", 64_000);
        }
    }

    CompletableFuture<PlanningResult> plan(PlanningRequest request);

    private static String requireText(String value, String field, int maximum) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty() || normalized.length() > maximum)
            throw new IllegalArgumentException(field);
        return normalized;
    }
}
