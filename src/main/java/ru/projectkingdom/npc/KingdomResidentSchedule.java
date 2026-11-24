package ru.projectkingdom.npc;

import java.util.UUID;

/**
 * The schedule of the day of a resident.
 *
 * <p>A resident of MCA lives its ordinary life and works for the Kingdom in
 * the same day. The schedule decides who owns the resident at every hour:
 *
 * <ul>
 *   <li>06:00-08:00 — the ordinary life of MCA;</li>
 *   <li>08:00-12:00 — the work of the Kingdom;</li>
 *   <li>12:00-13:00 — the rest;</li>
 *   <li>13:00-18:00 — the work of the Kingdom;</li>
 *   <li>18:00-06:00 — the ordinary life of MCA, the sleep included.</li>
 * </ul>
 *
 * <p>A resident without a contract lives its life of MCA the whole day. A
 * resident with a complex order of the planner works on it instead of the
 * ordinary work. Danger always wins: the resident may run away.
 */
public final class KingdomResidentSchedule {
    private KingdomResidentSchedule() {}

    /** Who owns the resident at this moment. */
    public enum Mode {
        /** The ordinary life of MCA: the schedule, the family, the bed. */
        MCA_LIFE,
        /** The work of the Kingdom: the contract, the tools, the goods. */
        KINGDOM_WORK,
        /** The rest of the Kingdom: the tavern, the meal, the bed. */
        KINGDOM_REST,
        /** A complex order of the planner. */
        PLAYER2_TASK,
        /** Danger: the resident may run away and must not be held. */
        EMERGENCY
    }

    /** The hour of the day in the ticks of Minecraft. */
    public static int tickOfHour(int hour) {
        int wrapped = ((hour - 6) % 24 + 24) % 24;
        return wrapped * 1000;
    }

    /** The hour of the day of a tick of Minecraft. */
    public static int hourOfTick(long dayTime) {
        int tick = (int) Math.floorMod(dayTime, 24000L);
        return (tick / 1000 + 6) % 24;
    }

    /**
     * The mode of a resident at the given time of the day.
     *
     * @param dayTime the time of the day in the ticks of Minecraft.
     * @param employed whether the resident has a contract of the Kingdom.
     * @param task whether a complex order of the planner is waiting.
     * @param emergency whether the resident is in danger.
     */
    public static Mode modeOf(long dayTime, boolean employed, boolean task, boolean emergency) {
        if (emergency) return Mode.EMERGENCY;
        if (task) return Mode.PLAYER2_TASK;
        int tick = (int) Math.floorMod(dayTime, 24000L);
        // 06:00-08:00: the ordinary life of MCA.
        if (tick < tickOfHour(8)) return Mode.MCA_LIFE;
        // 08:00-12:00: the work of the Kingdom.
        if (tick < tickOfHour(12)) return employed ? Mode.KINGDOM_WORK : Mode.MCA_LIFE;
        // 12:00-13:00: the rest.
        if (tick < tickOfHour(13)) return employed ? Mode.KINGDOM_REST : Mode.MCA_LIFE;
        // 13:00-18:00: the work of the Kingdom.
        if (tick < tickOfHour(18)) return employed ? Mode.KINGDOM_WORK : Mode.MCA_LIFE;
        // 18:00-06:00: the ordinary life of MCA, the sleep included.
        return Mode.MCA_LIFE;
    }

    /** Whether the Kingdom owns the navigation of the resident in this mode. */
    public static boolean kingdomOwns(Mode mode) {
        return mode == Mode.KINGDOM_WORK || mode == Mode.KINGDOM_REST || mode == Mode.PLAYER2_TASK;
    }

    /** Whether the resident must work in this mode. */
    public static boolean working(Mode mode) {
        return mode == Mode.KINGDOM_WORK || mode == Mode.PLAYER2_TASK;
    }

    /**
     * Whether the resident may work right now.
     *
     * <p>Every production executor asks this question first: during the rest
     * of the Kingdom the resident only lives its ordinary life.
     */
    public static boolean working(net.minecraft.server.MinecraftServer server,
                                  KingdomNpcData.Worker worker) {
        if (server == null || worker == null) return false;
        return working(modeOf(server, worker.entityId, worker));
    }

    /** Whether the resident may do its ordinary life of MCA in this mode. */
    public static boolean mcaLife(Mode mode) {
        return mode == Mode.MCA_LIFE || mode == Mode.EMERGENCY;
    }

    /**
     * The mode of a resident.
     *
     * <p>The contract of the Kingdom decides whether the resident works at
     * all, the planner decides whether a complex order waits and the danger
     * decides whether the resident may run away.
     */
    public static Mode modeOf(net.minecraft.server.MinecraftServer server, UUID resident,
                              KingdomNpcData.Worker worker) {
        if (server == null || worker == null) return Mode.MCA_LIFE;
        boolean employed = KingdomNpcWorkday.employed(server, worker);
        // A complex order of the planner is a real task of the planner, not
        // the ordinary intention of looking for work.
        boolean task = worker != null && "PLAYER2_TASK".equals(worker.autonomousIntent);
        boolean emergency = worker != null && worker.energy <= 0;
        return modeOf(server.overworld().getDayTime(), employed, task, emergency);
    }

    /** The name of a mode for the operator. */
    public static String describe(Mode mode) {
        if (mode == null) return "—";
        return switch (mode) {
            case MCA_LIFE -> "жизнь MCA";
            case KINGDOM_WORK -> "работа Kingdom";
            case KINGDOM_REST -> "отдых";
            case PLAYER2_TASK -> "поручение Player2";
            case EMERGENCY -> "опасность";
        };
    }
}
