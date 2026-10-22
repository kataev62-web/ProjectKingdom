package ru.projectkingdom.npc;

import java.util.Objects;
import java.util.UUID;

/** Single mutation entry point for enrolled managed inventories.
 * The caller must route every insertion/extraction path through this API;
 * vanilla and third-party containers are not automatically protected.
 */
public final class TradeManagedInventory {
    private final TradeInventoryMutationGuard locks;
    private final TradeProtectedLocations locations;

    public TradeManagedInventory(TradeInventoryMutationGuard locks, TradeProtectedLocations locations) {
        this.locks = Objects.requireNonNull(locks);
        this.locations = Objects.requireNonNull(locations);
    }

    public UUID owner(TradeProtectedLocations.Location location) { return locations.owner(location); }

    public boolean canMutate(TradeProtectedLocations.Location location, UUID account) {
        synchronized (locks) {
            return account != null && account.equals(locations.owner(location)) && locks.canMutate(account);
        }
    }

    public boolean register(TradeProtectedLocations.Location location, UUID owner) {
        return locations.enroll(location, owner);
    }

    /** Normal mutations are refused during a trade lease. Unknown containers fail closed. */
    public boolean mutate(TradeProtectedLocations.Location location, UUID account, Runnable action) {
        Objects.requireNonNull(action);
        synchronized (locks) {
            if (account == null || !account.equals(locations.owner(location)) || !locks.canMutate(account)) return false;
            action.run();
            return true;
        }
    }

    /** Trade coordinator may mutate only its own leased account. */
    public boolean project(TradeProtectedLocations.Location location, UUID account,
                           TradeInventoryMutationGuard.Lease lease, Runnable action) {
        Objects.requireNonNull(action);
        synchronized (locks) {
            if (account == null || lease == null || !lease.owns(account)
                    || !account.equals(locations.owner(location))) return false;
            action.run();
            return true;
        }
    }
}
