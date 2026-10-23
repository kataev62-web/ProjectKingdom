package ru.projectkingdom.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Small persistent atlas survey cache. Remembers ONLY real terrain sampled
 * from previously loaded chunks; it does not generate terrain or reveal ore.
 * Storage stays bounded on an endlessly expanding server world.
 */
public final class KingdomAtlasTerrainCache extends SavedData {
    private static final String DATA_KEY = "kingdom_explored_surface_atlas_v1";
    private static final int MAX_SAMPLES = 32768;

    public record Key(String dimension, int chunkX, int chunkZ) {}
    private final LinkedHashMap<Key, KingdomAtlasTerrain.Cell> remembered = new LinkedHashMap<>();

    public static KingdomAtlasTerrainCache get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(KingdomAtlasTerrainCache::load, KingdomAtlasTerrainCache::new, DATA_KEY);
    }

    public void remember(String dimension, Collection<KingdomAtlasTerrain.Cell> cells) {
        if (dimension == null || dimension.isBlank() || dimension.length() > 96) return;
        boolean changed = false;
        for (var cell : cells) {
            if (cell.type() == KingdomAtlasTerrain.Type.UNKNOWN) continue;
            var key = new Key(dimension, cell.chunkX(), cell.chunkZ());
            var previous = remembered.put(key, cell);
            if (!cell.equals(previous)) changed = true;
        }
        while (remembered.size() > MAX_SAMPLES) {
            var iterator = remembered.keySet().iterator();
            iterator.next();
            iterator.remove();
            changed = true;
        }
        if (changed) setDirty();
    }
    public KingdomAtlasTerrain.Cell remembered(String dimension, int x, int z) {
        return remembered.get(new Key(dimension, x, z));
    }
    public int sampleCount() { return remembered.size(); }

    /** Prefer live server blocks; when chunks unload, reuse the last surveyed surface. */
    public List<KingdomAtlasTerrain.Cell> view(ServerLevel level, int centerX, int centerZ, int scale) {
        String dimension = level.dimension().location().toString();
        var loaded = KingdomAtlasTerrain.sampleLoaded(level, centerX, centerZ, scale);
        remember(dimension, loaded);
        long minX = (long)centerX - (KingdomGraphicAtlasData.COLUMNS / 2L) * scale;
        long minZ = (long)centerZ - (KingdomGraphicAtlasData.ROWS / 2L) * scale;
        List<KingdomAtlasTerrain.Cell> answer = new ArrayList<>(KingdomGraphicAtlasData.MAX_CELLS);
        for (int row = 0; row < KingdomGraphicAtlasData.ROWS; row++) {
            for (int col = 0; col < KingdomGraphicAtlasData.COLUMNS; col++) {
                long x = minX + (long)col * scale + scale / 2;
                long z = minZ + (long)row * scale + scale / 2;
                if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE
                        || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) continue;
                var cell = remembered(dimension, (int)x, (int)z);
                if (cell != null) answer.add(cell);
            }
        }
        return List.copyOf(answer);
    }

    public static KingdomAtlasTerrainCache load(CompoundTag root) {
        var data = new KingdomAtlasTerrainCache();
        var records = root.getList("Samples", Tag.TAG_COMPOUND);
        for (int i = 0; i < records.size() && i < MAX_SAMPLES; i++) {
            var entry = records.getCompound(i);
            String dim = entry.getString("Dim");
            int id = entry.getInt("Type");
            if (dim.isBlank() || dim.length() > 96 || id <= 0
                    || id >= KingdomAtlasTerrain.Type.values().length) continue;
            int x = entry.getInt("X"), z = entry.getInt("Z");
            var cell = new KingdomAtlasTerrain.Cell(x, z,
                    KingdomAtlasTerrain.Type.values()[id], entry.getInt("Height"));
            data.remembered.put(new Key(dim, x, z), cell);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root) {
        var records = new ListTag();
        for (Map.Entry<Key, KingdomAtlasTerrain.Cell> item : remembered.entrySet()) {
            var row = new CompoundTag();
            row.putString("Dim", item.getKey().dimension());
            row.putInt("X", item.getKey().chunkX());
            row.putInt("Z", item.getKey().chunkZ());
            row.putInt("Type", item.getValue().type().ordinal());
            row.putInt("Height", item.getValue().elevation());
            records.add(row);
        }
        root.put("Samples", records);
        return root;
    }
}
