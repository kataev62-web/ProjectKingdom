package ru.projectkingdom.npc;

import ru.projectkingdom.enterprise.EnterpriseKind;

/**
 * Exact server-side work permissions. Registering a profession never creates an executor.
 * Crafting uses only real 3x3 recipes; TFC devices have their own adapters.
 */
public final class KingdomProfessionWorkRules {
    private KingdomProfessionWorkRules() {}

    public static boolean crafting(String job, EnterpriseKind kind) {
        if (job == null || kind == null) return false;
        return switch (job) {
            case "smith" -> kind == EnterpriseKind.SMITHY;
            case "baker", "cook", "butcher" -> kind == EnterpriseKind.BAKERY;
            case "potter" -> kind == EnterpriseKind.POTTERY;
            case "carpenter", "mason" -> kind == EnterpriseKind.CONSTRUCTION;
            default -> false;
        };
    }

    public static boolean pot(String job, EnterpriseKind kind) {
        return kind == EnterpriseKind.BAKERY
            && ("cook".equals(job)||"baker".equals(job)||"butcher".equals(job));
    }
    public static boolean grill(String job, EnterpriseKind kind) {
        return kind == EnterpriseKind.BAKERY && ("baker".equals(job)
            || "cook".equals(job) || "butcher".equals(job));
    }

    public static boolean tanning(String job, EnterpriseKind kind) {
        return "tanner".equals(job) && kind == EnterpriseKind.TANNERY;
    }
    public static boolean loom(String job, EnterpriseKind kind) {
        return "weaver".equals(job) && kind == EnterpriseKind.TEXTILE;
    }
    public static boolean quern(String job, EnterpriseKind kind) {
        return "baker".equals(job) && kind == EnterpriseKind.BAKERY;
    }

    /** Only list real server-side executors, not professions with an idle work schedule. */
    public static boolean executable(String job) {
        return switch (job == null ? "" : job) {
            case "farmer", "breeder", "miner", "lumberjack", "builder", "courier",
                 "smith", "baker", "cook", "butcher", "potter", "carpenter", "mason", "weaver", "tanner" -> true;
            default -> false;
        };
    }
}
