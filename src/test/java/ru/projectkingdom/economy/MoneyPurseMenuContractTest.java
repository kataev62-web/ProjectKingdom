package ru.projectkingdom.economy;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class MoneyPurseMenuContractTest {
    @Test void menuActionsRemainStableForServerButtons() {
        assertEquals(0, MoneyPurseMenu.COLLECT);
        assertEquals(1, MoneyPurseMenu.WITHDRAW_COPPER);
        assertEquals(2, MoneyPurseMenu.WITHDRAW_SILVER);
        assertEquals(3, MoneyPurseMenu.WITHDRAW_GOLD);
    }
}
