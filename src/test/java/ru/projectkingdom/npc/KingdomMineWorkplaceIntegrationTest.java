package ru.projectkingdom.npc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Regression for a miner receiving "Нужно рабочее место" despite a mine. */
final class KingdomMineWorkplaceIntegrationTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/"+relative));
    }

    @Test void miningAssignmentUsesEnterpriseShaftInsteadOfCraftingStation() throws Exception {
        var site=source("KingdomMineSite.java");
        var workday=source("KingdomNpcWorkday.java");
        assertTrue(site.contains("EnterpriseKind.MINE"));
        assertTrue(site.contains("KingdomMineCentralShaftPlan.entrance(area)"));
        assertTrue(site.contains("KingdomNpcWorkday.employed(server,worker)"));
        assertFalse(site.contains("worker.hasWorkstation"));
        assertTrue(workday.contains("if (\"miner\".equals(worker.job))"));
        assertTrue(workday.contains("KingdomMineSite.inspect(server,level,worker)"));
        assertTrue(workday.contains("return mine.ready()?mine.entrance():null"));
    }

    @Test void spokenWorkStatusReportsRealMineBlockers() throws Exception {
        var dialog=source("NpcDialogueService.java");
        var diagnostics=source("KingdomNpcWorkDiagnostics.java");
        assertTrue(dialog.contains("KingdomMineSite.inspect("));
        assertTrue(dialog.contains("w.mineStatus"));
        assertTrue(diagnostics.contains("Шахтёр: "));
        assertTrue(diagnostics.contains("mine.reason()"));
    }

    @Test void selectingShaftEnablesDownAndWorkOrderIsScheduledPromptly() throws Exception {
        String selection=source("KingdomMineSelection.java");
        String setup=source("KingdomNpcQuickSetup.java");
        assertTrue(selection.contains("mineSettings.choose(p.mine(),KingdomMineSettings.Start.DOWN)"));
        assertTrue(setup.contains("worker.order=\"work\""));
        assertTrue(setup.contains("KingdomNpcDuty.hasPhysicalExecutor(worker.job)"));
        assertTrue(setup.contains("KingdomNpcWorkday.hasAssignment(server,level,worker)"));
        assertTrue(setup.contains("CONTINUE_ASSIGNED_JOB.name()"));
    }

    @Test void quickSetupAndExecutorUseSameSurveyedEntrance() throws Exception {
        assertTrue(source("KingdomNpcQuickSetup.java")
            .contains("KingdomMineCentralShaftPlan.entrance(settings.shaft(business.id()))"));
        assertTrue(source("KingdomMineWork.java")
            .contains("KingdomMineCentralShaftPlan.entrance(area)"));
    }
}
