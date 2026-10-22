package ru.projectkingdom.economy;

/** Immutable contents of one physical purse. Counts are coins, not copper value. */
public record MoneyPurse(long copper, long silver, long gold, boolean legacyOverflow) {
    public static final long MAX_COINS = 1000;

    public MoneyPurse {
        if (copper < 0 || silver < 0 || gold < 0) {
            throw new IllegalArgumentException("Coin counts cannot be negative");
        }
        long count = Math.addExact(Math.addExact(copper, silver), gold);
        if (count > MAX_COINS && !legacyOverflow) {
            throw new IllegalArgumentException("A normal purse holds at most " + MAX_COINS + " coins");
        }
    }

    public static MoneyPurse empty() {
        return new MoneyPurse(0, 0, 0, false);
    }

    public long coinCount() {
        return Math.addExact(Math.addExact(copper, silver), gold);
    }

    public long valueCopper() {
        long silverValue = Math.multiplyExact(silver, MoneyDenomination.SILVER.copperValue());
        long goldValue = Math.multiplyExact(gold, MoneyDenomination.GOLD.copperValue());
        return Math.addExact(copper, Math.addExact(silverValue, goldValue));
    }

    public long count(MoneyDenomination denomination) {
        return switch (denomination) {
            case COPPER -> copper;
            case SILVER -> silver;
            case GOLD -> gold;
        };
    }

    public boolean canAccept(MoneyDenomination denomination, long amount) {
        if (denomination == null || amount <= 0 || legacyOverflow) return false;
        try {
            return Math.addExact(coinCount(), amount) <= MAX_COINS;
        } catch (ArithmeticException overflow) {
            return false;
        }
    }

    public MoneyPurse add(MoneyDenomination denomination, long amount) {
        if (!canAccept(denomination, amount)) throw new IllegalArgumentException("Purse capacity exceeded");
        return withCount(denomination, Math.addExact(count(denomination), amount), false);
    }

    public MoneyPurse remove(MoneyDenomination denomination, long amount) {
        if (denomination == null || amount <= 0 || count(denomination) < amount) {
            throw new IllegalArgumentException("Not enough coins");
        }
        MoneyPurse result = withCount(denomination, count(denomination) - amount, legacyOverflow);
        if (result.coinCount() <= MAX_COINS && result.legacyOverflow()) {
            return new MoneyPurse(result.copper(), result.silver(), result.gold(), false);
        }
        return result;
    }

    private MoneyPurse withCount(MoneyDenomination denomination, long amount, boolean overflow) {
        return switch (denomination) {
            case COPPER -> new MoneyPurse(amount, silver, gold, overflow);
            case SILVER -> new MoneyPurse(copper, amount, gold, overflow);
            case GOLD -> new MoneyPurse(copper, silver, amount, overflow);
        };
    }
}
