package ru.projectkingdom.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Server-side territorial index. Coordinates are chunk coordinates, not blocks.
 * Caller handles persistence, payments, authentication and world border checks.
 */
public final class CityTerritoryIndex {
    public record ChunkKey(String dimension, int x, int z) {
        public ChunkKey { Objects.requireNonNull(dimension, "dimension"); }
    }
    public record City(UUID id, UUID founderId) {
        public City {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(founderId, "founderId");
        }
    }
    public enum Availability { OWNED_BY_CITY, FREE_ADJACENT, FREE_NOT_ADJACENT, OCCUPIED, PROTECTED }
    public enum ClaimResult { CLAIMED, UNKNOWN_CITY, NOT_FOUNDER, NOT_ADJACENT, OCCUPIED, PROTECTED, ALREADY_OWNED }

    private final Map<UUID, City> cities = new HashMap<>();
    private final Map<ChunkKey, UUID> claims = new HashMap<>();
    private final Set<ChunkKey> protectedChunks = new HashSet<>();

    public synchronized boolean registerCity(City city, ChunkKey foundingChunk) {
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(foundingChunk, "foundingChunk");
        if (cities.containsKey(city.id()) || claims.containsKey(foundingChunk) || protectedChunks.contains(foundingChunk)) return false;
        cities.put(city.id(), city);
        claims.put(foundingChunk, city.id());
        return true;
    }
    public synchronized void setProtected(ChunkKey key, boolean value) {
        Objects.requireNonNull(key, "key");
        if (value && claims.containsKey(key)) throw new IllegalStateException("Cannot protect a claimed chunk");
        if (value) protectedChunks.add(key);
        else protectedChunks.remove(key);
    }
    public synchronized UUID owner(ChunkKey key) { return claims.get(key); }
    public synchronized Availability availability(UUID cityId, ChunkKey key) {
        Objects.requireNonNull(cityId, "cityId");
        Objects.requireNonNull(key, "key");
        UUID owner = claims.get(key);
        if (cityId.equals(owner)) return Availability.OWNED_BY_CITY;
        if (owner != null) return Availability.OCCUPIED;
        if (protectedChunks.contains(key)) return Availability.PROTECTED;
        return adjacent(cityId, key) ? Availability.FREE_ADJACENT : Availability.FREE_NOT_ADJACENT;
    }
    /** Invoke only after server-side payment reservation succeeds. */
    public synchronized ClaimResult claim(UUID cityId, UUID actorId, ChunkKey key) {
        City city = cities.get(cityId);
        if (city == null) return ClaimResult.UNKNOWN_CITY;
        if (!city.founderId().equals(actorId)) return ClaimResult.NOT_FOUNDER;
        return switch (availability(cityId, key)) {
            case OWNED_BY_CITY -> ClaimResult.ALREADY_OWNED;
            case OCCUPIED -> ClaimResult.OCCUPIED;
            case PROTECTED -> ClaimResult.PROTECTED;
            case FREE_NOT_ADJACENT -> ClaimResult.NOT_ADJACENT;
            case FREE_ADJACENT -> { claims.put(key, cityId); yield ClaimResult.CLAIMED; }
        };
    }
    public synchronized Map<ChunkKey, Availability> snapshot(UUID cityId, Collection<ChunkKey> visible) {
        Map<ChunkKey, Availability> result = new HashMap<>();
        for (ChunkKey key : visible) result.put(key, availability(cityId, key));
        return Map.copyOf(result);
    }
    public synchronized List<ChunkKey> claimedChunks(UUID cityId) {
        List<ChunkKey> result = new ArrayList<>();
        for (Map.Entry<ChunkKey, UUID> e : claims.entrySet()) {
            if (cityId.equals(e.getValue())) result.add(e.getKey());
        }
        return List.copyOf(result);
    }
    private boolean adjacent(UUID cityId, ChunkKey key) {
        return cityId.equals(claims.get(new ChunkKey(key.dimension(), key.x() + 1, key.z())))
            || cityId.equals(claims.get(new ChunkKey(key.dimension(), key.x() - 1, key.z())))
            || cityId.equals(claims.get(new ChunkKey(key.dimension(), key.x(), key.z() + 1)))
            || cityId.equals(claims.get(new ChunkKey(key.dimension(), key.x(), key.z() - 1)));
    }
}
