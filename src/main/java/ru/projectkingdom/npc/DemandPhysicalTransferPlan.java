package ru.projectkingdom.npc;

import java.math.BigInteger;
import java.util.Objects;

/** Pure preflight for physical delivery. Does not mint coins or items, and does not
 * claim atomicity across Minecraft inventories. Counts must come from real slots. */
public final class DemandPhysicalTransferPlan {
    private DemandPhysicalTransferPlan() {}
    public enum Status { READY, INVALID, WRONG_ITEM, NO_GOODS, NO_COINS, NO_GOODS_SPACE, NO_COIN_SPACE }
    public record Snapshot(String offeredItem, long supplierGoods, long enterpriseGoodsSpace,
                           long enterpriseCopper, long supplierCopperSpace) {
        public Snapshot { Objects.requireNonNull(offeredItem); }
    }
    public record Decision(Status status, long copperToMove, int goodsToMove) {
        public boolean ready() { return status == Status.READY; }
    }
    public static Decision inspect(EnterpriseDemandBoard.Listing listing,
                                   EnterpriseDemandBoard.Delivery delivery, Snapshot state) {
        Objects.requireNonNull(listing); Objects.requireNonNull(delivery); Objects.requireNonNull(state);
        if (!listing.listingId().equals(delivery.listingId()) || delivery.quantity() <= 0 ||
                delivery.quantity() > listing.remaining() || delivery.unitCopper() <= 0 ||
                delivery.unitCopper() > listing.maximumUnitCopper()) return reject(Status.INVALID);
        if (!listing.key().item().equals(state.offeredItem())) return reject(Status.WRONG_ITEM);
        if (state.supplierGoods() < delivery.quantity()) return reject(Status.NO_GOODS);
        if (state.enterpriseGoodsSpace() < delivery.quantity()) return reject(Status.NO_GOODS_SPACE);
        BigInteger price = BigInteger.valueOf(delivery.quantity()).multiply(BigInteger.valueOf(delivery.unitCopper()));
        if (price.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) return reject(Status.INVALID);
        long copper = price.longValueExact();
        if (state.enterpriseCopper() < copper) return reject(Status.NO_COINS);
        if (state.supplierCopperSpace() < copper) return reject(Status.NO_COIN_SPACE);
        return new Decision(Status.READY, copper, delivery.quantity());
    }
    private static Decision reject(Status status) { return new Decision(status, 0, 0); }
}
