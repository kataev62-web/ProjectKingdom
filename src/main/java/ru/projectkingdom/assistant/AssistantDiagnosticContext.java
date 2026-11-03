package ru.projectkingdom.assistant;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/** Immutable inputs shared by read-only diagnostic adapters. */
public record AssistantDiagnosticContext(
        MinecraftServer server,
        ServerPlayer player,
        WorldSurveySnapshot survey,
        Map<String, String> snapshots
) {
    public AssistantDiagnosticContext {
        snapshots = snapshots == null ? Map.of() : Map.copyOf(snapshots);
    }

    public static AssistantDiagnosticContext live(
            MinecraftServer server, ServerPlayer player, WorldSurveySnapshot survey) {
        return new AssistantDiagnosticContext(server, player, survey, Map.of());
    }

    public static AssistantDiagnosticContext forTest(Map<String, String> snapshots) {
        return new AssistantDiagnosticContext(null, null, null, snapshots);
    }
}
