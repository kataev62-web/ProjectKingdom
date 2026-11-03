package ru.projectkingdom.assistant;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class AssistantDiagnosticsRegistryTest {
    @Test void checksRunInDeterministicIdOrder() {
        List<String> calls = new ArrayList<>();
        var registry = new AssistantDiagnosticsRegistry(List.of(
                check("z-last", calls), check("a-first", calls), check("m-middle", calls)));

        AssistantDiagnosticReport report = registry.run(Set.of(), context());

        assertEquals(List.of("a-first", "m-middle", "z-last"), calls);
        assertEquals(List.of("a-first", "m-middle", "z-last"), report.executedIds());
    }

    @Test void failedAdapterDoesNotAbortRemainingChecks() {
        List<String> calls = new ArrayList<>();
        AssistantDiagnosticCheck broken = new AssistantDiagnosticCheck() {
            @Override public String id() { return "b-broken"; }
            @Override public String titleRu() { return "Сломанная"; }
            @Override public List<AssistantFinding> run(AssistantDiagnosticContext context) {
                calls.add(id());
                throw new IllegalStateException("boom");
            }
        };
        var registry = new AssistantDiagnosticsRegistry(List.of(
                check("a-before", calls), broken, check("c-after", calls)));

        AssistantDiagnosticReport report = registry.run(Set.of(), context());

        assertEquals(List.of("a-before", "b-broken", "c-after"), calls);
        assertEquals(List.of("b-broken"), report.failedIds());
        assertTrue(report.findings().stream().anyMatch(f -> f.title().contains("Сломанная")));
    }

    @Test void outputIsBoundedToTwoHundredFindings() {
        AssistantDiagnosticCheck noisy = new AssistantDiagnosticCheck() {
            @Override public String id() { return "noisy"; }
            @Override public String titleRu() { return "Шумная"; }
            @Override public List<AssistantFinding> run(AssistantDiagnosticContext context) {
                List<AssistantFinding> findings = new ArrayList<>();
                for (int index = 0; index < 250; index++) findings.add(finding("f-" + index));
                return findings;
            }
        };

        AssistantDiagnosticReport report = new AssistantDiagnosticsRegistry(List.of(noisy))
                .run(Set.of("noisy"), context());

        assertEquals(200, report.findings().size());
        assertTrue(report.truncated());
    }

    @Test void contextSnapshotsCannotBeMutatedByChecks() {
        Map<String, String> supplied = new LinkedHashMap<>();
        supplied.put("construction", "before");
        AssistantDiagnosticContext context = AssistantDiagnosticContext.forTest(supplied);
        AssistantDiagnosticCheck mutating = new AssistantDiagnosticCheck() {
            @Override public String id() { return "mutating"; }
            @Override public String titleRu() { return "Изменяющая"; }
            @Override public List<AssistantFinding> run(AssistantDiagnosticContext value) {
                value.snapshots().put("construction", "after");
                return List.of();
            }
        };

        AssistantDiagnosticReport report = new AssistantDiagnosticsRegistry(List.of(mutating))
                .run(Set.of(), context);

        assertEquals(Map.of("construction", "before"), supplied);
        assertEquals(List.of("mutating"), report.failedIds());
    }

    private static AssistantDiagnosticCheck check(String id, List<String> calls) {
        return new AssistantDiagnosticCheck() {
            @Override public String id() { return id; }
            @Override public String titleRu() { return id; }
            @Override public List<AssistantFinding> run(AssistantDiagnosticContext context) {
                calls.add(id);
                return List.of(finding(id));
            }
        };
    }

    private static AssistantFinding finding(String title) {
        return new AssistantFinding(AssistantFinding.Category.FUNCTIONALITY,
                AssistantFinding.Severity.INFO, title, "Причина", "Рекомендация", "Проверка");
    }

    private static AssistantDiagnosticContext context() {
        return AssistantDiagnosticContext.forTest(Map.of("stable", "snapshot"));
    }
}
