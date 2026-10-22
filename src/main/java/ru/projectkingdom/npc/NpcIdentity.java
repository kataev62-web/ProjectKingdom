package ru.projectkingdom.npc;

import java.util.Locale;

/** Age uses 24 Minecraft days per configurable simulated year (alpha rule). */
public final class NpcIdentity {
    public static final int DAYS_PER_YEAR = 24;
    private NpcIdentity() {}
    public static String gender(String value) {
        String g = value.toLowerCase(Locale.ROOT).trim();
        if (!g.equals("male") && !g.equals("female") && !g.equals("unspecified"))
            throw new IllegalArgumentException("Gender: male|female|unspecified");
        return g;
    }
    public static int validAge(int value) {
        if (value < 0 || value > 120) throw new IllegalArgumentException("Age must be 0..120");
        return value;
    }
    public static String stage(int age) {
        if (age < 13) return "child";
        if (age < 18) return "teen";
        if (age < 65) return "adult";
        return "elder";
    }
    public static boolean canHire(int age) { return age >= 18; }
    public static boolean advance(KingdomNpcData.Worker w, long day) {
        if (day <= w.ageAnchorDay) return false;
        long passed = Math.min(2400, day - w.ageAnchorDay);
        w.ageAnchorDay = day;
        long total = w.ageDaysAccumulated + passed;
        int years = (int)(total / DAYS_PER_YEAR);
        w.ageDaysAccumulated = (int)(total % DAYS_PER_YEAR);
        w.ageYears = Math.min(120, w.ageYears + years);
        return true;
    }
}
