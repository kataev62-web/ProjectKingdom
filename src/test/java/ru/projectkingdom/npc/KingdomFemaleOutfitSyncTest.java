package ru.projectkingdom.npc;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Only adult female workers may receive the visual overlay, regardless of profession. */
class KingdomFemaleOutfitSyncTest {
    private static KingdomNpcData.Worker sample() {
        return new KingdomNpcData.Worker(1, UUID.randomUUID(), "NPC", "minecraft:overworld", 0);
    }
    @Test void adultFemaleQualifies() {
        var worker = sample();
        worker.gender = "female";
        worker.ageYears = 18;
        assertTrue(KingdomFemaleOutfitSync.eligible(worker));
        worker.ageYears = 65;
        assertTrue(KingdomFemaleOutfitSync.eligible(worker));
    }
    @Test void noFemaleClothingOnChildrenOrOtherGenders() {
        var worker = sample();
        worker.gender = "female";
        worker.ageYears = 17;
        assertFalse(KingdomFemaleOutfitSync.eligible(worker));
        worker.ageYears = 28;
        worker.gender = "male";
        assertFalse(KingdomFemaleOutfitSync.eligible(worker));
        worker.gender = "unspecified";
        assertFalse(KingdomFemaleOutfitSync.eligible(worker));
    }
}
