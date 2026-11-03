package ru.projectkingdom.npc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class KingdomArchitectSurvivalRegressionTest {
    @Test void survivalPreviewKeepsMarkerSiteAndMaterialPipeline() throws Exception {
        String sessions = Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/npc/KingdomArchitectSessions.java"));
        String builder = Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/npc/KingdomSiteBuilder.java"));

        assertTrue(sessions.contains("SURVIVAL_SITE"));
        assertTrue(sessions.contains("KingdomConstructionMarkers.isMarker"));
        assertTrue(sessions.contains("KingdomSiteBuilder.placeAt"));
        assertTrue(builder.contains("KingdomConstructionRegistry.BUILDING_BLUEPRINT"));
        assertTrue(builder.contains("db.add(player.getUUID()"));
        assertTrue(builder.contains("KingdomConstructionWork.placeNext"));
        assertTrue(builder.contains("if (!player.isCreative())"));
    }

    @Test void assistantPurposeQueuesProposalWithoutUsingSurvivalPlacement() throws Exception {
        String sessions = Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/npc/KingdomArchitectSessions.java"));
        String service = Files.readString(Path.of(
                "src/main/java/ru/projectkingdom/assistant/AssistantPreviewService.java"));

        assertTrue(sessions.contains("ASSISTANT_CREATIVE"));
        assertTrue(sessions.contains("AssistantPreviewService.queue"));
        assertFalse(service.contains("KingdomSiteBuilder.placeAt"));
        assertFalse(service.contains("KingdomConstructionData.get"));
    }
}
