package ru.projectkingdom.npc;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/** One local trade action for both board directions. The notice board is read-only discovery.
 * PhysicalExchange implementations must use actual game inventory items and be all-or-nothing.
 * Run on the server thread; no polling, chunk loading, or NPC search.
 */
public final class SimpleLocalTradeService {
    public enum Result { TRADED, NOT_FOUND, WRONG_SHOP, WRONG_ITEM, INVALID_QUANTITY,
        NO_GOODS, NO_MONEY, NO_SPACE, NEEDS_REVIEW }

    public interface PhysicalExchange {
        /** BUY: visitor supplies goods and shop supplies coins.
         * SELL: shop supplies goods and visitor supplies coins. */
        boolean goodsAvailable(SimpleTradeNoticeBoard.Side side, String itemId, int quantity);
        boolean moneyAvailable(SimpleTradeNoticeBoard.Side side, long copper);
        boolean goodsFit(SimpleTradeNoticeBoard.Side side, String itemId, int quantity);
        boolean moneyFits(SimpleTradeNoticeBoard.Side side, long copper);
        /** Return false only if NO inventories changed. True only after both physical transfers. */
        boolean exchange(SimpleTradeNoticeBoard.Side side, String itemId, int quantity, long copper);
    }

    private final DurableSimpleTradeNoticeBoard board;
    private boolean uncertain;
    public SimpleLocalTradeService(DurableSimpleTradeNoticeBoard board) { this.board = Objects.requireNonNull(board); }

    public synchronized Result trade(UUID noticeId, UUID shopId, String itemId, int quantity, PhysicalExchange port) {
        Objects.requireNonNull(noticeId); Objects.requireNonNull(shopId);
        Objects.requireNonNull(itemId); Objects.requireNonNull(port);
        if (uncertain) return Result.NEEDS_REVIEW;
        var notice = board.notice(noticeId);
        if (notice == null) return Result.NOT_FOUND;
        if (!notice.owner().equals(shopId)) return Result.WRONG_SHOP;
        if (!notice.itemId().equals(itemId)) return Result.WRONG_ITEM;
        if (quantity < 1 || quantity > notice.quantity()) return Result.INVALID_QUANTITY;
        long cost;
        try { cost = Math.multiplyExact(notice.priceCopper(), quantity); }
        catch (ArithmeticException ex) { return Result.INVALID_QUANTITY; }
        var side = notice.side();
        if (!port.goodsAvailable(side, itemId, quantity)) return Result.NO_GOODS;
        if (!port.moneyAvailable(side, cost)) return Result.NO_MONEY;
        if (!port.goodsFit(side, itemId, quantity) || !port.moneyFits(side, cost)) return Result.NO_SPACE;
        uncertain = true;
        try {
            if (!port.exchange(side, itemId, quantity, cost)) {
                uncertain = false; // port contract: no mutation on false
                return Result.NO_SPACE;
            }
            if (!board.confirmedTrade(noticeId, itemId, quantity)) return Result.NEEDS_REVIEW;
            uncertain = false;
            return Result.TRADED;
        } catch (IOException | RuntimeException ex) {
            // An inventory transfer may already have happened. Never retry blindly.
            return Result.NEEDS_REVIEW;
        }
    }
    public synchronized boolean needsReview() { return uncertain; }
}
