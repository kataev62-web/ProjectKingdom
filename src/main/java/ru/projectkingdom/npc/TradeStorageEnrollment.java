package ru.projectkingdom.npc;

import java.util.Objects;
import java.util.UUID;

/** Rebuilds runtime container ownership from the persisted block-entity owner.
 * Unbound, conflicting and locked locations fail closed for automation.
 */
public final class TradeStorageEnrollment {
    private final TradeManagedInventory managed;
    private final TradeProtectedLocations locations;
    public TradeStorageEnrollment(TradeManagedInventory managed, TradeProtectedLocations locations) {
        this.managed = Objects.requireNonNull(managed);
        this.locations = Objects.requireNonNull(locations);
    }
    public boolean restore(TradeProtectedLocations.Location location, UUID persistedOwner) {
        if (location == null || persistedOwner == null) return false;
        return managed.register(location, persistedOwner);
    }
    public boolean mayAutomate(TradeProtectedLocations.Location location, UUID persistedOwner) {
        return location != null && persistedOwner != null && managed.canMutate(location, persistedOwner);
    }
    public void unload(TradeProtectedLocations.Location location, UUID persistedOwner) {
        if (location != null && persistedOwner != null) locations.remove(location, persistedOwner);
    }
}
