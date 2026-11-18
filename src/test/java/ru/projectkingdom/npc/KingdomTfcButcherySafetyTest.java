package ru.projectkingdom.npc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Structural safety regression checks for the reflection-only TFC integration.
 * Runtime TFC feeding/culling still requires an in-game integration test.
 */
final class KingdomTfcButcherySafetyTest {
    private static String source() throws Exception {
        return Files.readString(Path.of(
            "src/main/java/ru/projectkingdom/npc/KingdomTfcButcheryWork.java"));
    }

    @Test void calendarUsesPublicTfcApiRatherThanPrivateImplementation() throws Exception {
        String code=source();
        assertTrue(code.contains("net.dries007.tfc.util.calendar.ICalendar"));
        assertTrue(code.contains("getTotalDays.invoke(calendar)"));
        assertFalse(code.contains("calendar.getClass().getMethod(\"getTotalDays\")"));
    }

    @Test void cullingChecksClaimsBeforeHarmingAnimals() throws Exception {
        String code=source();
        assertTrue(code.contains("animal.isAlive()&&pen.contains(animal)"));
        assertTrue(code.contains("level.hasChunkAt(animal.blockPosition())"));
        assertTrue(code.contains("claims.canUse(level,animal.blockPosition(),owner)"));
        assertTrue(code.contains("ownedHerd.size()<=policy.minHerd()"));
        assertTrue(code.contains("mayCull(policy,day,ownedHerd.size(),true,false)"));
        int recheck=code.indexOf("if(!claims.canUse(level,animal.blockPosition(),owner)||!eligible(animal))");
        int harm=code.indexOf("animal.hurt(");
        assertTrue(recheck>=0 && harm>recheck,
            "Permission must be rechecked before irreversible culling");
    }
}
