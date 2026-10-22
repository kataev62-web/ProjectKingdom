package ru.projectkingdom.npc;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Server-side fail-closed guard for enterprise inventory mutations.
 * Call canMutate from ALL player/NPC/container mutation entry points.
 * This is a runtime lock, not a durable crash-recovery mechanism.
 */
public final class TradeInventoryMutationGuard {
    private final Set<UUID> locked = new HashSet<>();

    public synchronized boolean canMutate(UUID account) {
        return account != null && !locked.contains(account);
    }

    public synchronized Lease tryLock(UUID buyer, UUID seller) {
        if (buyer == null || seller == null || buyer.equals(seller)
                || locked.contains(buyer) || locked.contains(seller)) return null;
        locked.add(buyer);
        locked.add(seller);
        return new Lease(buyer, seller);
    }

    public Lease lock(UUID buyer, UUID seller) {
        Lease lease = tryLock(buyer, seller);
        if (lease == null) throw new IllegalStateException("enterprise inventories unavailable");
        return lease;
    }

    public final class Lease implements AutoCloseable {
        private final UUID buyer, seller;
        private boolean closed;
        private Lease(UUID buyer, UUID seller) { this.buyer = buyer; this.seller = seller; }
        public boolean owns(UUID id) { return !closed && (buyer.equals(id) || seller.equals(id)); }
        @Override public void close() {
            synchronized (TradeInventoryMutationGuard.this) {
                if (!closed) {
                    locked.remove(buyer);
                    locked.remove(seller);
                    closed = true;
                }
            }
        }
    }
}
