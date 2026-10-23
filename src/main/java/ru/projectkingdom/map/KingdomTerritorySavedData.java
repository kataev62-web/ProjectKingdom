package ru.projectkingdom.map;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent server-owned territory state. Access on the logical server thread.
 * Saved in the overworld data storage so it survives reloads and is shared
 * between dimensions (ChunkKey itself contains the dimension identifier).
 */
public final class KingdomTerritorySavedData extends SavedData {
    private static final String DATA_NAME = "kingdom_city_territories";
    private static final int FORMAT_VERSION = 1;
    private final CityTerritoryIndex index = new CityTerritoryIndex();

    public static KingdomTerritorySavedData get(ServerLevel level) {
        if (level.getServer() == null) throw new IllegalStateException("Server level required");
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
            KingdomTerritorySavedData::load, KingdomTerritorySavedData::new, DATA_NAME);
    }

    public CityTerritoryIndex index() { return index; }

    public boolean registerCity(UUID cityId, UUID founderId, CityTerritoryIndex.ChunkKey origin) {
        boolean result = index.registerCity(new CityTerritoryIndex.City(cityId, founderId), origin);
        if (result) setDirty();
        return result;
    }

    /**
     * The caller MUST validate the authenticated player and commit/reserve
     * payment atomically at a higher application layer before claiming.
     */
    public CityTerritoryIndex.ClaimResult claim(UUID cityId, UUID actorId, CityTerritoryIndex.ChunkKey key) {
        var result = index.claim(cityId, actorId, key);
        if (result == CityTerritoryIndex.ClaimResult.CLAIMED) setDirty();
        return result;
    }

    public void setProtected(CityTerritoryIndex.ChunkKey key, boolean protectedArea) {
        index.setProtected(key, protectedArea);
        setDirty();
    }

    public static KingdomTerritorySavedData load(CompoundTag root) {
        KingdomTerritorySavedData data = new KingdomTerritorySavedData();
        if (root.getInt("Version") > FORMAT_VERSION) {
            throw new IllegalStateException("Unsupported Kingdom territory data version");
        }
        ListTag cities = root.getList("Cities", Tag.TAG_COMPOUND);
        for (int i = 0; i < cities.size(); i++) {
            CompoundTag tag = cities.getCompound(i);
            UUID city = tag.getUUID("Id");
            UUID founder = tag.getUUID("Founder");
            CompoundTag origin = tag.getCompound("Origin");
            if (!data.index.registerCity(new CityTerritoryIndex.City(city, founder), key(origin))) {
                throw new IllegalStateException("Invalid founding territory for city " + city);
            }
            ListTag claimed = tag.getList("Claims", Tag.TAG_COMPOUND);
            // Claim order is serialized in adjacency order, making replay deterministic.
            for (int j = 0; j < claimed.size(); j++) {
                var claimedKey = key(claimed.getCompound(j));
                var result = data.index.claim(city, founder, claimedKey);
                if (result != CityTerritoryIndex.ClaimResult.CLAIMED) {
                    throw new IllegalStateException("Invalid city claim " + claimedKey + ": " + result);
                }
            }
        }
        ListTag protectedList = root.getList("Protected", Tag.TAG_COMPOUND);
        for (int i = 0; i < protectedList.size(); i++) {
            data.index.setProtected(key(protectedList.getCompound(i)), true);
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag root) {
        root.putInt("Version", FORMAT_VERSION);
        ListTag cities = new ListTag();
        for (var entry : index.exportCities()) {
            CompoundTag city = new CompoundTag();
            city.putUUID("Id", entry.city().id());
            city.putUUID("Founder", entry.city().founderId());
            city.put("Origin", writeKey(entry.origin()));
            ListTag claims = new ListTag();
            for (var key : entry.additionalClaims()) claims.add(writeKey(key));
            city.put("Claims", claims);
            cities.add(city);
        }
        root.put("Cities", cities);
        ListTag protectedChunks = new ListTag();
        for (var key : index.protectedSnapshot()) protectedChunks.add(writeKey(key));
        root.put("Protected", protectedChunks);
        return root;
    }

    private static CityTerritoryIndex.ChunkKey key(CompoundTag tag) {
        return new CityTerritoryIndex.ChunkKey(tag.getString("Dimension"), tag.getInt("X"), tag.getInt("Z"));
    }
    private static CompoundTag writeKey(CityTerritoryIndex.ChunkKey key) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", key.dimension());
        tag.putInt("X", key.x());
        tag.putInt("Z", key.z());
        return tag;
    }
}
