package ru.projectkingdom.assistant;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Optional model planner whose entire output passes through the advisory validator. */
public final class HybridAssistantPlanner implements AssistantPlanner {
    private final AssistantModelClient modelClient;
    private final AssistantPlanValidator validator;
    private final AssistantPlanner fallback;

    public HybridAssistantPlanner(
            AssistantModelClient modelClient,
            AssistantPlanValidator validator,
            AssistantPlanner fallback) {
        this.modelClient = Objects.requireNonNull(modelClient, "modelClient");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    @Override public CompletableFuture<PlanningResult> plan(PlanningRequest request) {
        Objects.requireNonNull(request, "request");
        if (!modelClient.enabled()) return fallback.plan(request);

        CompletableFuture<AssistantModelClient.ModelResponse> proposed;
        try {
            proposed = modelClient.propose(new AssistantModelClient.ModelRequest(
                    request.requestId(), compactContext(request)));
            if (proposed == null) return fallback.plan(request);
        } catch (RuntimeException failure) {
            return fallback.plan(request);
        }

        return proposed.handle((response, failure) -> {
            if (failure != null) return null;
            AssistantPlanValidator.ValidationResult checked = validator.validate(response,
                    new AssistantPlanValidator.PlanningLimits(request.requestId(), 20, 64_000));
            if (!checked.accepted()) return null;
            return new PlanningResult(request.requestId(), checked.actions(), checked.text(), true);
        }).thenCompose(result -> result == null
                ? fallback.plan(request)
                : CompletableFuture.completedFuture(result));
    }

    private static String compactContext(PlanningRequest request) {
        String prompt = request.prompt();
        if (prompt.length() > 2_048) prompt = prompt.substring(0, 2_048);
        String survey = request.survey() == null ? "missing" :
                "radius=" + request.survey().radius()
                        + ",columns=" + request.survey().columns().size()
                        + ",buildings=" + request.survey().buildingOutlines().size();
        return "prompt=" + prompt
                + "\nsurvey=" + survey
                + "\nsettlement_findings=" + request.settlementFindings().size()
                + "\ndiagnostic_findings=" + request.diagnosticFindings().size()
                + "\nmod_recommendations=" + request.modRecommendations().size();
    }
}
