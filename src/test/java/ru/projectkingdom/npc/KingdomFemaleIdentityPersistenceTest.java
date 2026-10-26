package ru.projectkingdom.npc;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Female identity is persistent server state, not a transient skin toggle. */
final class KingdomFemaleIdentityPersistenceTest {
    @Test void genderCycleUsesCanonicalValues() {
        assertEquals("female", NpcIdentity.nextGender("unspecified"));
        assertEquals("male", NpcIdentity.nextGender("female"));
        assertEquals("unspecified", NpcIdentity.nextGender("male"));
    }

    @Test void nbtSaveAndLoadPreserveFemaleIdentityAndAge() {
        var original = new KingdomNpcData.Worker(
                102, UUID.fromString("c2b9e3aa-388b-415f-94c8-85b521a89ba0"),
                "Горожанка", "minecraft:overworld", 0);
        original.gender = "female";
        original.ageYears = 32;
        var recovered = KingdomNpcData.Worker.load(original.save());
        assertEquals("female", recovered.gender);
        assertEquals(32, recovered.ageYears);
        assertTrue(KingdomFemaleOutfitSync.eligible(recovered));
    }

    @Test void oldSavesDoNotBecomeWomenByAccident() {
        var original = new KingdomNpcData.Worker(
                103, UUID.fromString("37706e29-5f62-49c4-9b84-c54723194811"),
                "Житель", "minecraft:overworld", 0);
        var oldTag = original.save();
        oldTag.remove("Gender");
        var recovered = KingdomNpcData.Worker.load(oldTag);
        assertEquals("unspecified", recovered.gender);
        assertFalse(KingdomFemaleOutfitSync.eligible(recovered));
    }
}
