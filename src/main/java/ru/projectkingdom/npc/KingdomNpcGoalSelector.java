package ru.projectkingdom.npc;

import java.util.List;
import java.util.Objects;

/**
 * Lightweight deterministic goal selection inspired by Millenaire's goal scheduling.
 * No upstream code is copied. Selection never moves entities or creates resources.
 * Server-side callers perform the actual action after validating world state.
 */
public final class KingdomNpcGoalSelector {
    private KingdomNpcGoalSelector() {}

    public enum Goal { REST, EAT, WORK, RETURN_HOME, IDLE }

    public record Context(boolean employed, boolean contractActive, boolean hasWorkstation,
                          boolean daytime, boolean hasHome, int hunger, int energy) {
        public Context {
            if (hunger < 0 || hunger > 100 || energy < 0 || energy > 100)
                throw new IllegalArgumentException("Needs must be within 0..100");
        }
    }

    /** Survival needs preempt work; night and missing contracts cannot start production. */
    public static Goal choose(Context context) {
        Objects.requireNonNull(context, "context");
        if (context.hunger <= 25) return Goal.EAT;
        if (context.energy <= 25) return Goal.REST;
        if (context.daytime && context.employed && context.contractActive
                && context.hasWorkstation) return Goal.WORK;
        if (context.hasHome && (!context.daytime || context.energy < 50))
            return Goal.RETURN_HOME;
        return Goal.IDLE;
    }

    /** Rate limit decision-making without running expensive per-tick AI. */
    public static boolean due(long gameTick, long lastEvaluationTick, int intervalTicks) {
        if (intervalTicks < 1) throw new IllegalArgumentException("intervalTicks");
        return gameTick >= 0 && (lastEvaluationTick < 0
                || gameTick < lastEvaluationTick
                || gameTick - lastEvaluationTick >= intervalTicks);
    }
}
