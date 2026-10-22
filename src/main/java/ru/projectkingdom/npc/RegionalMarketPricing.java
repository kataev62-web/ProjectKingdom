package ru.projectkingdom.npc;

import java.math.BigInteger;
import java.util.*;

/** Pure domain policy. Prices are copper coin units per item, not virtual balances. */
public final class RegionalMarketPricing {
    private RegionalMarketPricing() {}
    public record Key(UUID city, String item) {
        public Key { Objects.requireNonNull(city); Objects.requireNonNull(item); if (item.isBlank()) throw new IllegalArgumentException("item"); }
    }
    public record Sale(long unitPrice, int quantity, boolean relatedParties) {
        public Sale { if (unitPrice <= 0 || quantity <= 0) throw new IllegalArgumentException("sale"); }
    }
    public record Quote(long reference, long normalMax, long shortageMax) {}
    public record Request(long physicalCopperAvailable, int currentStock, int minimumStock,
                          int maximumStock, int quantity, long offeredUnitPrice,
                          boolean shortage, boolean ownerApprovedOverride) {}

    private static long scaled(long value, long percent) {
        return BigInteger.valueOf(value).multiply(BigInteger.valueOf(percent))
                .divide(BigInteger.valueOf(100)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
    }
    public static Quote quote(long reference) {
        if (reference <= 0) throw new IllegalArgumentException("reference");
        return new Quote(reference, Math.max(1, scaled(reference, 120)), Math.max(1, scaled(reference, 150)));
    }
    /** No transaction alone may move the reference by more than 5% per game day.
     *  Unrelated trades outside 50%-150% of reference are ignored. */
    public static long updateDaily(long current, Collection<Sale> sales) {
        if (current <= 0) throw new IllegalArgumentException("current");
        Objects.requireNonNull(sales);
        BigInteger value = BigInteger.ZERO, count = BigInteger.ZERO;
        long lower = Math.max(1, scaled(current, 50));
        long upper = Math.max(1, scaled(current, 150));
        for (Sale sale : sales) {
            Objects.requireNonNull(sale);
            if (sale.relatedParties() || sale.unitPrice() < lower || sale.unitPrice() > upper) continue;
            value = value.add(BigInteger.valueOf(sale.unitPrice()).multiply(BigInteger.valueOf(sale.quantity())));
            count = count.add(BigInteger.valueOf(sale.quantity()));
        }
        if (count.signum() == 0) return current;
        BigInteger target = value.divide(count);
        long delta = scaled(current, 5);
        BigInteger floor = BigInteger.valueOf(Math.max(1, current - delta));
        BigInteger ceiling = BigInteger.valueOf(current).add(BigInteger.valueOf(delta)).min(BigInteger.valueOf(Long.MAX_VALUE));
        return target.max(floor).min(ceiling).longValueExact();
    }
    /** Check a physical-coin purchase; does not transfer or mint coins/items. */
    public static boolean canNpcBuy(Quote quote, Request request) {
        Objects.requireNonNull(quote); Objects.requireNonNull(request);
        if (request.physicalCopperAvailable() < 0 || request.currentStock() < 0 || request.minimumStock() < 0
                || request.maximumStock() < request.minimumStock() || request.quantity() <= 0
                || request.offeredUnitPrice() <= 0) return false;
        if (request.currentStock() >= request.minimumStock()) return false;
        if ((long) request.currentStock() + request.quantity() > request.maximumStock()) return false;
        long cap = request.shortage() ? quote.shortageMax() : quote.normalMax();
        if (!request.ownerApprovedOverride() && request.offeredUnitPrice() > cap) return false;
        BigInteger total = BigInteger.valueOf(request.offeredUnitPrice()).multiply(BigInteger.valueOf(request.quantity()));
        return total.compareTo(BigInteger.valueOf(request.physicalCopperAvailable())) <= 0;
    }
}
