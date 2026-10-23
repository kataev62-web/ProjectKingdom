package ru.projectkingdom.npc;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class KingdomProfessionRegistryTest {
    @Test void identifiersAreUniqueAndPersistedNamesRemainStable() {
        var professions = KingdomProfessionRegistry.all();
        assertEquals(10, professions.size());
        assertEquals(professions.size(), new HashSet<>(professions.stream().map(KingdomProfessionRegistry.Profession::id).toList()).size());
        assertEquals("free", professions.get(0).id());
        assertEquals("farmer", KingdomProfessionRegistry.next("free"));
        assertEquals("free", KingdomProfessionRegistry.next("courier"));
        assertEquals("free", KingdomProfessionRegistry.next("unknown"));
    }

    @Test void displayNamesAndProductionClassification() {
        assertEquals("Кузнец", KingdomProfessionRegistry.title("smith"));
        assertEquals("Без профессии", KingdomProfessionRegistry.title("unknown"));
        assertTrue(KingdomProfessionRegistry.productive("builder"));
        assertFalse(KingdomProfessionRegistry.productive("merchant"));
        assertFalse(KingdomProfessionRegistry.known(null));
    }
}
