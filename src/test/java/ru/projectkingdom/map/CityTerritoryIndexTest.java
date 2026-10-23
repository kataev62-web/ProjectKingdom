package ru.projectkingdom.map;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CityTerritoryIndexTest {
    private static CityTerritoryIndex.ChunkKey at(int x, int z) {
        return new CityTerritoryIndex.ChunkKey("minecraft:overworld", x, z);
    }

    @Test void claimRequiresAdjacencyAndFounder() {
        var index = new CityTerritoryIndex();
        var founder = UUID.randomUUID();
        var city = UUID.randomUUID();
        assertTrue(index.registerCity(new CityTerritoryIndex.City(city, founder), at(0, 0)));
        assertEquals(CityTerritoryIndex.ClaimResult.NOT_ADJACENT,
            index.claim(city, founder, at(3, 0)));
        assertEquals(CityTerritoryIndex.ClaimResult.NOT_FOUNDER,
            index.claim(city, UUID.randomUUID(), at(1, 0)));
        assertEquals(CityTerritoryIndex.ClaimResult.CLAIMED,
            index.claim(city, founder, at(1, 0)));
        assertEquals(city, index.owner(at(1, 0)));
    }

    @Test void blocksProtectedAndOccupiedClaims() {
        var index = new CityTerritoryIndex();
        var a = UUID.randomUUID(); var b = UUID.randomUUID();
        var founderA = UUID.randomUUID(); var founderB = UUID.randomUUID();
        assertTrue(index.registerCity(new CityTerritoryIndex.City(a, founderA), at(0, 0)));
        assertTrue(index.registerCity(new CityTerritoryIndex.City(b, founderB), at(2, 0)));
        assertEquals(CityTerritoryIndex.ClaimResult.CLAIMED, index.claim(b, founderB, at(1, 0)));
        assertEquals(CityTerritoryIndex.ClaimResult.OCCUPIED, index.claim(a, founderA, at(1, 0)));
        index.setProtected(at(0, 1), true);
        assertEquals(CityTerritoryIndex.ClaimResult.PROTECTED, index.claim(a, founderA, at(0, 1)));
    }

    @Test void dimensionsAreIsolated() {
        var index = new CityTerritoryIndex();
        var founder = UUID.randomUUID(); var city = UUID.randomUUID();
        index.registerCity(new CityTerritoryIndex.City(city, founder), at(0, 0));
        assertEquals(CityTerritoryIndex.ClaimResult.NOT_ADJACENT,
            index.claim(city, founder, new CityTerritoryIndex.ChunkKey("minecraft:the_nether", 1, 0)));
    }

    @Test void exportReplaysClaimsInSafeAdjacencyOrder() {
        var index = new CityTerritoryIndex();
        var founder = UUID.randomUUID(); var city = UUID.randomUUID();
        assertTrue(index.registerCity(new CityTerritoryIndex.City(city, founder), at(0, 0)));
        assertEquals(CityTerritoryIndex.ClaimResult.CLAIMED, index.claim(city, founder, at(1, 0)));
        assertEquals(CityTerritoryIndex.ClaimResult.CLAIMED, index.claim(city, founder, at(1, 1)));
        assertEquals(CityTerritoryIndex.ClaimResult.CLAIMED, index.claim(city, founder, at(0, 1)));
        var copy = new CityTerritoryIndex();
        var snapshot = index.exportCities().get(0);
        assertTrue(copy.registerCity(snapshot.city(), snapshot.origin()));
        for (var key : snapshot.additionalClaims()) {
            assertEquals(CityTerritoryIndex.ClaimResult.CLAIMED, copy.claim(city, founder, key));
        }
        assertEquals(index.claimedChunks(city).size(), copy.claimedChunks(city).size());
    }
}
