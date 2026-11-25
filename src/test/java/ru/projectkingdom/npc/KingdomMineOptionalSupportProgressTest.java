package ru.projectkingdom.npc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Prevents a scheduled TFC post pocket from halting safe mining forever. */
final class KingdomMineOptionalSupportProgressTest {
    private static String work() throws Exception {
        return Files.readString(Path.of(
            "src/main/java/ru/projectkingdom/npc/KingdomMineWork.java"));
    }

    @Test void plannedFrameDoesNotBlockAlreadySafeRock() throws Exception {
        String code=work();
        assertTrue(code.contains(
            "if(prepare==KingdomMineSupportExcavation.Result.UNSAFE)"));
        assertTrue(code.contains(
            "Карманы опор недоступны; продолжает безопасную проходку"));
        assertTrue(code.contains(
            "if(build==KingdomMineSupportBuilder.Result.NEED_CLEAR_POCKETS"));
        assertTrue(code.contains(
            "Крепление пока недоступно; продолжает безопасную добычу"));
    }

    @Test void trulyUnsafeTfcRockStillRequiresPhysicalSupports() throws Exception {
        String code=work();
        int dangerous=code.indexOf(
            "if(supportSafety==KingdomMineSupportSafety.Result.NEEDS_HORIZONTAL_BEAM)");
        // The block is no longer replaced with air: the break goes through the
        // executor, so that the Forge break event may refuse it. The guard of
        // the dangerous rock must still come first.
        int mining=code.indexOf("KingdomActionEngine.breakBlock(level,npc,worker,target,realTool)");
        assertTrue(dangerous>0 && mining>dangerous);
        String guard=code.substring(dangerous,mining);
        assertTrue(guard.contains("KingdomMineSupportPlan.emergency(previous,floorCell)"));
        assertTrue(guard.contains(
            "Опасная порода: нет безопасного места для установки опор TFC"));
        assertTrue(guard.contains("return false;"));
    }
}
