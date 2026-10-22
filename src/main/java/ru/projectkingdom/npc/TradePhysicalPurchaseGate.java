package ru.projectkingdom.npc;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Fail-closed preflight for migration to canonical economy snapshots.
 * IMPORTANT: This does NOT apply the transaction to Minecraft containers.
 * A successful commit requires a separate, verified projection protocol before
 * either account can be allowed to trade again. Call on the server thread only.
 */
public final class TradePhysicalPurchaseGate {
    private TradePhysicalPurchaseGate() {}
    public enum Result {
        COMMITTED_REQUIRES_PROJECTION, ALREADY_COMMITTED,
        UNAVAILABLE, MISMATCH, UNKNOWN_ACCOUNT, INVALID,
        INSUFFICIENT_FUNDS, INSUFFICIENT_STOCK, NO_SPACE, STALE_VIEW
    }
    public static Result purchase(TradeAtomicEconomy economy, UUID transaction,
            UUID buyer, UUID seller, int sourceSlot, int count, long unitPrice,
            TradePhysicalReconciliation.PhysicalView physical) throws IOException {
        Objects.requireNonNull(economy);
        Objects.requireNonNull(physical);
        if (transaction == null || buyer == null || seller == null || buyer.equals(seller)) return Result.INVALID;
        // Idempotency must take precedence over checking stale physical projections.
        var snapshot = economy.read();
        if (snapshot.completed().contains(transaction)) return Result.ALREADY_COMMITTED;
        var inspection = TradePhysicalReconciliation.inspectSnapshot(snapshot, List.of(buyer, seller), physical);
        if (inspection != TradePhysicalReconciliation.Result.MATCH) return switch (inspection) {
            case UNAVAILABLE -> Result.UNAVAILABLE;
            case MISMATCH -> Result.MISMATCH;
            case UNKNOWN_ACCOUNT -> Result.UNKNOWN_ACCOUNT;
            case MATCH -> throw new IllegalStateException();
        };
        // Revalidation and concurrent trade serialization are handled by purchase().
        return switch (economy.purchaseAtGeneration(transaction, buyer, seller, sourceSlot, count, unitPrice, snapshot.generation())) {
            case COMMITTED -> Result.COMMITTED_REQUIRES_PROJECTION;
            case ALREADY_COMMITTED -> Result.ALREADY_COMMITTED;
            case INVALID -> Result.INVALID;
            case INSUFFICIENT_FUNDS -> Result.INSUFFICIENT_FUNDS;
            case INSUFFICIENT_STOCK -> Result.INSUFFICIENT_STOCK;
            case NO_SPACE -> Result.NO_SPACE;
            case STALE_VIEW -> Result.STALE_VIEW;
        };
    }
}
