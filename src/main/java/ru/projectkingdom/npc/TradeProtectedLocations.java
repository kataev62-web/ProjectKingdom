package ru.projectkingdom.npc;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Runtime-only index of explicitly enrolled physical trade containers.
 * Must be rebuilt from durable enterprise bindings on each server start.
 * An unknown location is never implicitly considered protected.
 */
public final class TradeProtectedLocations {
    public record Location(String dimension, long blockPos) {
        public Location { Objects.requireNonNull(dimension); }
    }
    private final Map<Location, UUID> accounts = new HashMap<>();
    private final TradeInventoryMutationGuard guard;
    public TradeProtectedLocations(TradeInventoryMutationGuard guard) {
        this.guard = Objects.requireNonNull(guard);
    }
    public synchronized boolean enroll(Location location, UUID account) {
        Objects.requireNonNull(location); Objects.requireNonNull(account);
        UUID existing = accounts.get(location);
        if (existing != null && !existing.equals(account)) return false;
        accounts.put(location, account);
        return true;
    }
    public synchronized boolean remove(Location location, UUID account) {
        return accounts.remove(location, account);
    }
    public synchronized UUID owner(Location location) { return accounts.get(location); }
    /** Unknown locations are not controlled by this registry. */
    public synchronized boolean mayMutate(Location location) {
        UUID owner = accounts.get(location);
        return owner == null || guard.canMutate(owner);
    }
}
