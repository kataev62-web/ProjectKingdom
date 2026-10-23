package ru.projectkingdom.npc;

import java.util.*;

/** Pure preflight of a proposed city expansion. Does not mutate or debit. */
public final class KingdomExpansionQuote {
    public static final long PRICE_PER_CHUNK = 100L;
    public enum Status { READY, EMPTY, TOO_LARGE, CONFLICT, DISCONNECTED, INSUFFICIENT_TREASURY }
    public record Result(Status status, long price, int chunks) {}
    private KingdomExpansionQuote() {}

    public static Result evaluate(UUID cityId, String dimension,
                                  Collection<KingdomExpansionPlans.Cell> proposed,
                                  Collection<KingdomCivicsData.Plot> existing, long treasury) {
        Objects.requireNonNull(cityId);
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(proposed);
        Objects.requireNonNull(existing);
        if (proposed.isEmpty()) return new Result(Status.EMPTY, 0, 0);
        if (proposed.size() > 256) return new Result(Status.TOO_LARGE, 0, proposed.size());
        var candidates = new HashSet<KingdomExpansionPlans.Cell>();
        for (var cell : proposed) {
            if (!dimension.equals(cell.dimension()) || !candidates.add(cell))
                return new Result(Status.CONFLICT, 0, proposed.size());
        }
        var owned = new HashSet<KingdomExpansionPlans.Cell>();
        var occupied = new HashSet<KingdomExpansionPlans.Cell>();
        for (var plot : existing) {
            var cell = new KingdomExpansionPlans.Cell(plot.dimension(), plot.x(), plot.z());
            occupied.add(cell);
            if (cityId.equals(plot.town())) owned.add(cell);
        }
        for (var cell : candidates) if (occupied.contains(cell))
            return new Result(Status.CONFLICT, 0, candidates.size());

        var visited = new HashSet<KingdomExpansionPlans.Cell>();
        var pending = new ArrayDeque<KingdomExpansionPlans.Cell>();
        for (var cell : candidates) {
            if (neighbors(cell).stream().anyMatch(owned::contains)) {
                visited.add(cell);
                pending.add(cell);
            }
        }
        while (!pending.isEmpty()) {
            for (var neighbor : neighbors(pending.removeFirst())) {
                if (candidates.contains(neighbor) && visited.add(neighbor)) pending.add(neighbor);
            }
        }
        long price = candidates.size() * PRICE_PER_CHUNK;
        if (visited.size() != candidates.size()) return new Result(Status.DISCONNECTED, price, candidates.size());
        if (treasury < price) return new Result(Status.INSUFFICIENT_TREASURY, price, candidates.size());
        return new Result(Status.READY, price, candidates.size());
    }
    private static List<KingdomExpansionPlans.Cell> neighbors(KingdomExpansionPlans.Cell c) {
        String d = c.dimension();
        return List.of(new KingdomExpansionPlans.Cell(d,c.x()+1,c.z()),
            new KingdomExpansionPlans.Cell(d,c.x()-1,c.z()),
            new KingdomExpansionPlans.Cell(d,c.x(),c.z()+1),
            new KingdomExpansionPlans.Cell(d,c.x(),c.z()-1));
    }
}
