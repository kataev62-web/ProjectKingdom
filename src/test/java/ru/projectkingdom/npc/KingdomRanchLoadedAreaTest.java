package ru.projectkingdom.npc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomRanchLoadedAreaTest {
    @Test void acceptsLoadedNeighborChunkWhenPenCenterIsUnloaded(){
        int[] point=KingdomRanchLoadedArea.loadedPoint(15,16,0,0,
            (cx,cz)->cx==1&&cz==0);
        assertArrayEquals(new int[]{16,0},point);
    }

    @Test void doesNotReturnAnUnloadedOrOutOfBoundsPoint(){
        assertNull(KingdomRanchLoadedArea.loadedPoint(15,16,0,0,(cx,cz)->false));
        int[] point=KingdomRanchLoadedArea.loadedPoint(15,16,15,16,
            (cx,cz)->cx==0&&cz==1);
        assertNotNull(point);
        assertTrue(point[0]>=15&&point[0]<=16);
        assertTrue(point[1]>=15&&point[1]<=16);
        assertEquals(0,point[0]>>4);
        assertEquals(1,point[1]>>4);
    }

    @Test void handlesNegativeChunkCoordinates(){
        int[] point=KingdomRanchLoadedArea.loadedPoint(-17,-16,-1,0,
            (cx,cz)->cx==-2&&cz==-1);
        assertArrayEquals(new int[]{-17,-1},point);
    }

    @Test void respectsVerticalPenBounds(){
        assertTrue(KingdomRanchLoadedArea.containsY(64,56));
        assertTrue(KingdomRanchLoadedArea.containsY(64,80));
        assertFalse(KingdomRanchLoadedArea.containsY(64,55));
        assertFalse(KingdomRanchLoadedArea.containsY(64,81));
        assertFalse(KingdomRanchLoadedArea.containsY(Integer.MIN_VALUE,Integer.MAX_VALUE));
    }
}
