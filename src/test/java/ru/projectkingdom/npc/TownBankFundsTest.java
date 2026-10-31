package ru.projectkingdom.npc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TownBankFundsTest {
    @Test void privateAndTownFundsStaySeparatelyOwnedAndPhysicallyBacked() {
        assertDoesNotThrow(()->TownBankFundsHarness.main(new String[0]));
    }
}
