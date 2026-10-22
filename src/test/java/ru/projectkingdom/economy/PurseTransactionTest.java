package ru.projectkingdom.economy;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PurseTransactionTest {
    @Test void debitUsesPursesInInventoryOrder() {
        var first = new MoneyPurse(0, 1, 0, false);
        var second = new MoneyPurse(0, 2, 0, false);
        var result = PurseTransaction.debit(List.of(first, second), 150);
        assertTrue(result.committed());
        assertEquals(0, result.purses().get(0).valueCopper());
        assertEquals(150, result.purses().get(1).valueCopper());
    }

    @Test void creditUsesFirstFreePurseThenNext() {
        var first = new MoneyPurse(999, 0, 0, false);
        var second = MoneyPurse.empty();
        var result = PurseTransaction.credit(List.of(first, second), 10_001);
        assertTrue(result.committed());
        assertEquals(1000, result.purses().get(0).coinCount());
        assertEquals(1, result.purses().get(1).coinCount());
        assertEquals(11_000, result.totalValueCopper());
    }

    @Test void changeThatWouldExceedCapacityRejectsWithoutMutation() {
        var original = List.of(new MoneyPurse(0, 0, 1000, false));
        var result = PurseTransaction.debit(original, 1);
        assertFalse(result.committed());
        assertEquals(original, result.purses());
    }

    @Test void transferRollsBackBothSidesWhenReceiverHasNoCapacity() {
        var sender = List.of(new MoneyPurse(0, 1, 0, false));
        var receiver = List.of(new MoneyPurse(1000, 0, 0, false));
        var result = PurseTransaction.transfer(sender, receiver, 100);
        assertFalse(result.committed());
        assertEquals(sender, result.sender());
        assertEquals(receiver, result.receiver());
    }

    @Test void snapshotsAreImmutable() {
        var result = PurseTransaction.credit(List.of(MoneyPurse.empty()), 1);
        assertThrows(UnsupportedOperationException.class,
                () -> result.purses().add(MoneyPurse.empty()));
    }
}
