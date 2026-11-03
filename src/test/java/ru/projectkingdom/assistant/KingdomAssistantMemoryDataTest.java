package ru.projectkingdom.assistant;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class KingdomAssistantMemoryDataTest {
    private static final UUID OWNER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test void onlyApprovedSummaryKindsSurviveSaveLoad() {
        var before = new KingdomAssistantMemoryData();

        assertTrue(before.rememberApprovedPlan(OWNER, "План дома подтверждён владельцем"));
        assertTrue(before.rememberBuildSummary(OWNER, "Дом завершён: 48 блоков"));
        assertTrue(before.rememberFinding(OWNER, "Складу не хватает еды"));
        assertTrue(before.rememberModDecision(OWNER, "spark: ADD для профилирования"));

        List<KingdomAssistantMemoryData.MemoryRecord> records = KingdomAssistantMemoryData
                .load(before.save(new CompoundTag())).records(OWNER);
        assertEquals(4, records.size());
        assertEquals(List.of(
                KingdomAssistantMemoryData.RecordType.APPROVED_PLAN,
                KingdomAssistantMemoryData.RecordType.BUILD_SUMMARY,
                KingdomAssistantMemoryData.RecordType.FINDING,
                KingdomAssistantMemoryData.RecordType.MOD_DECISION),
                records.stream().map(KingdomAssistantMemoryData.MemoryRecord::type).toList());
    }

    @Test void keepsNewestOneHundredRecordsPerOwner() {
        var before = new KingdomAssistantMemoryData();
        for (int index = 0; index < 105; index++) {
            assertTrue(before.rememberFinding(OWNER, "finding-" + index));
        }

        List<KingdomAssistantMemoryData.MemoryRecord> records = KingdomAssistantMemoryData
                .load(before.save(new CompoundTag())).records(OWNER);
        assertEquals(100, records.size());
        assertEquals("finding-5", records.get(0).summary());
        assertEquals("finding-104", records.get(99).summary());
    }

    @Test void ownerClearDoesNotEraseAnotherOwnersMemory() {
        var memory = new KingdomAssistantMemoryData();
        memory.rememberFinding(OWNER, "owner finding");
        memory.rememberFinding(OTHER, "other finding");

        assertEquals(1, memory.clear(OWNER));
        assertTrue(memory.records(OWNER).isEmpty());
        assertEquals("other finding", memory.records(OTHER).get(0).summary());
    }

    @Test void rejectsSecretsAndWorldSnapshotSizedPayloadsWithoutPersistingThem() {
        var memory = new KingdomAssistantMemoryData();

        assertFalse(memory.rememberApprovedPlan(OWNER, "api_key=sk-secret-value"));
        assertFalse(memory.rememberModDecision(OWNER, "Authorization: Bearer secret"));
        assertFalse(memory.rememberFinding(OWNER, "x".repeat(4_097)));

        assertTrue(memory.records(OWNER).isEmpty());
        assertTrue(KingdomAssistantMemoryData.load(memory.save(new CompoundTag()))
                .records(OWNER).isEmpty());
    }

    @Test void recordsTypedAuditResultsUsedByRuntimeIntegration() {
        var memory = new KingdomAssistantMemoryData();
        AssistantFinding finding = new AssistantFinding(AssistantFinding.Category.WAREHOUSE,
                AssistantFinding.Severity.WARNING, "Склад", "Не хватает склада",
                "Построить склад", "Повторить аудит");
        ModRecommendation recommendation = new ModRecommendation("spark", "spark",
                ModRecommendation.Recommendation.ADD, "Нет профилировщика", "Профилирование",
                "Ручная установка", 60, "Проверить список модов");

        assertEquals(1, memory.rememberFindings(OWNER, List.of(finding)));
        assertEquals(1, memory.rememberModDecisions(OWNER, List.of(recommendation)));

        assertEquals(List.of(KingdomAssistantMemoryData.RecordType.FINDING,
                        KingdomAssistantMemoryData.RecordType.MOD_DECISION),
                memory.records(OWNER).stream()
                        .map(KingdomAssistantMemoryData.MemoryRecord::type).toList());
    }
}
