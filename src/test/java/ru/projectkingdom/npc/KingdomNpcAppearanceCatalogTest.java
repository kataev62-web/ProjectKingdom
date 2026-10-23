package ru.projectkingdom.npc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomNpcAppearanceCatalogTest {
    @Test void excludesUnderageAndNonFemale() {
        assertEquals("", KingdomNpcAppearanceCatalog.select("female", 16, "baker", "auto", 0));
        assertEquals("", KingdomNpcAppearanceCatalog.select("male", 25, "baker", "auto", 0));
        assertEquals("", KingdomNpcAppearanceCatalog.select("unspecified", 25, "baker", "auto", 0));
    }
    @Test void selectsAdultRolesAndElder() {
        assertEquals("03_pekarnya", KingdomNpcAppearanceCatalog.select("female", 28, "baker", "auto", 3));
        assertEquals("08_lekar", KingdomNpcAppearanceCatalog.select("female", 38, "medic", "auto", 3));
        assertEquals("11_pozhilaya", KingdomNpcAppearanceCatalog.select("female", 70, "medic", "auto", 3));
    }
    @Test void validatesOverridesAndIds() {
        assertTrue(KingdomNpcAppearanceCatalog.allowed("auto"));
        assertTrue(KingdomNpcAppearanceCatalog.allowed("12_razboynitsa"));
        assertFalse(KingdomNpcAppearanceCatalog.allowed("../../malicious"));
        assertEquals("12_razboynitsa", KingdomNpcAppearanceCatalog.select("female", 25, "free", "12_razboynitsa", 5));
        assertEquals("", KingdomNpcAppearanceCatalog.select("female", 10, "free", "12_razboynitsa", 5));
    }
}
