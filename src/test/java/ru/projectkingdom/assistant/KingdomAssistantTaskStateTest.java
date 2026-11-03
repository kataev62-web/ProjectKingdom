package ru.projectkingdom.assistant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import net.minecraft.core.BlockPos;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class KingdomAssistantTaskStateTest {
    private final UUID owner = UUID.fromString("60000000-0000-0000-0000-000000000006");
    private KingdomAssistantData data;

    @BeforeEach void bindAssistant() {
        data = new KingdomAssistantData();
        data.bind(owner, UUID.fromString("70000000-0000-0000-0000-000000000007"),
                "minecraft:overworld");
    }

    @Test void oneActiveTaskPerOwner() {
        UUID first = UUID.fromString("80000000-0000-0000-0000-000000000008");
        UUID second = UUID.fromString("90000000-0000-0000-0000-000000000009");

        assertEquals(KingdomAssistantTasks.SubmitResult.ACCEPTED,
                KingdomAssistantTasks.transition(data, owner,
                        new AssistantCommand(AssistantIntent.SURVEY, ""), first));
        assertEquals(KingdomAssistantTasks.SubmitResult.BUSY,
                KingdomAssistantTasks.transition(data, owner,
                        new AssistantCommand(AssistantIntent.DIAGNOSTICS, ""), second));
        assertEquals(first, data.ownedBy(owner).orElseThrow().activeTaskId());
    }

    @Test void cancelIsIdempotent() {
        KingdomAssistantTasks.transition(data, owner,
                new AssistantCommand(AssistantIntent.SURVEY, ""), UUID.randomUUID());

        assertEquals(KingdomAssistantTasks.SubmitResult.CANCELLED,
                KingdomAssistantTasks.transition(data, owner,
                        new AssistantCommand(AssistantIntent.CANCEL, ""), UUID.randomUUID()));
        assertEquals(KingdomAssistantTasks.SubmitResult.NO_ACTIVE_TASK,
                KingdomAssistantTasks.transition(data, owner,
                        new AssistantCommand(AssistantIntent.CANCEL, ""), UUID.randomUUID()));
        assertNull(data.ownedBy(owner).orElseThrow().activeTaskId());
    }

    @Test void followAndStayCannotOverwriteRunningWorldMutation() {
        KingdomAssistantTasks.transition(data, owner,
                new AssistantCommand(AssistantIntent.BUILD, ""), UUID.randomUUID());

        assertEquals(KingdomAssistantTasks.SubmitResult.BUSY,
                KingdomAssistantTasks.transition(data, owner,
                        new AssistantCommand(AssistantIntent.STAY, ""), UUID.randomUUID()));
        assertEquals(KingdomAssistantData.Mode.FOLLOW, data.ownedBy(owner).orElseThrow().mode());
        assertEquals(AssistantIntent.BUILD, data.ownedBy(owner).orElseThrow().activeIntent());
    }

    @Test void statusIsReadOnly() {
        var before = data.ownedBy(owner).orElseThrow();

        assertEquals(KingdomAssistantTasks.SubmitResult.READ_ONLY,
                KingdomAssistantTasks.transition(data, owner,
                        new AssistantCommand(AssistantIntent.STATUS, ""), UUID.randomUUID()));

        assertEquals(before, data.ownedBy(owner).orElseThrow());
    }

    @Test void confirmedPreviewImmediatelyStartsTheRealBuild() {
        UUID assistant = data.ownedBy(owner).orElseThrow().entityId();
        assertTrue(data.beginTask(owner, UUID.randomUUID(), AssistantIntent.PREVIEW));
        var world = new AssistantCreativeBuildServiceTest.Fixture();
        var design = AssistantCreativeBuildServiceTest.line(1, "minecraft:stone_bricks");
        var builds = new AssistantCreativeBuildService(new KingdomAssistantBuildData(), world,
                key -> "HOUSE".equals(key) ? design : null);
        var proposal = new AssistantPreviewService.BuildProposal(owner, assistant,
                "minecraft:overworld", "HOUSE", new BlockPos(0, 64, 0), design);
        AtomicInteger consumed = new AtomicInteger();

        var result = KingdomAssistantTasks.startConfirmedPreview(
                data, builds, proposal, consumed::incrementAndGet, UUID.randomUUID());

        assertEquals(KingdomAssistantTasks.SubmitResult.ACCEPTED, result);
        assertEquals(1, consumed.get());
        assertEquals(AssistantIntent.BUILD, data.ownedBy(owner).orElseThrow().activeIntent());
        assertEquals(1, builds.tick().changed());
    }
}
