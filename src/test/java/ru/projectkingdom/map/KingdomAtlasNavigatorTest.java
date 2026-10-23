package ru.projectkingdom.map;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class KingdomAtlasNavigatorTest {
    private static KingdomGraphicAtlasData.Snapshot terrain(int scale, KingdomAtlasTerrain.Type fill,
                                                            int[][] overrides) {
        var tiles=new ArrayList<KingdomGraphicAtlasData.Tile>();
        for(int i=0;i<KingdomGraphicAtlasData.MAX_CELLS;i++){
            tiles.add(new KingdomGraphicAtlasData.Tile(0,false,false,false,
                    0,0,0,"","","",fill.ordinal(),0));
        }
        // Rows: {column,row,terrainType}
        for(var m:overrides){
            int index=m[1]*KingdomGraphicAtlasData.COLUMNS+m[0];
            var old=tiles.get(index);
            tiles.set(index,new KingdomGraphicAtlasData.Tile(old.claims(),old.own(),old.mixed(),
                    old.planned(),old.townColor(),old.nationColor(),old.enterprises(),
                    old.town(),old.nation(),old.business(),m[2],old.elevation()));
        }
        return new KingdomGraphicAtlasData.Snapshot(1,0,0,scale,
                KingdomGraphicAtlasData.Layer.INFRASTRUCTURE,tiles);
    }

    @Test void prefersConnectedRoadRatherThanShorterForestDetour() {
        // Start at centre cell (11,7), destination (14,7).
        var map=terrain(1,KingdomAtlasTerrain.Type.FOREST,new int[][]{
            {11,7,KingdomAtlasTerrain.Type.ROAD.ordinal()},
            {11,6,KingdomAtlasTerrain.Type.ROAD.ordinal()},
            {12,6,KingdomAtlasTerrain.Type.ROAD.ordinal()},
            {13,6,KingdomAtlasTerrain.Type.ROAD.ordinal()},
            {14,6,KingdomAtlasTerrain.Type.ROAD.ordinal()},
            {14,7,KingdomAtlasTerrain.Type.ROAD.ordinal()}
        });
        var route=KingdomAtlasNavigator.plan(map,0,0,3,0);
        assertEquals(KingdomAtlasNavigator.Status.ROUTE,route.status());
        assertTrue(route.steps().stream().anyMatch(s->s.row()==6));
        assertEquals(5*16,route.estimatedBlocks());
    }

    @Test void willNotInventPathsThroughUnknownTerrainOrWater() {
        var water=terrain(1,KingdomAtlasTerrain.Type.WATER,new int[][]{
            {11,7,KingdomAtlasTerrain.Type.PLAIN.ordinal()},
            {12,7,KingdomAtlasTerrain.Type.PLAIN.ordinal()},
            {14,7,KingdomAtlasTerrain.Type.PLAIN.ordinal()}
        });
        assertEquals(KingdomAtlasNavigator.Status.BLOCKED,
                KingdomAtlasNavigator.plan(water,0,0,3,0).status());
        var unknown=terrain(1,KingdomAtlasTerrain.Type.UNKNOWN,new int[][]{
            {11,7,KingdomAtlasTerrain.Type.PLAIN.ordinal()},
            {14,7,KingdomAtlasTerrain.Type.PLAIN.ordinal()}
        });
        assertEquals(KingdomAtlasNavigator.Status.BLOCKED,
                KingdomAtlasNavigator.plan(unknown,0,0,3,0).status());
        assertEquals(KingdomAtlasNavigator.Status.UNCHARTED,
                KingdomAtlasNavigator.plan(unknown,4,0,3,0).status());
    }

    @Test void givesClearResultForTooDistantOrZoomedDestination() {
        var map=terrain(1,KingdomAtlasTerrain.Type.PLAIN,new int[0][]);
        assertEquals(KingdomAtlasNavigator.Status.OUTSIDE_MAP,
                KingdomAtlasNavigator.plan(map,0,0,100,0).status());
        assertEquals(KingdomAtlasNavigator.Status.ALREADY_THERE,
                KingdomAtlasNavigator.plan(map,0,0,0,0).status());
        assertEquals(KingdomAtlasNavigator.Status.ZOOM_IN,
                KingdomAtlasNavigator.plan(terrain(4,KingdomAtlasTerrain.Type.PLAIN,new int[0][]),
                        0,0,1,0).status());
    }

    @Test void rejectsNullSnapshot() {
        assertThrows(IllegalArgumentException.class,()->
                KingdomAtlasNavigator.plan(null,0,0,1,1));
    }
}
