package ru.projectkingdom.assistant;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import ru.projectkingdom.npc.KingdomArchitectSessions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server dispatcher for one active assistant task per owner. */
public final class KingdomAssistantTasks {
    public enum SubmitResult {
        ACCEPTED, BUSY, CANCELLED, NO_ACTIVE_TASK, READ_ONLY, SURVEY_REQUIRED, PREVIEW_REJECTED,
        BUILD_REJECTED, UNDO_REJECTED,
        NOT_OWNER, NOT_ADMIN
    }

    private static final class SessionResults {
        private WorldSurveySnapshot survey;
        private List<AssistantFinding> findings = List.of();
        private AssistantDiagnosticReport diagnosticReport;
    }

    /** Session-only results; persistent reporting and memory are added by later plan tasks. */
    private static final Map<MinecraftServer, Map<UUID, SessionResults>> RESULTS =
            new WeakHashMap<>();

    private KingdomAssistantTasks() {}

    public static SubmitResult submit(ServerPlayer owner, AssistantCommand command) {
        Objects.requireNonNull(command, "command");
        if (owner == null || owner.getServer() == null || !owner.hasPermissions(4))
            return SubmitResult.NOT_ADMIN;
        KingdomAssistantData data = KingdomAssistantData.get(owner.getServer());
        if (data.ownedBy(owner.getUUID()).isEmpty()) return SubmitResult.NOT_OWNER;
        if (command.intent() == AssistantIntent.SETTLEMENT_AUDIT
                && lastSurvey(owner.getServer(), owner.getUUID()).isEmpty()) {
            sendResult(owner, command, SubmitResult.SURVEY_REQUIRED, data);
            return SubmitResult.SURVEY_REQUIRED;
        }
        SubmitResult result = transition(data, owner.getUUID(), command, UUID.randomUUID());
        if (result == SubmitResult.ACCEPTED && command.intent() == AssistantIntent.PREVIEW) {
            UUID assistantId = data.ownedBy(owner.getUUID()).orElseThrow().entityId();
            if (!AssistantPreviewService.begin(owner, assistantId,
                    command.argument(), owner.blockPosition())) {
                data.finishTask(owner.getUUID(), "Предпросмотр не открыт");
                result = SubmitResult.PREVIEW_REJECTED;
            }
        } else if (result == SubmitResult.ACCEPTED && command.intent() == AssistantIntent.BUILD) {
            var proposal = AssistantPreviewService.pending(owner.getServer(), owner.getUUID());
            if (proposal.isEmpty()) {
                data.finishTask(owner.getUUID(), "Стройка не начата: нет подтверждённого проекта");
                result = SubmitResult.BUILD_REJECTED;
            } else {
                var item = proposal.get();
                var queued = AssistantCreativeBuildService.forServer(owner.getServer()).queue(
                        item.owner(), item.assistant(), item.dimension(), item.designKey(), item.origin());
                if (queued.status() == AssistantCreativeBuildService.QueueStatus.QUEUED) {
                    AssistantPreviewService.consume(owner.getServer(), item.owner(), item.assistant());
                } else {
                    data.finishTask(owner.getUUID(), "Стройка отклонена: " + queued.status());
                    result = SubmitResult.BUILD_REJECTED;
                }
            }
        } else if (result == SubmitResult.ACCEPTED && command.intent() == AssistantIntent.UNDO) {
            KingdomAssistantBuildData builds = KingdomAssistantBuildData.get(owner.getServer());
            UUID buildId = parseBuildId(command.argument());
            if (buildId == null) buildId = builds.latest(owner.getUUID())
                    .map(KingdomAssistantBuildData.BuildView::id).orElse(null);
            if (buildId == null || builds.build(buildId).isEmpty()) {
                data.finishTask(owner.getUUID(), "Отмена не начата: стройка не найдена");
                result = SubmitResult.UNDO_REJECTED;
            } else {
                AssistantCreativeBuildService.forServer(owner.getServer())
                        .undo(owner.getUUID(), buildId);
            }
        } else if (result == SubmitResult.CANCELLED) {
            KingdomArchitectSessions.cancel(owner.getUUID());
            AssistantCreativeBuildService.forServer(owner.getServer()).cancel(owner.getUUID());
        }
        sendResult(owner, command, result, data);
        return result;
    }

    public static SubmitResult transition(
            KingdomAssistantData data, UUID owner, AssistantCommand command, UUID taskId) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(taskId, "taskId");
        var state = data.ownedBy(owner);
        if (state.isEmpty()) return SubmitResult.NOT_OWNER;
        AssistantIntent intent = command.intent();
        if (intent == AssistantIntent.STATUS) return SubmitResult.READ_ONLY;
        if (intent == AssistantIntent.CANCEL) {
            if (state.get().activeTaskId() == null) return SubmitResult.NO_ACTIVE_TASK;
            return data.finishTask(owner, "Отменено владельцем")
                    ? SubmitResult.CANCELLED : SubmitResult.NO_ACTIVE_TASK;
        }
        if (intent == AssistantIntent.FOLLOW || intent == AssistantIntent.STAY) {
            if (isWorldMutation(state.get().activeIntent())) return SubmitResult.BUSY;
            KingdomAssistantData.Mode mode = intent == AssistantIntent.FOLLOW
                    ? KingdomAssistantData.Mode.FOLLOW : KingdomAssistantData.Mode.STAY;
            data.setMode(owner, mode);
            return SubmitResult.ACCEPTED;
        }
        return data.beginTask(owner, taskId, intent) ? SubmitResult.ACCEPTED : SubmitResult.BUSY;
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        KingdomAssistantData data = KingdomAssistantData.get(server);
        for (KingdomAssistantData.OwnerState state : data.allOwners()) {
            ServerPlayer owner = server.getPlayerList().getPlayer(state.ownerId());
            if (owner == null) continue;
            KingdomAssistantEntity assistant = loadedAssistant(server, state.entityId());
            if (assistant == null || !assistant.isAlive()) continue;
            assistant.setAssistantMode(state.mode());
            assistant.applyMovement(owner);
            if (state.activeIntent() == AssistantIntent.SURVEY) runSurvey(data, owner);
            else if (state.activeIntent() == AssistantIntent.SETTLEMENT_AUDIT)
                runSettlementAudit(data, owner);
            else if (state.activeIntent() == AssistantIntent.DIAGNOSTICS)
                runDiagnostics(data, owner);
        }
    }

    public static Optional<WorldSurveySnapshot> lastSurvey(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) return Optional.empty();
        Map<UUID, SessionResults> serverResults = RESULTS.get(server);
        SessionResults result = serverResults == null ? null : serverResults.get(owner);
        return result == null ? Optional.empty() : Optional.ofNullable(result.survey);
    }

    public static List<AssistantFinding> lastFindings(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) return List.of();
        Map<UUID, SessionResults> serverResults = RESULTS.get(server);
        SessionResults result = serverResults == null ? null : serverResults.get(owner);
        return result == null ? List.of() : result.findings;
    }

    public static Optional<AssistantDiagnosticReport> lastDiagnosticReport(
            MinecraftServer server, UUID owner) {
        if (server == null || owner == null) return Optional.empty();
        Map<UUID, SessionResults> serverResults = RESULTS.get(server);
        SessionResults result = serverResults == null ? null : serverResults.get(owner);
        return result == null ? Optional.empty() : Optional.ofNullable(result.diagnosticReport);
    }

    private static void runSurvey(KingdomAssistantData data, ServerPlayer owner) {
        try {
            String dimension = owner.serverLevel().dimension().location().toString();
            var request = WorldSurveyService.SurveyRequest.around(dimension, owner.blockPosition());
            WorldSurveySnapshot snapshot = WorldSurveyService.scan(
                    WorldSurveySource.forPlayer(owner), request).snapshot();
            SessionResults results = session(owner.getServer(), owner.getUUID());
            results.survey = snapshot;
            results.findings = List.of();
            data.finishTask(owner.getUUID(), "Обследование завершено: "
                    + snapshot.columns().size() + " колонок");
            owner.sendSystemMessage(Component.literal("[Архитектор Авалона] Обследование завершено: "
                    + snapshot.columns().size() + " колонок, пропущено чанков: "
                    + snapshot.skippedChunks() + "."));
        } catch (RuntimeException failure) {
            data.finishTask(owner.getUUID(), "Ошибка обследования");
            owner.sendSystemMessage(Component.literal(
                    "[Архитектор Авалона] Не удалось обследовать область: "
                            + safeMessage(failure) + "."));
        }
    }

    private static void runSettlementAudit(KingdomAssistantData data, ServerPlayer owner) {
        WorldSurveySnapshot snapshot = lastSurvey(owner.getServer(), owner.getUUID()).orElse(null);
        if (snapshot == null) {
            data.finishTask(owner.getUUID(), "Аудит не выполнен: нет обследования");
            owner.sendSystemMessage(Component.literal(
                    "[Архитектор Авалона] Сначала выполните обследование территории."));
            return;
        }
        try {
            List<AssistantFinding> findings = SettlementAuditService.audit(
                    owner.getServer(), owner, snapshot);
            session(owner.getServer(), owner.getUUID()).findings = List.copyOf(findings);
            data.finishTask(owner.getUUID(), "Аудит поселения: " + findings.size() + " замечаний");
            if (findings.isEmpty()) {
                owner.sendSystemMessage(Component.literal(
                        "[Архитектор Авалона] Аудит завершён: замечаний нет."));
                return;
            }
            owner.sendSystemMessage(Component.literal("[Архитектор Авалона] Найдено замечаний: "
                    + findings.size() + ". Показываю первые " + Math.min(5, findings.size()) + ":"));
            for (int index = 0; index < Math.min(5, findings.size()); index++) {
                AssistantFinding finding = findings.get(index);
                owner.sendSystemMessage(Component.literal((index + 1) + ". " + finding.title()
                        + " — " + finding.recommendation()));
            }
        } catch (RuntimeException failure) {
            data.finishTask(owner.getUUID(), "Ошибка аудита поселения");
            owner.sendSystemMessage(Component.literal(
                    "[Архитектор Авалона] Не удалось выполнить аудит: "
                            + safeMessage(failure) + "."));
        }
    }

    private static void runDiagnostics(KingdomAssistantData data, ServerPlayer owner) {
        try {
            WorldSurveySnapshot survey = lastSurvey(owner.getServer(), owner.getUUID()).orElse(null);
            AssistantDiagnosticReport report = AssistantDiagnosticsRegistry.standard().run(
                    java.util.Set.of(),
                    AssistantDiagnosticContext.live(owner.getServer(), owner, survey));
            SessionResults results = session(owner.getServer(), owner.getUUID());
            results.diagnosticReport = report;
            results.findings = report.findings();
            data.finishTask(owner.getUUID(), "Диагностика: " + report.findings().size()
                    + " замечаний, " + report.failedIds().size() + " сбоев проверок");
            owner.sendSystemMessage(Component.literal("[Архитектор Авалона] Диагностика завершена: "
                    + report.findings().size() + " замечаний; адаптеров: "
                    + report.executedIds().size() + "; сбоев: " + report.failedIds().size()
                    + (report.truncated() ? "; отчёт ограничен 200 пунктами." : ".")));
        } catch (RuntimeException failure) {
            data.finishTask(owner.getUUID(), "Ошибка общей диагностики");
            owner.sendSystemMessage(Component.literal(
                    "[Архитектор Авалона] Диагностика не выполнена: "
                            + safeMessage(failure) + "."));
        }
    }

    private static SessionResults session(MinecraftServer server, UUID owner) {
        return RESULTS.computeIfAbsent(server, unused -> new HashMap<>())
                .computeIfAbsent(owner, unused -> new SessionResults());
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static UUID parseBuildId(String argument) {
        if (argument == null || argument.isBlank()) return null;
        try { return UUID.fromString(argument.trim()); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    private static boolean isWorldMutation(AssistantIntent intent) {
        return intent == AssistantIntent.PREVIEW
                || intent == AssistantIntent.BUILD
                || intent == AssistantIntent.UNDO;
    }

    private static KingdomAssistantEntity loadedAssistant(MinecraftServer server, UUID entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            var entity = level.getEntity(entityId);
            if (entity instanceof KingdomAssistantEntity assistant) return assistant;
        }
        return null;
    }

    private static void sendResult(ServerPlayer owner, AssistantCommand command,
                                   SubmitResult result, KingdomAssistantData data) {
        String message = switch (result) {
            case ACCEPTED -> command.intent() == AssistantIntent.FOLLOW
                    ? "Следую за вами." : command.intent() == AssistantIntent.STAY
                    ? "Остаюсь на месте." : "Задача принята: " + command.intent().name() + ".";
            case BUSY -> "Сначала завершите или отмените текущую задачу.";
            case CANCELLED -> "Текущая задача отменена.";
            case NO_ACTIVE_TASK -> "Активной задачи нет.";
            case READ_ONLY -> {
                var state = data.ownedBy(owner.getUUID()).orElseThrow();
                yield "Режим: " + state.mode().name() + "; задача: "
                        + (state.activeIntent() == null ? "нет" : state.activeIntent().name()) + ".";
            }
            case SURVEY_REQUIRED -> "Сначала выполните обследование территории.";
            case PREVIEW_REJECTED -> "Не удалось открыть проект: проверьте ключ схемы и привязку помощника.";
            case BUILD_REJECTED -> "Нет подтверждённого безопасного проекта для строительства.";
            case UNDO_REJECTED -> "Не найдена творческая стройка для отмены.";
            case NOT_OWNER -> "Помощник ещё не призван или принадлежит другому владельцу.";
            case NOT_ADMIN -> "Управление доступно только владельцу сервера с уровнем прав 4.";
        };
        owner.sendSystemMessage(Component.literal("[Архитектор Авалона] " + message));
    }
}
