package ru.projectkingdom.npc;

/** Server-process registry; persisted owners are re-enrolled when storage block entities load.
 * Trade coordinator must use this same lock instance before automation is enabled.
 */
public final class KingdomStorageRuntime {
    private KingdomStorageRuntime() {}
    public static final TradeInventoryMutationGuard LOCKS = new TradeInventoryMutationGuard();
    public static final TradeProtectedLocations LOCATIONS = new TradeProtectedLocations(LOCKS);
    public static final TradeManagedInventory MANAGED = new TradeManagedInventory(LOCKS, LOCATIONS);
    public static final TradeStorageEnrollment ENROLLMENT = new TradeStorageEnrollment(MANAGED, LOCATIONS);
}
