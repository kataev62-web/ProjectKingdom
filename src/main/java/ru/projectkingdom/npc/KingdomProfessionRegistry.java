package ru.projectkingdom.npc;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonical NPC profession identifiers used by contracts, menus and work planners.
 * Identifiers are stable save-data values; do not rename them without migration.
 */
public final class KingdomProfessionRegistry {
    private KingdomProfessionRegistry() {}

    public record Profession(String id, String title, boolean productive) {}

    private static final List<Profession> PROFESSIONS = List.of(
        new Profession("free", "Без профессии", false),
        new Profession("farmer", "Фермер", true),
        new Profession("smith", "Кузнец", true),
        new Profession("miner", "Шахтёр", true),
        new Profession("lumberjack", "Лесоруб", true),
        new Profession("merchant", "Купец", false),
        new Profession("guard", "Стражник", false),
        new Profession("builder", "Строитель", true),
        new Profession("warehouse", "Кладовщик", false),
        new Profession("courier", "Курьер", false)
    );

    private static final Map<String, Profession> BY_ID = PROFESSIONS.stream()
        .collect(java.util.stream.Collectors.toUnmodifiableMap(Profession::id, p -> p));

    public static List<Profession> all() { return PROFESSIONS; }

    public static boolean known(String id) { return id != null && BY_ID.containsKey(id); }

    public static String title(String id) {
        Profession profession = BY_ID.get(id);
        return profession == null ? "Без профессии" : profession.title();
    }

    public static String next(String current) {
        for (int i = 0; i < PROFESSIONS.size(); i++) {
            if (PROFESSIONS.get(i).id().equals(current)) {
                return PROFESSIONS.get((i + 1) % PROFESSIONS.size()).id();
            }
        }
        return PROFESSIONS.get(0).id();
    }

    public static boolean productive(String id) {
        Profession profession = BY_ID.get(id);
        return profession != null && profession.productive();
    }
}
