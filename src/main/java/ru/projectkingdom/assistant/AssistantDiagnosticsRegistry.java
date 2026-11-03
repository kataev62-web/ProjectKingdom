package ru.projectkingdom.assistant;

import ru.projectkingdom.assistant.checks.ConstructionDiagnosticCheck;
import ru.projectkingdom.assistant.checks.NpcProfessionDiagnosticCheck;
import ru.projectkingdom.assistant.checks.WarehouseDiagnosticCheck;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Failure-isolated deterministic registry for all assistant diagnostics. */
public final class AssistantDiagnosticsRegistry {
    public static final int MAX_FINDINGS = 200;
    private final List<AssistantDiagnosticCheck> checks;

    public AssistantDiagnosticsRegistry(List<AssistantDiagnosticCheck> checks) {
        Objects.requireNonNull(checks, "checks");
        List<AssistantDiagnosticCheck> sorted = new ArrayList<>(checks);
        sorted.sort(Comparator.comparing(AssistantDiagnosticCheck::id));
        Set<String> ids = new HashSet<>();
        for (AssistantDiagnosticCheck check : sorted) {
            if (check == null || check.id() == null || check.id().isBlank()
                    || check.titleRu() == null || check.titleRu().isBlank()
                    || !ids.add(check.id())) throw new IllegalArgumentException("invalid diagnostic check");
        }
        this.checks = List.copyOf(sorted);
    }

    public static AssistantDiagnosticsRegistry standard() {
        return new AssistantDiagnosticsRegistry(List.of(
                new ConstructionDiagnosticCheck(),
                new WarehouseDiagnosticCheck(),
                new NpcProfessionDiagnosticCheck()));
    }

    public AssistantDiagnosticReport run(
            Set<String> requestedIds, AssistantDiagnosticContext context) {
        Objects.requireNonNull(context, "context");
        Set<String> requested = requestedIds == null ? Set.of() : Set.copyOf(requestedIds);
        List<String> executed = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        List<AssistantFinding> findings = new ArrayList<>();
        boolean truncated = false;
        for (AssistantDiagnosticCheck check : checks) {
            if (!requested.isEmpty() && !requested.contains(check.id())) continue;
            executed.add(check.id());
            try {
                List<AssistantFinding> produced = Objects.requireNonNull(
                        check.run(context), "diagnostic findings");
                int remaining = MAX_FINDINGS - findings.size();
                for (AssistantFinding finding : produced) {
                    if (findings.size() >= MAX_FINDINGS) { truncated = true; break; }
                    findings.add(Objects.requireNonNull(finding, "finding"));
                }
                if (produced.size() > remaining) truncated = true;
            } catch (RuntimeException failure) {
                failed.add(check.id());
                if (findings.size() < MAX_FINDINGS) {
                    findings.add(new AssistantFinding(AssistantFinding.Category.FUNCTIONALITY,
                            AssistantFinding.Severity.CRITICAL,
                            "Сбой проверки: " + check.titleRu(),
                            failure.getClass().getSimpleName() + ": "
                                    + (failure.getMessage() == null ? "без описания" : failure.getMessage()),
                            "Проверьте журнал сервера и запустите адаптер отдельно.",
                            "Повторный запуск проверки «" + check.id() + "» должен завершиться без исключения."));
                } else truncated = true;
            }
        }
        return new AssistantDiagnosticReport(executed, failed, findings, truncated);
    }
}
