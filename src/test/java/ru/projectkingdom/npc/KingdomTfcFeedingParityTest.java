package ru.projectkingdom.npc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Source contract for the reflection-only TFC feeding adapter.
 * In-game tests are still needed to validate real TFC entity state.
 */
final class KingdomTfcFeedingParityTest {
    @Test void childFamiliarityIsNotCappedLikeAdultFamiliarity() throws Exception {
        String code=Files.readString(Path.of(
            "src/main/java/ru/projectkingdom/npc/KingdomLivestockWork.java"));
        assertTrue(code.contains("getAgeType=tfcAnimal.getMethod(\"getAgeType\")"));
        assertTrue(code.contains("boolean child=\"CHILD\".equals(String.valueOf(getAgeType.invoke(animal)))"));
        assertTrue(code.contains("if(child||familiar<cap)"),
            "Do not reduce adults already at the cap; always allow juvenile growth");
        assertTrue(code.contains("child ? familiar+0.06f"));
        assertTrue(code.contains("Math.min(cap,familiar+0.06f)"));
        assertFalse(code.contains("setFamiliarity.invoke(animal,Math.min(cap,familiar+0.06f))"),
            "Unconditional adult-cap clamping breaks native juvenile feeding");
    }
}
