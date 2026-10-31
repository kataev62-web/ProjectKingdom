package ru.projectkingdom.npc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Guards pen discovery and preserves TerraFirmaCraft's native player interaction. */
final class KingdomLivestockInteractionRegressionTest {
    private static String read(String path)throws Exception{
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+path));
    }

    @Test void penScanUsesLoadedChunksIntersectingTheWholePasture()throws Exception{
        String work=read("npc/KingdomLivestockWork.java");
        assertTrue(work.contains("private static BlockPos loadedAnchor"));
        assertTrue(work.contains("for(int cx=pen.minX()>>4"));
        assertTrue(work.contains("for(int cz=pen.minZ()>>4"));
        assertFalse(work.contains("!level.hasChunkAt(pen.center())"));
        assertTrue(work.contains("pen.y()-8"));
        assertTrue(work.contains("pen.y()+17"));
        String menu=read("npc/KingdomRanchPenMenu.java");
        assertTrue(menu.contains("status.loaded()"));
    }

    @Test void npcFeedingUsesPublicTfcCalendarInterfaceAndKeepsFoodRemainder()throws Exception{
        String work=read("npc/KingdomLivestockWork.java");
        assertTrue(work.contains("net.dries007.tfc.util.calendar.ICalendar"));
        assertTrue(work.contains("getTotalDays.invoke(calendar)"));
        assertFalse(work.contains("calendar.getClass().getMethod(\"getTotalDays\")"));
        assertTrue(work.contains("feedRemainder(item)"));
        assertTrue(work.contains("NpcFieldWork.put(worker,remainder)"));
    }

    @Test void kingdomDoesNotCancelNativeTfcAnimalHandFeeding()throws Exception{
        String mod=read("npc/KingdomNpcMod.java");
        int start=mod.indexOf("public void explainTfcLivestockFeed(");
        assertTrue(start>=0);
        int end=mod.indexOf("public static void openNpcMenu",start);
        assertTrue(end>start);
        String handler=mod.substring(start,end);
        assertTrue(handler.contains("playerFeedNotice"));
        assertFalse(handler.contains("setCanceled"));
        assertTrue(handler.contains("Deliberately do NOT cancel"));
    }
}
