package ru.projectkingdom.npc;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** One-click trade menu contract: a player puts goods into an offer slot and confirms.
 * The server-side port MUST implement atomic physical transfer (or durable quarantine)
 * and must never mint coins. This class does not claim Minecraft GUI integration. */
public final class SimpleTradeMenuSale {
    public record Offer(UUID id, String item, int quantity, long priceCopper) {
        public Offer {
            Objects.requireNonNull(id);
            if (item == null || item.isBlank() || quantity <= 0 || priceCopper <= 0)
                throw new IllegalArgumentException("invalid offer");
        }
    }
    public record Result(boolean sold, String reason, long paidCopper) {}
    public interface PhysicalPort {
        int offered(String item) throws IOException;
        int warehouseSpace(String item) throws IOException;
        long cashboxCopper() throws IOException;
        long walletSpaceCopper() throws IOException;
        /** Must move offered goods to warehouse and existing coins to wallet together.
         * On uncertain partial completion, throw and quarantine externally. */
        void settle(String item, int quantity, long copper) throws IOException;
    }
    private final Set<UUID> confirmed = new HashSet<>();
    private boolean busy;
    public synchronized Result sell(Offer offer, PhysicalPort port) throws IOException {
        Objects.requireNonNull(offer); Objects.requireNonNull(port);
        if (confirmed.contains(offer.id())) return new Result(false, "ALREADY_SOLD", 0);
        if (busy) return new Result(false, "BUSY", 0);
        busy = true;
        try {
            final long total;
            try { total = Math.multiplyExact((long)offer.quantity(), offer.priceCopper()); }
            catch (ArithmeticException ex) { return new Result(false, "PRICE_OVERFLOW", 0); }
            if (port.offered(offer.item()) < offer.quantity()) return new Result(false, "NOT_ENOUGH_GOODS", 0);
            if (port.warehouseSpace(offer.item()) < offer.quantity()) return new Result(false, "WAREHOUSE_FULL", 0);
            if (port.cashboxCopper() < total) return new Result(false, "CASHBOX_EMPTY", 0);
            if (port.walletSpaceCopper() < total) return new Result(false, "WALLET_FULL", 0);
            port.settle(offer.item(), offer.quantity(), total);
            confirmed.add(offer.id());
            return new Result(true, "SOLD", total);
        } finally { busy = false; }
    }
}
