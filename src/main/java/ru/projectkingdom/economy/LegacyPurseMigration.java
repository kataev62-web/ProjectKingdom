package ru.projectkingdom.economy;

/** Lossless migration from the former copper-value-only wallet NBT field. */
public final class LegacyPurseMigration {
    private LegacyPurseMigration() {}

    public static MoneyPurse fromCopperValue(long copperValue) {
        if (copperValue < 0) throw new IllegalArgumentException("Legacy balance cannot be negative");
        long gold = copperValue / MoneyDenomination.GOLD.copperValue();
        long remainder = copperValue % MoneyDenomination.GOLD.copperValue();
        long silver = remainder / MoneyDenomination.SILVER.copperValue();
        long copper = remainder % MoneyDenomination.SILVER.copperValue();
        long coins = Math.addExact(Math.addExact(copper, silver), gold);
        return new MoneyPurse(copper, silver, gold, coins > MoneyPurse.MAX_COINS);
    }
}
