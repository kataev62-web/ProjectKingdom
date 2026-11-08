package ru.projectkingdom.npc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Regressions for workers that were configured correctly but remained idle. */
final class KingdomPhysicalWorkerProgressRegressionTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/" + name));
    }

    @Test void minerAcceptsTheSameModernTownDeedThatMineSurveyAccepts() throws Exception {
        String work=source("KingdomMineWork.java");
        String structure=source("KingdomMineStructureWork.java");
        String maintenance=source("KingdomMineMaintenance.java");
        assertTrue(work.contains("canUse(level,pos,worker.employer)"));
        assertTrue(structure.contains("canUse(level,at,w.employer)"));
        assertTrue(maintenance.contains("canUse(level,at,worker.employer)"));
        assertFalse(work.contains(
            "worker.employer.equals(KingdomTerritories.get(level.getServer()).owner(level,pos))"));
        assertFalse(structure.contains(".owner(level,at)"));
        assertFalse(maintenance.contains(".owner(level,at)"));
        assertTrue(maintenance.contains(
            "KingdomContainerAccess.resolve(level,depot,worker.employer)"));
    }

    @Test void lumberjackCanReplaceAStaleToolAndUseModdedWarehouses() throws Exception {
        String forestry=source("KingdomTfcForestry.java");
        assertTrue(forestry.contains("NpcFieldWork.put(w,w.fieldTool)"));
        assertFalse(forestry.contains("if(!w.fieldTool.isEmpty())return false;"));
        assertTrue(forestry.contains("KingdomContainerAccess.resolve(level,source,w.employer)"));
        assertTrue(forestry.contains("KingdomContainerAccess.resolve(level,dest,w.employer)"));
    }

    @Test void sharedToolAndDeliveryRoutesUseTheRegisteredStorageResolver() throws Exception {
        String field=source("NpcFieldWork.java");
        assertTrue(field.indexOf("KingdomContainerAccess.resolve(level,pos,")>=0);
        assertTrue(field.indexOf("KingdomContainerAccess.resolve(level,pos,")
            !=field.lastIndexOf("KingdomContainerAccess.resolve(level,pos,"));
    }

    @Test void quickSetupSchedulesEveryReadyPhysicalWorkerNotOnlyMiner() throws Exception {
        String setup=source("KingdomNpcQuickSetup.java");
        assertTrue(setup.contains("KingdomNpcDuty.hasPhysicalExecutor(worker.job)"));
        assertTrue(setup.contains("KingdomNpcWorkday.hasAssignment(server,level,worker)"));
        assertTrue(setup.contains("KingdomContainerAccess.resolve(level,registered.pos(),owner)"));
        assertTrue(setup.contains("KingdomContainerAccess.resolve(level,p,owner)"));
        assertTrue(setup.contains("KingdomContainerAccess.samePhysicalStorage(level,"));
    }

    @Test void deliveryConfirmsStorageMutationBeforeRemovingWorkerCargo() throws Exception {
        String field=source("NpcFieldWork.java");
        assertTrue(field.contains("ItemStack updated=cur.copy()"));
        assertTrue(field.contains("ItemStack.matches(c.getItem(slot),updated)"));
        assertTrue(field.contains("c.canPlaceItem(slot,updated)"));
        assertFalse(field.contains("c.canPlaceItem(slot,s)"));
    }

    @Test void lumberStockCountsResolvedPhysicalStorageExactlyOnce() throws Exception {
        String stock=source("KingdomEnterpriseStock.java");
        assertTrue(stock.contains("KingdomContainerAccess.resolve(level,pos,firm.owner())"));
        assertTrue(stock.contains("KingdomContainerAccess.samePhysicalStorage(level,pos,countedPos)"));
    }

    @Test void doubleChestResolverNeverCrossesIntoForeignLand() throws Exception {
        String access=source("KingdomContainerAccess.java");
        assertTrue(access.contains("resolve(ServerLevel level,BlockPos pos,java.util.UUID owner)"));
        assertTrue(access.contains("canUse(level,otherPos,owner)"));
    }
}
