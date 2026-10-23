package ru.projectkingdom.npc;
import java.util.List;
/** Central appearance ID validation and policy. Does not depend on client-only classes. */
public final class KingdomNpcAppearanceCatalog {
    public static final List<String> MODELS = List.of(
            "01_gorozhanka", "02_torgovka", "03_pekarnya", "04_traktirshchitsa",
            "05_shveya", "06_goncharka", "07_znakharka", "08_lekar",
            "09_znatnaya", "10_strazzh", "11_pozhilaya", "12_razboynitsa");
    private KingdomNpcAppearanceCatalog() {}
    public static boolean allowed(String id) { return "auto".equals(id) || MODELS.contains(id); }
    public static String select(String gender, int age, String job, String override, int seed) {
        if (!"female".equals(gender) || age < 18) return "";
        if (age >= 65) return "11_pozhilaya";
        if (MODELS.contains(override)) return override;
        if (job == null) return "01_gorozhanka";
        return switch (job) {
            case "merchant" -> "02_torgovka";
            case "baker" -> "03_pekarnya";
            case "cook", "innkeeper" -> "04_traktirshchitsa";
            case "weaver" -> "05_shveya";
            case "potter" -> "06_goncharka";
            case "breeder", "farmer", "hunter" -> "07_znakharka";
            case "medic" -> "08_lekar";
            case "guard" -> "10_strazzh";
            default -> (seed & 3) == 0 ? "09_znatnaya" : "01_gorozhanka";
        };
    }
}
