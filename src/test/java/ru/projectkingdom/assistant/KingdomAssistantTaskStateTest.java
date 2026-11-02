package ru.projectkingdom.assistant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
