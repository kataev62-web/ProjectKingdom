package ru.projectkingdom.npc;

/** Chooses an exact sum from existing coin denominations; it never makes change. */
final class PhysicalCoinSelection {
    private static final long MAX_OPERATION = 1_000_000L;

    record Coins(long gold, long silver, long copper) {
        Coins {
            if (gold < 0 || silver < 0 || copper < 0)
                throw new IllegalArgumentException("Negative physical coin count");
        }
        long copperValue() {
            return Math.addExact(Math.addExact(Math.multiplyExact(gold, 10_000L),
                    Math.multiplyExact(silver, 100L)), copper);
        }
    }

    private PhysicalCoinSelection() {}

    static Coins select(long amount, long goldAvailable, long silverAvailable, long copperAvailable) {
        if (amount < 1 || amount > MAX_OPERATION || goldAvailable < 0
                || silverAvailable < 0 || copperAvailable < 0) return null;
        long maxGold = Math.min(goldAvailable, amount / 10_000L);
        for (long gold = maxGold; gold >= 0; gold--) {
            long afterGold = amount - gold * 10_000L;
            long maxSilver = Math.min(silverAvailable, afterGold / 100L);
            long minSilver = afterGold <= copperAvailable ? 0
                    : 1 + (afterGold - copperAvailable - 1) / 100L;
            if (minSilver > maxSilver) continue;
            long silver = maxSilver;
            long copper = afterGold - silver * 100L;
            return new Coins(gold, silver, copper);
        }
        return null;
    }
}
