package ru.projectkingdom.assistant;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class FunctionalAuditServiceTest {
    @Test void reportsMissingEnterpriseWarehouseLinkWithEvidence() {
        FeatureInventory inventory = inventory(
                Map.of("enterprise", feature("enterprise", true, "Ядро предприятия зарегистрировано"),
                        "warehouse", feature("warehouse", true, "Комната склада зарегистрирована")),
                List.of(), Map.of(), Map.of());

        var findings = FunctionalAuditService.audit(emptyReport(), List.of(), inventory);

        AssistantFinding finding = findings.stream().filter(f -> f.title().contains("связи"))
                .findFirst().orElseThrow();
        assertTrue(finding.reason().contains("Ядро предприятия"));
        assertTrue(finding.reason().contains("Комната склада"));
    }

    @Test void reportsDuplicateAccessPathsUnimplementedFeatureAndExcessiveSteps() {
        FeatureInventory inventory = inventory(
                Map.of("rentals", feature("rentals", false, "В меню есть кнопка без обработчика")),
                List.of(), Map.of("warehouse", List.of("Книга → Склад", "Ядро → Склад")),
                Map.of("profession-setup", 7));

        var findings = FunctionalAuditService.audit(emptyReport(), List.of(), inventory);

        assertTrue(findings.stream().anyMatch(f -> f.title().contains("Дублиру")));
        assertTrue(findings.stream().anyMatch(f -> f.title().contains("не реализована")
                && f.reason().contains("без обработчика")));
        assertTrue(findings.stream().anyMatch(f -> f.title().contains("ручных действий")
                && f.reason().contains("7")));
    }

    @Test void completeChainProducesNoSpeculativeFinding() {
        FeatureInventory inventory = inventory(
                Map.of("enterprise", feature("enterprise", true, "ok"),
                        "warehouse", feature("warehouse", true, "ok")),
                List.of(new FeatureInventory.Link("enterprise", "warehouse", true,
                        "Привязка контейнера проверена")),
                Map.of("warehouse", List.of("Книга → Склад")),
                Map.of("profession-setup", 3));

        assertEquals(List.of(), FunctionalAuditService.audit(emptyReport(), List.of(), inventory));
    }

    private static FeatureInventory inventory(
            Map<String, FeatureInventory.Feature> features,
            List<FeatureInventory.Link> links,
            Map<String, List<String>> paths,
            Map<String, Integer> steps) {
        return new FeatureInventory(features, links, paths, steps, Set.of());
    }

    private static FeatureInventory.Feature feature(String id, boolean implemented, String evidence) {
        return new FeatureInventory.Feature(id, implemented, evidence);
    }

    private static AssistantDiagnosticReport emptyReport() {
        return new AssistantDiagnosticReport(List.of(), List.of(), List.of(), false);
    }
}
