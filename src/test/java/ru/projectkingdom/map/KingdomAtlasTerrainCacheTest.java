package ru.projectkingdom.map;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class KingdomAtlasTerrainCacheTest {
    @Test void storesOnlyObservedTerrainAndRestoresItFromWorldNbt() {
        var cache = new KingdomAtlasTerrainCache();
        var forest = new KingdomAtlasTerrain.Cell(-12, 31, KingdomAtlasTerrain.Type.FOREST, 17);
        var lake = new KingdomAtlasTerrain.Cell(-13, 31, KingdomAtlasTerrain.Type.WATER, -2);
        cache.remember("minecraft:overworld", List.of(forest,lake));
        assertEquals(2,cache.sampleCount());
        assertNull(cache.remembered("minecraft:the_nether",-12,31));
        assertEquals(forest,cache.remembered("minecraft:overworld",-12,31));
        var loaded = KingdomAtlasTerrainCache.load(cache.save(new CompoundTag()));
        assertEquals(2,loaded.sampleCount());
        assertEquals(forest,loaded.remembered("minecraft:overworld",-12,31));
        assertEquals(lake,loaded.remembered("minecraft:overworld",-13,31));
    }

    @Test void ignoresUnknownSamplesAndUpdatesExistingSurface() {
        var cache = new KingdomAtlasTerrainCache();
        var first = new KingdomAtlasTerrain.Cell(0,0,KingdomAtlasTerrain.Type.PLAIN,10);
        var road = new KingdomAtlasTerrain.Cell(0,0,KingdomAtlasTerrain.Type.ROAD,11);
        cache.remember("minecraft:overworld",List.of(first,
                new KingdomAtlasTerrain.Cell(2,2,KingdomAtlasTerrain.Type.UNKNOWN,0)));
        assertEquals(1,cache.sampleCount());
        cache.remember("minecraft:overworld",List.of(road));
        assertEquals(1,cache.sampleCount());
        assertEquals(road,cache.remembered("minecraft:overworld",0,0));
    }

    @Test void rejectsMalformedOrUnboundedTerrainData() {
        var root = new CompoundTag();
        var entries = new net.minecraft.nbt.ListTag();
        var wrong = new CompoundTag();
        wrong.putString("Dim","minecraft:overworld");
        wrong.putInt("X",2);
        wrong.putInt("Z",3);
        wrong.putInt("Type",250);
        entries.add(wrong);
        root.put("Samples",entries);
        assertEquals(0,KingdomAtlasTerrainCache.load(root).sampleCount());
    }
}
