package ru.projectkingdom.assistant.checks;

import ru.projectkingdom.assistant.AssistantDiagnosticCheck;
import ru.projectkingdom.assistant.AssistantDiagnosticContext;
import ru.projectkingdom.assistant.AssistantFinding;
import ru.projectkingdom.npc.KingdomConstructionData;
import ru.projectkingdom.npc.KingdomConstructionDiagnostics;
import ru.projectkingdom.npc.KingdomConstructionPlans;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Missing plans, markers, materials and stalled construction progress. */
public final class ConstructionDiagnosticCheck implements AssistantDiagnosticCheck {
    @Override public String id() { return "construction"; }
    @Override public String titleRu() { return "Строительство"; }

    @Override public List<AssistantFinding> run(AssistantDiagnosticContext context) {
        if (context.server() == null) return List.of();
        var projects = new ArrayList<>(KingdomConstructionData.get(context.server()).all());
        projects.sort(Comparator.comparing(project -> project.id().toString()));
        List<AssistantFinding> findings = new ArrayList<>();
        for (var project : projects) {
            if (findings.size() >= 64 || project.status() == KingdomConstructionData.Status.CANCELLED
                    || project.status() == KingdomConstructionData.Status.COMPLETED) continue;
            if (KingdomConstructionPlans.resolve(context.server(), project.design()) == null) {
                findings.add(finding(AssistantFinding.Severity.CRITICAL,
                        "Недоступна схема стройки",
                        "Проект " + project.id() + " ссылается на недоступную схему " + project.design() + ".",
                        "Верните закреплённый файл схемы или отмените повреждённый проект.",
                        "Диагностика должна снова прочитать схему проекта."));
                continue;
            }
            String step = KingdomConstructionDiagnostics.nextStep(context.server(), project);
            String materials = KingdomConstructionDiagnostics.missingMaterials(context.server(), project);
            if (!step.contains("выполняет заказ") && !step.contains("Постройка завершена"))
                findings.add(finding(AssistantFinding.Severity.WARNING,
                        "Стройка остановлена", step,
                        "Выполните указанное условие для проекта " + project.id() + ".",
                        "Повторная проверка должна показать активного строителя или завершение."));
            if (materials.startsWith("Недостаёт") && findings.size() < 64)
                findings.add(finding(AssistantFinding.Severity.WARNING,
                        "Не хватает материалов", materials,
                        "Доставьте реальные блоки в соседний склад стройплощадки.",
                        "Раздел «Материалы» должен показать нулевой дефицит."));
        }
        return List.copyOf(findings);
    }

    private static AssistantFinding finding(AssistantFinding.Severity severity, String title,
                                            String reason, String recommendation,
                                            String verification) {
        return new AssistantFinding(AssistantFinding.Category.CONSTRUCTION, severity,
                title, reason, recommendation, verification);
    }
}
