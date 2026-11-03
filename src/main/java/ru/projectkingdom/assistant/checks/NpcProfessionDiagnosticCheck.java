package ru.projectkingdom.assistant.checks;

import net.minecraft.server.level.ServerLevel;
import ru.projectkingdom.assistant.AssistantDiagnosticCheck;
import ru.projectkingdom.assistant.AssistantDiagnosticContext;
import ru.projectkingdom.assistant.AssistantFinding;
import ru.projectkingdom.npc.KingdomNpcData;
import ru.projectkingdom.npc.KingdomNpcWorkday;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Live entity, contract, workplace, debt pause and order consistency. */
public final class NpcProfessionDiagnosticCheck implements AssistantDiagnosticCheck {
    @Override public String id() { return "npc-professions"; }
    @Override public String titleRu() { return "Профессии NPC"; }

    @Override public List<AssistantFinding> run(AssistantDiagnosticContext context) {
        if (context.server() == null) return List.of();
        var workers = new ArrayList<>(KingdomNpcData.get(context.server()).all());
        workers.sort(Comparator.comparing(worker -> worker.entityId.toString()));
        List<AssistantFinding> findings = new ArrayList<>();
        for (var worker : workers) {
            if (findings.size() >= 64) break;
            ServerLevel level = level(context, worker.dimension);
            var entity = level == null ? null : level.getEntity(worker.entityId);
            if (entity == null || !entity.isAlive()) {
                findings.add(finding(AssistantFinding.Severity.CRITICAL,
                        "NPC отсутствует в мире", worker.name + " зарегистрирован, но живая сущность не найдена.",
                        "Проверьте смерть, выгрузку и корректность UUID жителя.",
                        "Повторная проверка должна найти живую сущность " + worker.entityId + "."));
            }
            if (worker.contractPaused) {
                findings.add(finding(AssistantFinding.Severity.WARNING,
                        "Работа остановлена из-за долга",
                        worker.name + " не работает: достигнут предел задолженности по зарплате.",
                        "Погасите долг реальными монетами через кассу предприятия.",
                        "contractPaused должен сняться только после полного погашения."));
                continue;
            }
            if (worker.employer != null && !KingdomNpcWorkday.employed(context.server(), worker)) {
                findings.add(finding(AssistantFinding.Severity.WARNING,
                        "Нет действующего договора", worker.name + " имеет работодателя, но договор неактивен.",
                        "Проверьте предприятие, договор и рабочее место.",
                        "KingdomNpcWorkday.employed должен вернуть true."));
            } else if (worker.employer != null && level != null
                    && !KingdomNpcWorkday.hasAssignment(context.server(), level, worker)) {
                findings.add(finding(AssistantFinding.Severity.WARNING,
                        "Профессия не настроена", worker.name
                                + " не имеет полного источника, назначения или рабочего блока.",
                        "Выполните быструю настройку профессии и проверьте привязки здания.",
                        "KingdomNpcWorkday.hasAssignment должен вернуть true."));
            }
            if (worker.employer != null && (worker.order == null || worker.order.isBlank()
                    || "wander".equals(worker.order))) {
                findings.add(finding(AssistantFinding.Severity.INFO,
                        "Нет активного рабочего приказа", worker.name + " сейчас имеет приказ «"
                                + (worker.order == null ? "нет" : worker.order) + "».",
                        "Назначьте безопасный приказ через меню профессии.",
                        "В карточке NPC должен отображаться активный рабочий приказ."));
            }
        }
        return List.copyOf(findings);
    }

    private static ServerLevel level(AssistantDiagnosticContext context, String dimension) {
        for (ServerLevel level : context.server().getAllLevels())
            if (dimension.equals(level.dimension().location().toString())) return level;
        return null;
    }

    private static AssistantFinding finding(AssistantFinding.Severity severity, String title,
                                            String reason, String recommendation,
                                            String verification) {
        return new AssistantFinding(AssistantFinding.Category.PROFESSION, severity,
                title, reason, recommendation, verification);
    }
}
