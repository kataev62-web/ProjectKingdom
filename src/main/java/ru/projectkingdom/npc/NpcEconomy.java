package ru.projectkingdom.npc;

/** Pure domain logic. Pure calculations for legacy NPC tasks; new payroll uses copper coin units. */
public final class NpcEconomy {
    private NpcEconomy() {}

    public static long dailyWage(long dailyRate, long elapsedDays) {
        if (dailyRate < 0 || elapsedDays < 0) throw new IllegalArgumentException("Negative value");
        return Math.multiplyExact(dailyRate, elapsedDays);
    }

    public static long payableDays(long availableFunds, long dailyRate, long elapsedDays) {
        if (availableFunds < 0 || dailyRate <= 0 || elapsedDays < 0) throw new IllegalArgumentException("Invalid inputs");
        return Math.min(elapsedDays, availableFunds / dailyRate);
    }

    public static String normalizeJob(String job) {
        String value = job.toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "farmer", "smith", "miner", "lumberjack", "merchant", "guard", "builder", "courier", "warehouse", "free" -> value;
            default -> throw new IllegalArgumentException("Unknown job: " + job);
        };
    }
}
