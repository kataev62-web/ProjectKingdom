package ru.projectkingdom.assistant;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class KingdomAssistantDataTest {
    @Test void ownerBindingSurvivesSaveLoad() {
        UUID owner = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID entity = UUID.fromString("22222222-2222-2222-2222-222222222222");
        var before = new KingdomAssistantData();

        assertTrue(before.bind(owner, entity, "minecraft:overworld"));

        var after = KingdomAssistantData.load(before.save(new CompoundTag()));
        var restored = after.ownedBy(owner).orElseThrow();
        assertEquals(owner, restored.ownerId());
        assertEquals(entity, restored.entityId());
        assertEquals("minecraft:overworld", restored.dimension());
    }

    @Test void onlyOneAssistantIsBoundPerOwner() {
        UUID owner = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID first = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID forged = UUID.fromString("55555555-5555-5555-5555-555555555555");
        var data = new KingdomAssistantData();

        assertTrue(data.bind(owner, first, "minecraft:overworld"));
        assertFalse(data.bind(owner, forged, "minecraft:the_nether"));
        assertEquals(first, data.ownedBy(owner).orElseThrow().entityId());
        assertEquals("minecraft:overworld", data.ownedBy(owner).orElseThrow().dimension());
    }

    @Test void taskAndModeSurviveSaveLoad() {
        UUID owner = UUID.fromString("66666666-6666-6666-6666-666666666666");
        UUID entity = UUID.fromString("77777777-7777-7777-7777-777777777777");
        UUID task = UUID.fromString("88888888-8888-8888-8888-888888888888");
        var before = new KingdomAssistantData();
        before.bind(owner, entity, "minecraft:overworld");

        assertTrue(before.setMode(owner, KingdomAssistantData.Mode.STAY));
        assertTrue(before.beginTask(owner, task, AssistantIntent.SETTLEMENT_AUDIT));

        var restored = KingdomAssistantData.load(before.save(new CompoundTag()))
                .ownedBy(owner).orElseThrow();
        assertEquals(KingdomAssistantData.Mode.STAY, restored.mode());
        assertEquals(task, restored.activeTaskId());
        assertEquals(AssistantIntent.SETTLEMENT_AUDIT, restored.activeIntent());
    }

    @Test void oversizedHistoryIsTrimmedToTwentyEntries() {
        UUID owner = UUID.fromString("99999999-9999-9999-9999-999999999999");
        var data = new KingdomAssistantData();
        data.bind(owner,
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "minecraft:overworld");

        for (int index = 0; index < 25; index++) {
            assertTrue(data.beginTask(owner, new UUID(0L, index + 1L), AssistantIntent.STATUS));
            assertTrue(data.finishTask(owner, "result-" + index));
        }

        var restored = KingdomAssistantData.load(data.save(new CompoundTag()))
                .ownedBy(owner).orElseThrow();
        assertEquals(20, restored.history().size());
        assertEquals("result-5", restored.history().get(0));
        assertEquals("result-24", restored.history().get(19));
    }
}
