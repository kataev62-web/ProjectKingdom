package ru.projectkingdom.npc;

/** Pure deterministic policy. Plans tasks without inventing output or stealing inventory.
 * Execution requires explicit adapter to an existing real-world block/mod recipe. */
public final class NpcAutonomyPlanner {
    private NpcAutonomyPlanner() {}
    public enum Intention { REST, EAT, SEEK_SHELTER, FIND_WORK, REPORT_BLOCKED, CONTINUE_ASSIGNED_JOB, ON_DUTY, IDLE }
    public record Snapshot(boolean employed, boolean contractPaused, int hunger, int energy, int safety,
                           boolean hasHome, boolean hasWorkstation, boolean hasInput, boolean hasOutput,
                           boolean onAuthorizedLand, boolean realRecipeAdapter) {}
    public static Intention plan(Snapshot s) {
        if (s.safety() < 25) return Intention.SEEK_SHELTER;
        if (s.hunger() < 30) return Intention.EAT;
        if (s.energy() < 25) return Intention.REST;
        if (!s.employed()) return s.hasHome() ? Intention.FIND_WORK : Intention.SEEK_SHELTER;
        if (s.contractPaused() || !s.onAuthorizedLand()) return Intention.REPORT_BLOCKED;
        if (!s.hasWorkstation() || !s.hasInput() || !s.hasOutput() || !s.realRecipeAdapter()) return Intention.REPORT_BLOCKED;
        return Intention.CONTINUE_ASSIGNED_JOB;
    }
    public static String description(Intention i) {
        return switch(i) {
            case REST -> "Нужен отдых";
            case EAT -> "Нужно питание";
            case SEEK_SHELTER -> "Ищет безопасное жильё";
            case FIND_WORK -> "Ищет работу и торговлю";
            case REPORT_BLOCKED -> "Работа заблокирована: проверь права, склады и технологию";
            case CONTINUE_ASSIGNED_JOB -> "Может приступать к настоящему процессу";
            case ON_DUTY -> "На рабочем месте (действия профессии ещё не подключены)";
            case IDLE -> "Свободное время";
        };
    }
}
