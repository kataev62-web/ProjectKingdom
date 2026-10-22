package ru.projectkingdom.npc;

import java.util.*;

/** Information only. Never moves items, coins, or loads chunks. Update on listing/trade events. */
public final class SimpleTradeNoticeBoard {
    public enum Side { BUY, SELL }
    public record Notice(UUID id, UUID owner, Side side, String itemId, int quantity,
                         long priceCopper, String location) {
        public Notice {
            Objects.requireNonNull(id); Objects.requireNonNull(owner); Objects.requireNonNull(side);
            if (itemId == null || !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("registered item ID required");
            if (quantity <= 0 || priceCopper <= 0) throw new IllegalArgumentException("quantity/price");
            if (location == null || location.isBlank() || location.length() > 256)
                throw new IllegalArgumentException("location");
        }
    }
    private final Map<UUID, Notice> notices = new LinkedHashMap<>();
    public synchronized void post(Notice notice) {
        Objects.requireNonNull(notice);
        Notice existing = notices.get(notice.id());
        if (existing != null && !existing.owner().equals(notice.owner()))
            throw new IllegalArgumentException("Cannot change notice owner");
        notices.put(notice.id(), notice);
    }
    public synchronized boolean remove(UUID id) { return notices.remove(id) != null; }
    /** Reflect a confirmed physical trade, never execute one. */
    public synchronized boolean sold(UUID id, int amount) {
        Notice n = notices.get(id);
        if (n == null || amount <= 0 || amount > n.quantity()) return false;
        if (amount == n.quantity()) notices.remove(id);
        else notices.put(id, new Notice(n.id(), n.owner(), n.side(), n.itemId(),
                n.quantity() - amount, n.priceCopper(), n.location()));
        return true;
    }
    public synchronized List<Notice> page(Side side, int offset, int limit) {
        Objects.requireNonNull(side);
        if (offset < 0 || limit < 1 || limit > 50) throw new IllegalArgumentException("page");
        return notices.values().stream().filter(n -> n.side() == side)
                .sorted(Comparator.comparing(n -> n.id().toString())).skip(offset).limit(limit).toList();
    }
    public synchronized int size() { return notices.size(); }
}
