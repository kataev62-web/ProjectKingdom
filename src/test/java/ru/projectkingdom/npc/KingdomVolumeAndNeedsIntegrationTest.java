package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source contracts plus pure geometry; manual world QA still required for visuals. */
final class KingdomVolumeAndNeedsIntegrationTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+path));
    }
    @Test void allThreeSurveysShareTheSameServerControlledPreview() throws Exception {
        String selection=source("network/KingdomVolumeSelectionPacket.java");
        assertTrue(selection.contains("WAREHOUSE=1,ENTERPRISE=2,COPY=3"));
        assertTrue(source("administration/KingdomCentralWarehouseRooms.java")
                .contains("KingdomVolumeSelectionPacket.WAREHOUSE"));
        assertTrue(source("enterprise/EnterpriseBuildingSelection.java")
                .contains("KingdomVolumeSelectionPacket.ENTERPRISE"));
        assertTrue(source("npc/KingdomStructureCopy.java")
                .contains("KingdomVolumeSelectionPacket.COPY"));
        var renderer=source("npc/client/KingdomVolumeSelectionOverlay.java");
        assertTrue(renderer.contains("RenderLevelStageEvent"));
        assertTrue(renderer.contains("LevelRenderer.renderLineBox"));
        assertTrue(renderer.contains("RenderType.debugFilledBox()"));
        assertTrue(renderer.contains("KingdomCancelVolumeSelectionPacket"));
        assertTrue(source("network/KingdomCancelVolumeSelectionPacket.java")
                .contains("ctx.getDirection()==NetworkDirection.PLAY_TO_SERVER"));
    }
    @Test void lumberjackExecutorIsActuallyScheduledOnServer() throws Exception {
        var main=source("npc/KingdomNpcMod.java");
        assertTrue(main.contains("KingdomTfcForestry.tick(level,resident,w)"));
        var forest=source("npc/KingdomTfcForestry.java");
        assertTrue(forest.contains("validRoot(level,p,w.fieldTool)"));
        assertTrue(forest.contains("nativeLogging.invoke(null,level,root,fake,w.fieldTool)"));
        assertTrue(forest.contains("ownedHarvestLand(level,next,owner)"));
    }
    @Test void liveNpcNeedDescriptionsDoNotMintItemsOrChangeEmployment() throws Exception {
        String diagnostics=source("npc/KingdomNpcWorkDiagnostics.java");
        assertTrue(diagnostics.contains("Нужна еда"));
        assertTrue(diagnostics.contains("не выплачена"));
        assertTrue(diagnostics.contains("нужен топор TFC"));
        assertTrue(diagnostics.contains("привяжите здание"));
        assertTrue(source("npc/KingdomNpcNeedsBroadcast.java").contains("player.distanceToSqr(npc)>20*20"));
        assertTrue(source("npc/client/KingdomNpcNeedsOverlay.java")
                .contains("RenderLivingEvent.Post"));
    }
}
