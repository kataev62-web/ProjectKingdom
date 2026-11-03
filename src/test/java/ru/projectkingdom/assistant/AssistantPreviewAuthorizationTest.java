package ru.projectkingdom.assistant;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class AssistantPreviewAuthorizationTest {
    private static final UUID TOKEN = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OWNER = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID ASSISTANT = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final BlockPos ANCHOR = new BlockPos(10, 70, -10);

    @Test void everyRejectedDecisionLeavesProposalQueueUntouched() {
        var session = new AssistantPreviewService.SessionProof(
                TOKEN, OWNER, ASSISTANT, "minecraft:overworld", ANCHOR, 1_200);
        var valid = validAttempt();
        List<Case> rejected = List.of(
                new Case(valid.withToken(UUID.randomUUID()),
                        AssistantPreviewService.Authorization.STALE_TOKEN),
                new Case(valid.withActor(UUID.randomUUID()),
                        AssistantPreviewService.Authorization.WRONG_OWNER),
                new Case(valid.withSavedAssistant(UUID.randomUUID()),
                        AssistantPreviewService.Authorization.WRONG_ASSISTANT),
                new Case(valid.withPermissionLevel(3),
                        AssistantPreviewService.Authorization.NOT_ADMIN),
                new Case(valid.withGameTime(1_201),
                        AssistantPreviewService.Authorization.EXPIRED),
                new Case(valid.withDimension("minecraft:the_nether"),
                        AssistantPreviewService.Authorization.DIMENSION_CHANGED),
                new Case(valid.withOrigin(ANCHOR.offset(81, 0, 0)),
                        AssistantPreviewService.Authorization.OUT_OF_RANGE),
                new Case(valid.withOrigin(ANCHOR.offset(0, 33, 0)),
                        AssistantPreviewService.Authorization.OUT_OF_RANGE),
                new Case(valid.withOrigin(ANCHOR.offset(0, 0, -81)),
                        AssistantPreviewService.Authorization.OUT_OF_RANGE),
                new Case(valid.withRotation(4),
                        AssistantPreviewService.Authorization.INVALID_ROTATION),
                new Case(valid.withOrigin(null),
                        AssistantPreviewService.Authorization.INVALID_PACKET),
                new Case(valid.withToken(null),
                        AssistantPreviewService.Authorization.INVALID_PACKET));

        AtomicInteger queued = new AtomicInteger();
        for (Case item : rejected) {
            assertEquals(item.expected(), AssistantPreviewService.authorizeAndQueue(
                    session, item.attempt(), queued::incrementAndGet));
            assertEquals(0, queued.get(), "rejected packet must not queue build data");
        }
    }

    @Test void exactOwnerAssistantAndBoundsQueueOneProposal() {
        var session = new AssistantPreviewService.SessionProof(
                TOKEN, OWNER, ASSISTANT, "minecraft:overworld", ANCHOR, 1_200);
        AtomicInteger queued = new AtomicInteger();

        assertEquals(AssistantPreviewService.Authorization.ACCEPTED,
                AssistantPreviewService.authorizeAndQueue(
                        session, validAttempt(), queued::incrementAndGet));
        assertEquals(1, queued.get());
    }

    private static AssistantPreviewService.DecisionAttempt validAttempt() {
        return new AssistantPreviewService.DecisionAttempt(
                TOKEN, OWNER, ASSISTANT, 4, "minecraft:overworld",
                ANCHOR.offset(80, 32, -80), 3, 1_200);
    }

    private record Case(AssistantPreviewService.DecisionAttempt attempt,
                        AssistantPreviewService.Authorization expected) {}
}
