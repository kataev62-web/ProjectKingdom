package ru.projectkingdom.economy;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class MoneyPurseNbtTest {
    @Test void migratesLegacyBalanceAndWritesVersionedCoinCounts() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("KingdomCopperBalance", 12_345);
        MoneyPurse purse = MoneyPurseNbt.readMigrating(tag);
        assertEquals(12_345, purse.valueCopper());
        assertEquals(1, purse.gold());
        assertEquals(23, purse.silver());
        assertEquals(45, purse.copper());
        assertEquals(1, tag.getInt(MoneyPurseNbt.VERSION));
        assertFalse(tag.contains("KingdomCopperBalance"));
    }

    @Test void emptyPurseUsesSixteenAndFilledPurseUsesOneAsStackLimit() {
        assertEquals(16, MoneyPurseInventory.maxStackSize(MoneyPurse.empty()));
        assertEquals(1, MoneyPurseInventory.maxStackSize(
                MoneyPurse.empty().add(MoneyDenomination.COPPER, 1)));
    }
}
