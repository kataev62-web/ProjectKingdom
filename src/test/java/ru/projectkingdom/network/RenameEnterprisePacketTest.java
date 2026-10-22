package ru.projectkingdom.network;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class RenameEnterprisePacketTest {
    @Test void acceptsTrimmedNamesUpToFortyEightCharacters(){
        assertEquals("Новая кузница",RenameEnterprisePacket.validatedName("  Новая кузница  "));
        assertEquals("я".repeat(48),RenameEnterprisePacket.validatedName("я".repeat(48)));
    }
    @Test void rejectsFormattingControlEmptyAndLongNames(){
        assertNull(RenameEnterprisePacket.validatedName(""));
        assertNull(RenameEnterprisePacket.validatedName("я".repeat(49)));
        assertNull(RenameEnterprisePacket.validatedName("§cЧужой цвет"));
        assertNull(RenameEnterprisePacket.validatedName("Строка\nвторая"));
    }
}
