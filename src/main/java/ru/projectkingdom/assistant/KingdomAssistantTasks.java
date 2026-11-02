package ru.projectkingdom.assistant;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;

/** Server dispatcher for one active assistant task per owner. */
public final class KingdomAssistantTasks {
    public enum SubmitResult {
        ACCEPTED, BUSY, CANCELLED, NO_ACTIVE_TASK, READ_ONLY, NOT_OWNER, NOT_ADMIN
    }

    private KingdomAssistantTasks() {}

    public static SubmitResult submit(ServerPlayer owner, AssistantCommand command) {
        Objects.requireNonNull(command, "command");
        if (owner == null || owner.getServer() == null || !owner.hasPermissions(4))
            return SubmitResult.NOT_ADMIN;
        KingdomAssistantData data = KingdomAssistantData.get(owner.getServer());
        if (data.ownedBy(owner.getUUID()).isEmpty()) return SubmitResult.NOT_OWNER;
        SubmitResult result = transition(data, owner.getUUID(), command, UUID.randomUUID());
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
        }
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
            case NOT_OWNER -> "Помощник ещё не призван или принадлежит другому владельцу.";
            case NOT_ADMIN -> "Управление доступно только владельцу сервера с уровнем прав 4.";
        };
        owner.sendSystemMessage(Component.literal("[Архитектор Авалона] " + message));
    }
}
