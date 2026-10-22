package ru.projectkingdom.npc;

/** Pure server-side simulation rules; work produces virtual stock, never free money. */
public final class NpcSimulation {
    private NpcSimulation() {}
    public static String normalizeOrder(String input) {
        String s = input.toLowerCase(java.util.Locale.ROOT);
        return switch (s) {
            case "wander", "stay", "follow", "work", "home", "haul" -> s;
            default -> throw new IllegalArgumentException("Unknown order: " + input);
        };
    }
    public static long production(String job, long days, boolean paid) {
        if (days < 0 || days > 30) throw new IllegalArgumentException("Days outside 0..30");
        if (!paid || job.equals("free") || job.equals("guard")) return 0;
        return Math.multiplyExact(days, switch(job) {
            case "farmer" -> 4; case "miner" -> 2; case "lumberjack" -> 3;
            case "smith" -> 1; case "merchant" -> 1; default -> 0;
        });
    }
    public static long elapsed(long now, long previous) { return Math.max(0, Math.min(30, now-previous)); }
}
