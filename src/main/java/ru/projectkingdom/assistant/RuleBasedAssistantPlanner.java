package ru.projectkingdom.assistant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Deterministic offline planner used by the free JAR and as the hybrid fallback. */
public final class RuleBasedAssistantPlanner implements AssistantPlanner {
    @Override public CompletableFuture<PlanningResult> plan(PlanningRequest request) {
        Objects.requireNonNull(request, "request");
        List<PlannedAction> actions = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        if (request.survey() == null) {
            actions.add(new PlannedAction(ActionType.REQUEST_SURVEY, "loaded-only radius 24"));
            notes.add("Для плана нужен ограниченный обзор уже загруженной местности.");
        } else {
            notes.add("Обзор: радиус " + request.survey().radius()
                    + ", колонок " + request.survey().columns().size() + ".");
        }

        List<AssistantFinding> findings = new ArrayList<>();
        findings.addAll(request.settlementFindings());
        findings.addAll(request.diagnosticFindings());
        findings.sort(Comparator.comparing((AssistantFinding value) -> value.severity().ordinal())
                .reversed().thenComparing(value -> value.category().name())
                .thenComparing(AssistantFinding::title));
        if (!findings.isEmpty()) {
            long serious = findings.stream().filter(value ->
                    value.severity() != AssistantFinding.Severity.INFO).count();
            actions.add(new PlannedAction(ActionType.REPORT,
                    "findings=" + findings.size() + ", warnings=" + serious));
            notes.add("Проверки: " + findings.size() + ", требуют внимания: " + serious + ".");
            if (serious > 0 && request.diagnosticFindings().isEmpty()) {
                actions.add(new PlannedAction(ActionType.REQUEST_DIAGNOSTICS,
                        "construction,warehouse,professions"));
            }
        }

        if (!request.modRecommendations().isEmpty()) {
            List<ModRecommendation> mods = request.modRecommendations().stream()
                    .sorted(Comparator.comparingInt(ModRecommendation::priority).reversed()
                            .thenComparing(ModRecommendation::modId)).toList();
            actions.add(new PlannedAction(ActionType.REPORT,
                    "mod-recommendations=" + mods.size() + ", first=" + mods.get(0).modId()));
            notes.add("Рекомендации по модпаку: " + mods.size() + ".");
        }

        String prompt = request.prompt().toLowerCase(Locale.ROOT);
        if (prompt.contains("схем") || prompt.contains("проект") || prompt.contains("preview")) {
            actions.add(new PlannedAction(ActionType.SUGGEST_SCHEMATIC, "preview-required"));
            notes.add("Строительство возможно только после предпросмотра и подтверждения.");
        }
        if (actions.isEmpty()) {
            actions.add(new PlannedAction(ActionType.REPORT, "no-blocking-findings"));
            notes.add("Блокирующих проблем не найдено.");
        }
        return CompletableFuture.completedFuture(new PlanningResult(
                request.requestId(), actions, String.join(" ", notes), false));
    }
}
