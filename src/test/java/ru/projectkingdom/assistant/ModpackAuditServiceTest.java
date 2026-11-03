package ru.projectkingdom.assistant;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class ModpackAuditServiceTest {
    @Test void baselineAndKingdomIntegrationsAreNeverAutoRemoved() {
        List<InstalledMod> installed = List.of("minecraft", "forge", "kingdomnpc", "tfc",
                        "create", "patchouli").stream()
                .map(id -> new InstalledMod(id, id, "metadata", List.of(), Set.of())).toList();

        var recommendations = ModpackAuditService.audit(installed, emptyInventory());

        assertEquals(6, recommendations.size());
        assertTrue(recommendations.stream().allMatch(item ->
                item.recommendation() == ModRecommendation.Recommendation.KEEP));
    }

    @Test void mineColoniesIsExplicitlyRemovedButUnknownModOnlyReviewed() {
        List<InstalledMod> installed = List.of(
                new InstalledMod("minecolonies", "MineColonies", "colonies", List.of(), Set.of()),
                new InstalledMod("mystery", "Mystery", "unknown", List.of(), Set.of()));

        var recommendations = ModpackAuditService.audit(installed, emptyInventory());

        assertEquals(ModRecommendation.Recommendation.REMOVE,
                byId(recommendations, "minecolonies").recommendation());
        assertEquals(ModRecommendation.Recommendation.REVIEW,
                byId(recommendations, "mystery").recommendation());
    }

    @Test void missingDeclaredCapabilityCanRecommendAddWithoutChangingInstalledList() {
        List<InstalledMod> installed = new ArrayList<>(List.of(
                new InstalledMod("kingdomnpc", "Kingdom", "core", List.of(), Set.of())));
        FeatureInventory inventory = new FeatureInventory(Map.of(), List.of(), Map.of(), Map.of(),
                Set.of("performance_profiling"));

        var recommendations = ModpackAuditService.audit(installed, inventory);

        assertEquals(ModRecommendation.Recommendation.ADD,
                byId(recommendations, "spark").recommendation());
        assertEquals(1, installed.size());
    }

    @Test void auditImplementationNeverWritesModsDirectory() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/assistant/ModpackAuditService.java"));
        assertFalse(source.contains("Files.write"));
        assertFalse(source.contains("Files.delete"));
        assertFalse(source.contains("Files.move"));
    }

    private static ModRecommendation byId(List<ModRecommendation> values, String id) {
        return values.stream().filter(value -> value.modId().equals(id)).findFirst().orElseThrow();
    }

    private static FeatureInventory emptyInventory() {
        return new FeatureInventory(Map.of(), List.of(), Map.of(), Map.of(), Set.of());
    }
}
