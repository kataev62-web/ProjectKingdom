package ru.projectkingdom.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BookModelTest {
    private static PlayerIdentity player() {
        return PlayerIdentity.create(new IdentityIds.AccountId("account"), UUID.randomUUID());
    }

    @Test void bookAlwaysHasSixStableSections() {
        var entries = BookModel.contents(player(), new BookModel.Finance(120, 450));
        assertEquals(6, entries.size());
        assertArrayEquals(BookModel.Section.values(), entries.stream().map(BookModel.Entry::section).toArray(BookModel.Section[]::new));
    }

    @Test void walletAndCapitalAreActiveWhileFutureSystemsAreVisiblePlaceholders() {
        var entries = BookModel.contents(player(), new BookModel.Finance(120, 450));
        assertEquals(BookModel.Status.ACTIVE, entries.get(1).status());
        assertEquals("120", entries.get(1).summary());
        assertEquals(BookModel.Status.ACTIVE, entries.get(2).status());
        assertTrue(entries.subList(3, 6).stream().allMatch(entry -> entry.status() == BookModel.Status.PLACEHOLDER));
    }

    @Test void financeCannotClaimLessCapitalThanBankBalance() {
        assertThrows(IllegalArgumentException.class, () -> new BookModel.Finance(10, 9));
    }
}
