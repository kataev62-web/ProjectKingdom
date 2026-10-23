package ru.projectkingdom.npc;

/**
 * Profession-independent NPC duty routing.
 *
 * All professions share schedules, contracts and navigation. Physical actions
 * are delegated to existing profession adapters, never simulated with virtual
 * items or currency. Keep these identifiers stable in saved worlds.
 */
public final class KingdomNpcDuty {
    private KingdomNpcDuty() {}

    public enum Kind { NONE, FIELD, WORKSHOP, CONSTRUCTION, DELIVERY, SERVICE }

    public static Kind kind(String job) {
        if (!KingdomProfessionRegistry.known(job)) return Kind.NONE;
        return switch (job) {
            case "free" -> Kind.NONE;
            case "farmer", "miner", "lumberjack" -> Kind.FIELD;
            case "smith", "baker", "cook", "butcher", "potter", "carpenter", "mason" -> Kind.WORKSHOP;
            case "builder" -> Kind.CONSTRUCTION;
            case "courier" -> Kind.DELIVERY;
            default -> Kind.SERVICE;
        };
    }

    /** Orders for which automatic day/night scheduling is allowed. */
    public static boolean autonomousOrder(String order) {
        return "wander".equals(order) || "work".equals(order);
    }

    /** A station is necessary for fixed-place services and crafting. */
    public static boolean needsStation(String job) {
        return kind(job) == Kind.WORKSHOP || kind(job) == Kind.SERVICE;
    }

    /** Existing real-world executors. Unsupported trades must not mint goods. */
    public static boolean hasPhysicalExecutor(String job) {
        return KingdomProfessionWorkRules.executable(job);
    }

    /** Actual actions are never run at night or during a needs-related break. */
    public static boolean mayExecute(String job, String order, String intent, boolean activeContract) {
        return activeContract && autonomousOrder(order)
            && "CONTINUE_ASSIGNED_JOB".equals(intent) && hasPhysicalExecutor(job);
    }
}
