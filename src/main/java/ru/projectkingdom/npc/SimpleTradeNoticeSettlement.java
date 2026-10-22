package ru.projectkingdom.npc;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/** Event-driven local purchase against a posted BUY notice. No trading on the notice board.
 * The caller opens the shop in person and supplies a physical inventory port.
 * All methods must be called on the Minecraft server thread.
 */
public final class SimpleTradeNoticeSettlement {
    public enum Result { SOLD, NOT_FOUND, WRONG_BUYER, WRONG_ITEM, INVALID_AMOUNT,
        NOT_ENOUGH_GOODS, NOT_ENOUGH_COINS, NO_SPACE, NEEDS_REVIEW }

    /** Implement using real inventories. A transfer must either succeed completely or
     * return false without changing any inventory. Never mint coins or goods. */
    public interface PhysicalTrade {
        boolean hasGoods(String itemId, int quantity);
        boolean hasCoins(long copper);
        boolean canReceiveGoods(String itemId, int quantity);
        boolean canReceiveCoins(long copper);
        boolean transfer(String itemId, int quantity, long copper);
    }

    private final DurableSimpleTradeNoticeBoard notices;
    private boolean uncertain;
    public SimpleTradeNoticeSettlement(DurableSimpleTradeNoticeBoard notices) {
        this.notices = Objects.requireNonNull(notices);
    }

    /** A single synchronized server-thread settlement; the notice is never a marketplace. */
    public synchronized Result sell(UUID noticeId, UUID buyer, String itemId, int quantity, PhysicalTrade trade) {
        Objects.requireNonNull(noticeId); Objects.requireNonNull(buyer);
        Objects.requireNonNull(itemId); Objects.requireNonNull(trade);
        if (uncertain) return Result.NEEDS_REVIEW;
        var n = notices.notice(noticeId);
        if (n == null || n.side() != SimpleTradeNoticeBoard.Side.BUY) return Result.NOT_FOUND;
        if (!n.owner().equals(buyer)) return Result.WRONG_BUYER;
        if (!n.itemId().equals(itemId)) return Result.WRONG_ITEM;
        if (quantity <= 0 || quantity > n.quantity()) return Result.INVALID_AMOUNT;
        long total;
        try { total = Math.multiplyExact(n.priceCopper(), (long) quantity); }
        catch (ArithmeticException ex) { return Result.INVALID_AMOUNT; }
        if (!trade.hasGoods(itemId, quantity)) return Result.NOT_ENOUGH_GOODS;
        if (!trade.hasCoins(total)) return Result.NOT_ENOUGH_COINS;
        if (!trade.canReceiveGoods(itemId, quantity) || !trade.canReceiveCoins(total)) return Result.NO_SPACE;
        // Fail closed after an uncertain transfer. A crash between transfer and notice
        // update still requires an external recovery procedure; not crash-atomic.
        uncertain = true;
        if (!trade.transfer(itemId, quantity, total)) {
            // Contract: false guarantees NO mutation.
            uncertain = false;
            return Result.NO_SPACE;
        }
        try {
            if (!notices.confirmedTrade(noticeId, itemId, quantity)) return Result.NEEDS_REVIEW;
            uncertain = false;
            return Result.SOLD;
        } catch (IOException ex) {
            return Result.NEEDS_REVIEW;
        }
    }

    public synchronized boolean needsReview() { return uncertain; }
}
