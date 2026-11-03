package ru.projectkingdom.assistant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Evidence-only analysis of links and player paths in Project Kingdom. */
public final class FunctionalAuditService {
    private FunctionalAuditService() {}

    public static List<AssistantFinding> audit(
            AssistantDiagnosticReport diagnostics,
            List<AssistantFinding> settlementAudit,
            FeatureInventory inventory) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(settlementAudit, "settlementAudit");
        Objects.requireNonNull(inventory, "inventory");
        List<AssistantFinding> findings = new ArrayList<>();

        var enterprise = inventory.features().get("enterprise");
        var warehouse = inventory.features().get("warehouse");
        if (enterprise != null && enterprise.implemented() && warehouse != null
                && warehouse.implemented() && !inventory.linked("enterprise", "warehouse")) {
            findings.add(finding("Нет связи предприятия со складом",
                    "Обе функции существуют, но связь не подтверждена. Доказательства: «"
                            + enterprise.evidence() + "»; «" + warehouse.evidence() + "».",
                    "Добавьте проверяемую привязку предприятия к зарегистрированному помещению склада.",
                    "Инвентарь должен содержать подтверждённую связь enterprise → warehouse."));
        }

        inventory.features().values().stream().sorted(Comparator.comparing(FeatureInventory.Feature::id))
                .filter(feature -> !feature.implemented()).forEach(feature -> findings.add(finding(
                        "Функция не реализована: " + feature.id(),
                        "Заявленная функция не завершена. Доказательство: " + feature.evidence() + ".",
                        "Завершите обработчик либо удалите недействующий пункт интерфейса.",
                        "Повторная инвентаризация должна подтвердить реализацию функции.")));

        inventory.accessPaths().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .filter(entry -> entry.getValue().size() > 1).forEach(entry -> findings.add(finding(
                        "Дублирующиеся пути доступа: " + entry.getKey(),
                        "Одна функция открывается несколькими путями: "
                                + String.join("; ", entry.getValue()) + ".",
                        "Оставьте один основной путь, а остальные сделайте понятными ссылками на него.",
                        "Инвентарь должен содержать один основной путь доступа.")));

        inventory.manualSteps().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .filter(entry -> entry.getValue() != null && entry.getValue() > 4)
                .forEach(entry -> findings.add(finding(
                        "Слишком много ручных действий: " + entry.getKey(),
                        "Зафиксировано " + entry.getValue() + " обязательных шагов в одном сценарии.",
                        "Объедините безопасные настройки в мастер или быструю настройку.",
                        "Повторный путь должен занимать не более четырёх обязательных шагов.")));

        for (String failed : diagnostics.failedIds()) findings.add(finding(
                "Диагностический пробел: " + failed,
                "Адаптер завершился ошибкой; функциональная цепочка не может считаться проверенной.",
                "Исправьте адаптер и повторите аудит.",
                "В отчёте не должно быть failedIds для этого адаптера."));
        return List.copyOf(findings);
    }

    private static AssistantFinding finding(
            String title, String reason, String recommendation, String verification) {
        return new AssistantFinding(AssistantFinding.Category.FUNCTIONALITY,
                AssistantFinding.Severity.WARNING, title, reason, recommendation, verification);
    }
}
