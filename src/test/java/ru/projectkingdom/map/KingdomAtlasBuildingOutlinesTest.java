package ru.projectkingdom.map;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class KingdomAtlasBuildingOutlinesTest {
    private static final String WORLD="minecraft:overworld";

    @Test void clipsRegisteredFootprintsToCurrentDimensionAndViewport() {
        var inside=new KingdomAtlasBuildingOutlines.Outline(
                KingdomAtlasInfrastructure.Kind.BUILDING,-12,-9,7,8);
        var far=new KingdomAtlasBuildingOutlines.Outline(
                KingdomAtlasInfrastructure.Kind.ROAD,4000,0,4015,1);
        var nether=new KingdomAtlasBuildingOutlines.Outline(
                KingdomAtlasInfrastructure.Kind.BRIDGE,0,0,15,2);
        var result=KingdomAtlasBuildingOutlines.select(List.of(
            new KingdomAtlasBuildingOutlines.Source(WORLD,inside),
            new KingdomAtlasBuildingOutlines.Source(WORLD,far),
            new KingdomAtlasBuildingOutlines.Source("minecraft:the_nether",nether)
        ),WORLD,0,0,1);
        assertEquals(List.of(inside),result);
    }

    @Test void boundsFootprintCountsAndRejectsAbsurdExtents() {
        var all=new ArrayList<KingdomAtlasBuildingOutlines.Source>();
        for(int i=0;i<100;i++)all.add(new KingdomAtlasBuildingOutlines.Source(
                WORLD,new KingdomAtlasBuildingOutlines.Outline(
                    KingdomAtlasInfrastructure.Kind.BUILDING, i, 0,i+3,6)));
        assertEquals(KingdomAtlasBuildingOutlines.MAX_OUTLINES,
                KingdomAtlasBuildingOutlines.select(all,WORLD,0,0,1).size());
        assertThrows(IllegalArgumentException.class,()->
                new KingdomAtlasBuildingOutlines.Outline(
                    KingdomAtlasInfrastructure.Kind.BUILDING,0,0,1024,2));
        assertThrows(IllegalArgumentException.class,()->
                new KingdomAtlasBuildingOutlines.Outline(
                    KingdomAtlasInfrastructure.Kind.ROAD,2,3,1,5));
    }
}
