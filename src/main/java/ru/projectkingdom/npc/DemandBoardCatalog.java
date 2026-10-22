package ru.projectkingdom.npc;

import java.util.*;

/** Server-side listing index. This is a view, not a settlement ledger. */
public final class DemandBoardCatalog {
    private final Map<UUID, EnterpriseDemandBoard.Listing> listings = new HashMap<>();
    public synchronized void upsert(EnterpriseDemandBoard.Listing listing) {
        Objects.requireNonNull(listing);
        listings.entrySet().removeIf(e -> e.getValue().key().equals(listing.key()) && !e.getKey().equals(listing.listingId()));
        listings.put(listing.listingId(), listing);
    }
    public synchronized void remove(UUID id) { listings.remove(Objects.requireNonNull(id)); }
    public synchronized List<EnterpriseDemandBoard.Listing> page(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 50) throw new IllegalArgumentException("page");
        return listings.values().stream().sorted(Comparator.comparing(x -> x.listingId().toString()))
                .skip(offset).limit(limit).toList();
    }
    public synchronized int size() { return listings.size(); }
}
