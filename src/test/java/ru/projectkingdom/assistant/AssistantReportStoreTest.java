package ru.projectkingdom.assistant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class AssistantReportStoreTest {
    @TempDir Path world;

    @Test void rejectsTraversalAbsolutePathsAndReportsOverOneMebibyte() {
        var store = new AssistantReportStore(world);
        var report = report("Безопасный отчёт");

        assertThrows(IllegalArgumentException.class,
                () -> store.write("../outside.json", report));
        assertThrows(IllegalArgumentException.class,
                () -> store.write("nested/outside.json", report));
        assertThrows(IllegalArgumentException.class,
                () -> store.write(world.resolve("absolute.json").toString(), report));

        String maximum = "я".repeat(512);
        AssistantFinding large = new AssistantFinding(AssistantFinding.Category.FUNCTIONALITY,
                AssistantFinding.Severity.WARNING, maximum, maximum, maximum, maximum);
        var oversized = new AssistantReportStore.Report("Большой отчёт",
                Collections.nCopies(1_000, large), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> store.write("oversized.json", oversized));
    }

    @Test void writesRussianJsonAsUtf8UnderWorldReportDirectory() throws Exception {
        var store = new AssistantReportStore(world);

        Path saved = store.write("settlement.json", report("Проверка поселения"));

        assertEquals(world.resolve("kingdom-assistant/reports/settlement.json"), saved);
        String json = Files.readString(saved, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(json.contains("Проверка поселения"));
        assertTrue(json.contains("Не хватает склада"));
    }

    @Test void atomicallyReplacesNamedReportWithoutLeavingTemporaryFiles() throws Exception {
        var store = new AssistantReportStore(world);
        Path first = store.write("audit.json", report("Первый"));

        Path replaced = store.write("audit.json", report("Второй"));

        assertEquals(first, replaced);
        String json = Files.readString(replaced, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(json.contains("Второй"));
        assertFalse(json.contains("Первый"));
        try (var files = Files.list(replaced.getParent())) {
            assertEquals(List.of("audit.json"),
                    files.map(path -> path.getFileName().toString()).sorted().toList());
        }
    }

    @Test void retainsOnlyNewestTwentyReports() throws Exception {
        var store = new AssistantReportStore(world);
        for (int index = 0; index < 25; index++) {
            store.write(String.format("report-%02d.json", index), report("Отчёт " + index));
        }

        List<String> names = store.list().stream()
                .map(path -> path.getFileName().toString()).sorted().toList();
        assertEquals(20, names.size());
        assertFalse(names.contains("report-00.json"));
        assertFalse(names.contains("report-04.json"));
        assertTrue(names.contains("report-05.json"));
        assertTrue(names.contains("report-24.json"));
    }

    private static AssistantReportStore.Report report(String title) {
        AssistantFinding finding = new AssistantFinding(AssistantFinding.Category.WAREHOUSE,
                AssistantFinding.Severity.WARNING, "Склад", "Не хватает склада",
                "Построить центральный склад", "Повторить аудит");
        ModRecommendation recommendation = new ModRecommendation("spark", "spark",
                ModRecommendation.Recommendation.ADD, "Нет профилировщика",
                "Диагностика нагрузки", "Только по решению владельца", 60,
                "Проверить список модов");
        return new AssistantReportStore.Report(title, List.of(finding), List.of(recommendation));
    }
}
