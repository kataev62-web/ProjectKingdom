package ru.projectkingdom.npc;

import java.io.IOException;
import java.util.*;

/** Read-only crash-recovery classifier for a two-party physical trade.
 * NEVER mutates a Minecraft inventory. An ambiguous or unavailable projection
 * must be held for manual reconciliation, not replayed.
 */
public final class TradePhysicalProjectionAudit {
    private TradePhysicalProjectionAudit() {}
    public enum Status { BOTH_BEFORE, BOTH_AFTER, BUYER_ONLY_AFTER, SELLER_ONLY_AFTER,
        UNAVAILABLE, UNEXPECTED, UNKNOWN_ACCOUNT, INVALID_RECEIPT }
    public record Assessment(Status status, boolean safeToTrade) {}

    public static Assessment inspect(TradeAtomicEconomy economy, UUID tx,
            TradeAtomicEconomy.Account buyerBefore, TradeAtomicEconomy.Account sellerBefore,
            TradePhysicalReconciliation.PhysicalView physical) throws IOException {
        Objects.requireNonNull(economy); Objects.requireNonNull(physical);
        if (tx == null || buyerBefore == null || sellerBefore == null ||
                buyerBefore.id().equals(sellerBefore.id()))
            return new Assessment(Status.INVALID_RECEIPT, false);
        TradeAtomicEconomy.State state = economy.read();
        if (!state.completed().contains(tx))
            return new Assessment(Status.INVALID_RECEIPT, false);
        TradeAtomicEconomy.Account buyerAfter = state.accounts().get(buyerBefore.id());
        TradeAtomicEconomy.Account sellerAfter = state.accounts().get(sellerBefore.id());
        if (buyerAfter == null || sellerAfter == null)
            return new Assessment(Status.UNKNOWN_ACCOUNT, false);
        TradeAtomicEconomy.Account buyerPhysical = physical.read(buyerBefore.id());
        TradeAtomicEconomy.Account sellerPhysical = physical.read(sellerBefore.id());
        if (buyerPhysical == null || sellerPhysical == null)
            return new Assessment(Status.UNAVAILABLE, false);
        boolean bb = equal(buyerBefore, buyerPhysical), ba = equal(buyerAfter, buyerPhysical);
        boolean sb = equal(sellerBefore, sellerPhysical), sa = equal(sellerAfter, sellerPhysical);
        // A no-op/ambiguous account cannot prove that a transaction was applied.
        if ((bb && ba) || (sb && sa))
            return new Assessment(Status.UNEXPECTED, false);
        if (ba && sa) return new Assessment(Status.BOTH_AFTER, false);
        if (bb && sb) return new Assessment(Status.BOTH_BEFORE, false);
        if (ba && sb) return new Assessment(Status.BUYER_ONLY_AFTER, false);
        if (bb && sa) return new Assessment(Status.SELLER_ONLY_AFTER, false);
        return new Assessment(Status.UNEXPECTED, false);
    }
    private static boolean equal(TradeAtomicEconomy.Account a, TradeAtomicEconomy.Account b) {
        if (!a.id().equals(b.id()) || a.copper() != b.copper() || a.slots().size() != b.slots().size())
            return false;
        for (int i=0;i<a.slots().size();i++) {
            TradeAtomicEconomy.Stack x=a.slots().get(i), y=b.slots().get(i);
            if (x == null || y == null) { if (x != y) return false; }
            else if (x.count()!=y.count() || !x.sameItem(y)) return false;
        }
        return true;
    }
}
