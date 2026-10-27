package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Regression guards for two-click bank surveys and TFC tree ownership. */
final class KingdomBankSurveyAndTreeClaimRegressionTest {
    private static String src(String path)throws Exception{
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+path));
    }

    @Test void bankAndWarehouseSurveysConsumeOnlyMainHandAndRetainFailedSecondCorner()throws Exception {
        String mod=src("npc/KingdomNpcMod.java");
        for(String name:new String[]{"selectWarehouseCorners","selectBankVaultCorners"}){
            int start=mod.indexOf("public void "+name+"(");
            assertTrue(start>=0);
            assertTrue(mod.substring(start,start+320).contains("InteractionHand.MAIN_HAND"),name);
        }
        String vault=src("administration/KingdomBankVaultRooms.java");
        int first=vault.indexOf("if(session.first()==null)");
        int commit=vault.indexOf("rooms.put(session.town(),proposed);setDirty();");
        assertTrue(first>=0 && commit>first);
        assertFalse(vault.substring(first,commit).contains("SESSIONS.remove(player.getUUID());"),
                "Invalid second corner must not clear the first corner");
        assertTrue(vault.substring(commit).contains("SESSIONS.remove(player.getUUID());"),
                "Success closes the bank survey");
    }

    @Test void aPublicCityPlotDoesNotLetVisitorsHarvestTfcTrees()throws Exception{
        String protection=src("npc/KingdomClaimProtection.java");
        assertTrue(protection.contains("TFC_FELLABLE_LOGS"));
        assertTrue(protection.contains("\"logs_that_log\""));
        assertTrue(protection.contains("isTreePart(level.getBlockState(pos))"));
        assertTrue(protection.indexOf("isTreePart(level.getBlockState(pos))")
                <protection.indexOf("KingdomTemporaryLand.temporaryArea("));
        assertTrue(protection.contains("treeFellingTouchesForeignLand"));
        assertTrue(protection.contains("level.hasChunkAt(next)"),
                "Cross-boundary scan must not force chunk loads");
        assertTrue(protection.contains("visited.size()>=512"),
                "Cross-boundary scan must have a strict limit");
        String open=src("settlement/KingdomOpenLandEvents.java");
        assertTrue(open.contains("KingdomClaimProtection.isTreePart(state)"));
    }
}
