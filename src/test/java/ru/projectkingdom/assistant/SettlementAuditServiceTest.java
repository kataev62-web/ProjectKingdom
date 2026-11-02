package ru.projectkingdom.assistant;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import ru.projectkingdom.settlement.KingdomSettlementGrowth;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class SettlementAuditServiceTest {
    @Test void pinsCurrentPromotionThresholds() {
        assertEquals(new SettlementAuditService.Target(
                        KingdomSettlementGrowth.Rank.VILLAGE, 4, 1, 3, true),
                SettlementAuditService.targetAfter(KingdomSettlementGrowth.Rank.HAMLET));
        assertEquals(new SettlementAuditService.Target(
                        KingdomSettlementGrowth.Rank.SETTLEMENT, 12, 4, 8, true),
                SettlementAuditService.targetAfter(KingdomSettlementGrowth.Rank.VILLAGE));
        assertEquals(new SettlementAuditService.Target(
                        KingdomSettlementGrowth.Rank.CITY, 32, 11, 20, true),
                SettlementAuditService.targetAfter(KingdomSettlementGrowth.Rank.SETTLEMENT));
        assertNull(SettlementAuditService.targetAfter(KingdomSettlementGrowth.Rank.CITY));
    }

    @Test void reportsMissingTavern() {
        var findings = SettlementAuditService.assess(
                new KingdomSettlementGrowth.Progress(4, 1, 3, false), true, connectedSnapshot());

        assertTrue(hasCategory(findings, AssistantFinding.Category.FOOD_AND_LODGING));
        assertTrue(findings.stream().anyMatch(f -> f.reason().contains("таверн")));
    }

    @Test void reportsHousingAndBuildingGap() {
        var findings = SettlementAuditService.assess(
                new KingdomSettlementGrowth.Progress(12, 3, 8, true), true, connectedSnapshot());

        AssistantFinding finding = findings.stream()
                .filter(f -> f.category() == AssistantFinding.Category.HOUSING)
                .findFirst().orElseThrow();
        assertTrue(finding.reason().contains("3 из 4"));
        assertTrue(finding.verification().contains("4"));
    }

    @Test void reportsAbsentCentralWarehouse() {
        var findings = SettlementAuditService.assess(
                new KingdomSettlementGrowth.Progress(12, 4, 8, true), false, connectedSnapshot());

        assertTrue(hasCategory(findings, AssistantFinding.Category.LOGISTICS));
    }

    @Test void reportsDisconnectedRoad() {
        WorldSurveySnapshot snapshot = snapshot(
                List.of(new WorldSurveySnapshot.TerrainColumn(0, 0, 64, false, true)),
                List.of(
                        new WorldSurveySnapshot.BuildingOutline("a", "HOUSE", 0, 0, 0, 0),
                        new WorldSurveySnapshot.BuildingOutline("b", "HOUSE", 16, 0, 16, 0)));

        var findings = SettlementAuditService.assess(
                new KingdomSettlementGrowth.Progress(32, 11, 20, true), true, snapshot);

        assertTrue(hasCategory(findings, AssistantFinding.Category.ROADS));
    }

    @Test void fullySatisfiedSettlementProducesNoFinding() {
        var findings = SettlementAuditService.assess(
                new KingdomSettlementGrowth.Progress(32, 11, 20, true), true, connectedSnapshot());

        assertEquals(List.of(), findings);
    }

    private static boolean hasCategory(List<AssistantFinding> findings,
                                       AssistantFinding.Category category) {
        return findings.stream().anyMatch(f -> f.category() == category);
    }

    private static WorldSurveySnapshot connectedSnapshot() {
        List<WorldSurveySnapshot.TerrainColumn> roads = new ArrayList<>();
        for (int x = 0; x <= 8; x += 2) {
            roads.add(new WorldSurveySnapshot.TerrainColumn(x, 0, 64, false, true));
        }
        return snapshot(roads, List.of(
                new WorldSurveySnapshot.BuildingOutline("a", "HOUSE", 0, 0, 0, 0),
                new WorldSurveySnapshot.BuildingOutline("b", "HOUSE", 8, 0, 8, 0)));
    }

    private static WorldSurveySnapshot snapshot(
            List<WorldSurveySnapshot.TerrainColumn> columns,
            List<WorldSurveySnapshot.BuildingOutline> outlines) {
        return new WorldSurveySnapshot("minecraft:overworld", new BlockPos(4, 64, 0), 8,
                columns, outlines, List.of(), List.of(), 0);
    }
}
