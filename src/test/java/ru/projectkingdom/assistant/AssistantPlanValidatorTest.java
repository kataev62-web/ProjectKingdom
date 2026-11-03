package ru.projectkingdom.assistant;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

final class AssistantPlanValidatorTest {
    private static final UUID REQUEST = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test void acceptsOnlyBoundedAdvisoryActions() {
        var response = new AssistantModelClient.ModelResponse(REQUEST, List.of(
                action("REPORT", "Проверить склад"),
                action("SUGGEST_SCHEMATIC", "kingdomnpc:small_house"),
                action("REQUEST_SURVEY", "радиус 24"),
                action("REQUEST_DIAGNOSTICS", "строительство")), "Безопасный план");

        var result = new AssistantPlanValidator().validate(response, limits());

        assertTrue(result.accepted());
        assertEquals(4, result.actions().size());
        assertEquals(AssistantPlanner.ActionType.REPORT, result.actions().get(0).type());
        assertEquals("Безопасный план", result.text());
    }

    @Test void rejectsForbiddenIntentsCommandsPathsAndRawBlocksWithoutActions() {
        List<AssistantModelClient.ModelAction> forbidden = List.of(
                action("BUILD", "дом"),
                action("UNDO", "последнее"),
                action("TELEPORT", "spawn"),
                action("REPORT", "/op player"),
                action("REPORT", "execute as @a run kill @s"),
                action("REPORT", "../server.properties"),
                action("REPORT", "C:\\world\\level.dat"),
                new AssistantModelClient.ModelAction("REPORT", "готовые блоки",
                        List.of("minecraft:stone", "minecraft:air")));

        for (AssistantModelClient.ModelAction candidate : forbidden) {
            var result = new AssistantPlanValidator().validate(
                    new AssistantModelClient.ModelResponse(REQUEST, List.of(candidate), "text"), limits());
            assertFalse(result.accepted(), candidate.toString());
            assertTrue(result.actions().isEmpty(), "rejection must queue zero actions");
        }
    }

    @Test void rejectsMalformedOversizedAndMismatchedResponsesWithoutActions() {
        List<AssistantModelClient.ModelResponse> invalid = new ArrayList<>();
        invalid.add(null);
        invalid.add(new AssistantModelClient.ModelResponse(null, List.of(), "text"));
        invalid.add(new AssistantModelClient.ModelResponse(UUID.randomUUID(), List.of(), "text"));
        invalid.add(new AssistantModelClient.ModelResponse(REQUEST, null, "text"));
        invalid.add(new AssistantModelClient.ModelResponse(REQUEST,
                java.util.Collections.singletonList(null), "text"));
        invalid.add(new AssistantModelClient.ModelResponse(REQUEST,
                java.util.Collections.nCopies(21, action("REPORT", "ok")), "text"));
        invalid.add(new AssistantModelClient.ModelResponse(REQUEST, List.of(), "x".repeat(64_001)));
        invalid.add(new AssistantModelClient.ModelResponse(REQUEST,
                List.of(new AssistantModelClient.ModelAction(null, "ok", List.of())), "text"));

        for (AssistantModelClient.ModelResponse response : invalid) {
            var result = new AssistantPlanValidator().validate(response, limits());
            assertFalse(result.accepted());
            assertTrue(result.actions().isEmpty(), "rejection must queue zero actions");
        }
    }

    @Test void disabledUnavailableAndRejectedModelResponsesFallBackToRules() {
        AssistantPlanner.PlanningRequest request = request();
        AssistantPlanner rules = new RuleBasedAssistantPlanner();
        AssistantPlanner.PlanningResult expected = rules.plan(request).join();

        AssistantPlanner disabled = new HybridAssistantPlanner(
                new DisabledAssistantModelClient(), new AssistantPlanValidator(), rules);
        assertEquals(expected, disabled.plan(request).join());

        AssistantModelClient unavailable = new AssistantModelClient() {
            @Override public boolean enabled() { return true; }
            @Override public CompletableFuture<ModelResponse> propose(ModelRequest ignored) {
                return CompletableFuture.failedFuture(new IllegalStateException("offline"));
            }
        };
        assertEquals(expected, new HybridAssistantPlanner(unavailable,
                new AssistantPlanValidator(), rules).plan(request).join());

        AssistantModelClient unsafe = fixed(new AssistantModelClient.ModelResponse(REQUEST,
                List.of(action("BUILD", "castle")), "unsafe"));
        assertEquals(expected, new HybridAssistantPlanner(unsafe,
                new AssistantPlanValidator(), rules).plan(request).join());
    }

    @Test void acceptedModelResponseRemainsAdvisoryAndRequiresLaterConfirmation() {
        AssistantModelClient client = fixed(new AssistantModelClient.ModelResponse(REQUEST,
                List.of(action("SUGGEST_SCHEMATIC", "kingdomnpc:small_house")), "Предложение"));

        AssistantPlanner.PlanningResult result = new HybridAssistantPlanner(client,
                new AssistantPlanValidator(), new RuleBasedAssistantPlanner()).plan(request()).join();

        assertTrue(result.modelUsed());
        assertEquals(List.of(new AssistantPlanner.PlannedAction(
                AssistantPlanner.ActionType.SUGGEST_SCHEMATIC,
                "kingdomnpc:small_house")), result.actions());
        assertEquals("Предложение", result.text());
    }

    @Test void rulePlannerIsDeterministicAndRequestsMissingSurvey() {
        AssistantPlanner planner = new RuleBasedAssistantPlanner();

        AssistantPlanner.PlanningResult first = planner.plan(request()).join();
        AssistantPlanner.PlanningResult second = planner.plan(request()).join();

        assertEquals(first, second);
        assertEquals(AssistantPlanner.ActionType.REQUEST_SURVEY, first.actions().get(0).type());
        assertFalse(first.modelUsed());
    }

    private static AssistantPlanValidator.PlanningLimits limits() {
        return new AssistantPlanValidator.PlanningLimits(REQUEST, 20, 64_000);
    }

    private static AssistantModelClient.ModelAction action(String type, String argument) {
        return new AssistantModelClient.ModelAction(type, argument, List.of());
    }

    private static AssistantModelClient fixed(AssistantModelClient.ModelResponse response) {
        return new AssistantModelClient() {
            @Override public boolean enabled() { return true; }
            @Override public CompletableFuture<ModelResponse> propose(ModelRequest ignored) {
                return CompletableFuture.completedFuture(response);
            }
        };
    }

    private static AssistantPlanner.PlanningRequest request() {
        return new AssistantPlanner.PlanningRequest(REQUEST, OWNER, "проверь город",
                null, List.of(), List.of(), List.of());
    }
}
