package ru.projectkingdom.npc;

import java.util.Objects;

/** Fail-closed decision table for recovery. A checkpoint is not itself proof
 * that Minecraft persisted its independent SavedData and chunk files.
 * Only a trusted durability barrier can set durableSaveVerified=true.
 */
public final class TradeRecoveryPolicy {
    private TradeRecoveryPolicy() {}
    public enum Observed { BEFORE, AFTER, MIXED, UNKNOWN }
    public enum Decision { NO_MUTATION_NEEDED, KEEP_LOCKED, ELIGIBLE_FOR_OPERATOR_REVIEW }

    public record Evidence(Observed observed, boolean journalIntact,
                           boolean checkpointMatches, boolean durableSaveVerified,
                           boolean sameWorldGeneration, boolean noOtherPendingTrades) {
        public Evidence { Objects.requireNonNull(observed); }
    }

    /** Never replays or reverses items or money. The only eligible result
     * still requires explicit review; it does not unlock an enterprise. */
    public static Decision evaluate(Evidence e) {
        Objects.requireNonNull(e);
        if (!e.journalIntact() || !e.sameWorldGeneration()) return Decision.KEEP_LOCKED;
        if (e.observed()==Observed.BEFORE && !e.checkpointMatches())
            return Decision.NO_MUTATION_NEEDED;
        if (e.observed()==Observed.AFTER && e.checkpointMatches()
                && e.durableSaveVerified() && e.noOtherPendingTrades())
            return Decision.ELIGIBLE_FOR_OPERATOR_REVIEW;
        return Decision.KEEP_LOCKED;
    }
}
