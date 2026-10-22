package ru.projectkingdom.npc;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/** Conservative, non-automatic physical projection of an already committed trade.
 * A two-container update is NOT atomic. Any interrupted/ambiguous projection
 * requires a human reconciliation; never replay from a partial state.
 */
public final class TradePhysicalProjectionCoordinator {
    private TradePhysicalProjectionCoordinator() {}
    public enum Result { APPLIED, ALREADY_APPLIED, NEEDS_MANUAL_REVIEW, UNAVAILABLE, INVALID_RECEIPT }

    /** Caller must hold exclusive server-thread ownership of BOTH containers for
     * the entire call. CAS must compare complete NBT, cash, slot counts and IDs.
     * Return false on any mismatch; throw if a chunk unloads or a write fails.
     */
    public interface PhysicalWriter extends TradePhysicalReconciliation.PhysicalView {
        boolean compareAndSet(UUID accountId, TradeAtomicEconomy.Account expected,
                              TradeAtomicEconomy.Account replacement) throws IOException;
    }

    /** Guarded entry point: reserves both enterprise inventories for the full
     * projection. External inventory handlers MUST consult the same guard.
     * No claim of crash-atomicity is made by this lock.
     */
    public static Result projectGuarded(TradeAtomicEconomy economy, UUID transaction,
            TradeAtomicEconomy.Account buyerBefore, TradeAtomicEconomy.Account sellerBefore,
            PhysicalWriter physical, TradeInventoryMutationGuard guard) throws IOException {
        Objects.requireNonNull(guard);
        if (buyerBefore == null || sellerBefore == null) return Result.INVALID_RECEIPT;
        var lease = guard.tryLock(buyerBefore.id(), sellerBefore.id());
        if (lease == null) return Result.UNAVAILABLE;
        try (lease) {
            return project(economy, transaction, buyerBefore, sellerBefore, physical);
        }
    }

    /** Durable projection boundary. A pending marker survives a crash and blocks
     * automatic replay; an operator must reconcile partial states. */
    public static Result projectDurableGuarded(TradeAtomicEconomy economy, UUID transaction,
            TradeAtomicEconomy.Account buyerBefore, TradeAtomicEconomy.Account sellerBefore,
            PhysicalWriter physical, TradeInventoryMutationGuard guard,
            TradeProjectionQuarantine quarantine) throws IOException {
        Objects.requireNonNull(quarantine);
        if (transaction == null || buyerBefore == null || sellerBefore == null ||
                buyerBefore.id().equals(sellerBefore.id())) return Result.INVALID_RECEIPT;
        if (quarantine.isQuarantined(transaction)) return Result.NEEDS_MANUAL_REVIEW;
        var lease = guard.tryLock(buyerBefore.id(), sellerBefore.id());
        if (lease == null) return Result.UNAVAILABLE;
        try (lease) {
            // Inspect before writing: do not leave a pending marker for invalid receipts.
            var audit = TradePhysicalProjectionAudit.inspect(economy, transaction,
                    buyerBefore, sellerBefore, physical);
            if (audit.status() == TradePhysicalProjectionAudit.Status.BOTH_AFTER) return Result.ALREADY_APPLIED;
            if (audit.status() == TradePhysicalProjectionAudit.Status.UNAVAILABLE) return Result.UNAVAILABLE;
            if (audit.status() == TradePhysicalProjectionAudit.Status.INVALID_RECEIPT) return Result.INVALID_RECEIPT;
            if (audit.status() != TradePhysicalProjectionAudit.Status.BOTH_BEFORE) return Result.NEEDS_MANUAL_REVIEW;
            if (!quarantine.begin(transaction, buyerBefore.id(), sellerBefore.id()))
                return Result.NEEDS_MANUAL_REVIEW;
            // Any error or ambiguous state leaves the durable marker in place.
            Result result = project(economy, transaction, buyerBefore, sellerBefore, physical);
            if (result == Result.APPLIED || result == Result.ALREADY_APPLIED) {
                var verified = TradePhysicalProjectionAudit.inspect(economy, transaction,
                        buyerBefore, sellerBefore, physical);
                if (verified.status() == TradePhysicalProjectionAudit.Status.BOTH_AFTER) {
                    quarantine.verifiedComplete(transaction);
                    return result;
                }
                return Result.NEEDS_MANUAL_REVIEW;
            }
            return result;
        }
    }

    // Serialize projections in this JVM: the read-check-write sequence spans two
    // physical inventories and must not interleave with another coordinator call.
    // Minecraft inventory mutations outside this coordinator still require a
    // server-thread access policy; this is not disk-level atomicity.
    public static synchronized Result project(TradeAtomicEconomy economy, UUID transaction,
            TradeAtomicEconomy.Account buyerBefore, TradeAtomicEconomy.Account sellerBefore,
            PhysicalWriter physical) throws IOException {
        Objects.requireNonNull(economy);
        Objects.requireNonNull(physical);
        if (transaction == null || buyerBefore == null || sellerBefore == null ||
                buyerBefore.id().equals(sellerBefore.id())) return Result.INVALID_RECEIPT;
        var assessment = TradePhysicalProjectionAudit.inspect(economy, transaction,
                buyerBefore, sellerBefore, physical);
        if (assessment.status() == TradePhysicalProjectionAudit.Status.BOTH_AFTER)
            return Result.ALREADY_APPLIED;
        if (assessment.status() == TradePhysicalProjectionAudit.Status.UNAVAILABLE)
            return Result.UNAVAILABLE;
        if (assessment.status() == TradePhysicalProjectionAudit.Status.INVALID_RECEIPT)
            return Result.INVALID_RECEIPT;
        if (assessment.status() != TradePhysicalProjectionAudit.Status.BOTH_BEFORE)
            return Result.NEEDS_MANUAL_REVIEW;
        var snapshot = economy.read();
        // Abort if the receipt or accounts changed between inspection and projection.
        if (!snapshot.completed().contains(transaction)) return Result.INVALID_RECEIPT;
        var buyerAfter = snapshot.accounts().get(buyerBefore.id());
        var sellerAfter = snapshot.accounts().get(sellerBefore.id());
        if (buyerAfter == null || sellerAfter == null) return Result.NEEDS_MANUAL_REVIEW;
        // Deliberately no automatic rollback on failure: it could overwrite changes.
        if (!physical.compareAndSet(buyerBefore.id(), buyerBefore, buyerAfter))
            return Result.NEEDS_MANUAL_REVIEW;
        if (!physical.compareAndSet(sellerBefore.id(), sellerBefore, sellerAfter))
            return Result.NEEDS_MANUAL_REVIEW;
        return Result.APPLIED;
    }
}
