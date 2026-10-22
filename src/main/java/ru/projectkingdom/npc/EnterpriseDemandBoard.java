package ru.projectkingdom.npc;

import java.math.BigInteger;
import java.util.*;

/** Deterministic demand-board state machine. Physical transfers are performed by the trade coordinator. */
public final class EnterpriseDemandBoard {
    private EnterpriseDemandBoard() {}
    public record Key(UUID enterprise, String item) {
        public Key { Objects.requireNonNull(enterprise); if (item == null || item.isBlank()) throw new IllegalArgumentException("item"); }
    }
    public record Listing(Key key, UUID listingId, int remaining, long maximumUnitCopper) {
        public Listing { Objects.requireNonNull(key); Objects.requireNonNull(listingId); if (remaining <= 0 || maximumUnitCopper <= 0) throw new IllegalArgumentException("listing"); }
    }
    /** Preserve listing identity on refresh; remove it when no stock is needed. */
    public static Listing refresh(Listing old, Key key, EnterpriseStockPolicy.Thresholds limits,
                                  int physicalStock, int incoming, long maximumUnitCopper) {
        Objects.requireNonNull(key);
        if (old != null && !old.key().equals(key)) throw new IllegalArgumentException("wrong listing owner");
        if (maximumUnitCopper <= 0) throw new IllegalArgumentException("price");
        int needed = EnterpriseStockPolicy.reorderQuantity(limits, physicalStock, incoming);
        if (needed == 0) return null;
        return new Listing(key, old == null ? UUID.randomUUID() : old.listingId(), needed, maximumUnitCopper);
    }
    public record Delivery(UUID deliveryId, UUID listingId, int quantity, long unitCopper) {
        public Delivery { Objects.requireNonNull(deliveryId); Objects.requireNonNull(listingId); }
    }
    public enum Decision { ACCEPT, DUPLICATE, INVALID, PRICE_TOO_HIGH, INSUFFICIENT_COINS }
    public static Decision check(Listing listing, Delivery delivery, Set<UUID> completedDeliveries, long physicalCopper) {
        Objects.requireNonNull(listing); Objects.requireNonNull(delivery); Objects.requireNonNull(completedDeliveries);
        if (completedDeliveries.contains(delivery.deliveryId())) return Decision.DUPLICATE;
        if (!listing.listingId().equals(delivery.listingId()) || delivery.quantity() <= 0 ||
                delivery.quantity() > listing.remaining() || delivery.unitCopper() <= 0) return Decision.INVALID;
        if (delivery.unitCopper() > listing.maximumUnitCopper()) return Decision.PRICE_TOO_HIGH;
        if (physicalCopper < 0 || BigInteger.valueOf(delivery.quantity()).multiply(BigInteger.valueOf(delivery.unitCopper()))
                .compareTo(BigInteger.valueOf(physicalCopper)) > 0) return Decision.INSUFFICIENT_COINS;
        return Decision.ACCEPT;
    }
    /** Call only AFTER physical items/coins have been committed and reconciled. */
    public static Listing committed(Listing listing, Delivery delivery, Set<UUID> completedDeliveries) {
        if (completedDeliveries.contains(delivery.deliveryId())) return listing;
        if (!listing.listingId().equals(delivery.listingId()) || delivery.quantity() <= 0 || delivery.quantity() > listing.remaining())
            throw new IllegalArgumentException("delivery");
        completedDeliveries.add(delivery.deliveryId());
        int left = listing.remaining() - delivery.quantity();
        return left == 0 ? null : new Listing(listing.key(), listing.listingId(), left, listing.maximumUnitCopper());
    }
}
