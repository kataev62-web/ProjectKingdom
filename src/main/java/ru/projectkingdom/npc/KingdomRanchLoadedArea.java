package ru.projectkingdom.npc;

import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Loaded, bounded portion of a ranch pen. Never requests a chunk load.
 * A pen may straddle several chunks; a worker can use the loaded portion
 * even when another part of the pen is offline.
 */
final class KingdomRanchLoadedArea {
    static final int BELOW=8;
    static final int ABOVE=16;

    private KingdomRanchLoadedArea(){}

    static boolean containsY(int floorY,int entityY){
        long offset=(long)entityY-floorY;
        return offset>=-BELOW&&offset<=ABOVE;
    }

    /** Returns a block (x,z) within a loaded chunk of the pen, or null. */
    static int[] loadedPoint(int minX,int maxX,int minZ,int maxZ,
                             BiPredicate<Integer,Integer> loadedChunk){
        Objects.requireNonNull(loadedChunk);
        if(minX>maxX||minZ>maxZ)throw new IllegalArgumentException("invalid pen bounds");
        int centerX=minX+(maxX-minX)/2;
        int centerZ=minZ+(maxZ-minZ)/2;
        if(loadedChunk.test(centerX>>4,centerZ>>4))return new int[]{centerX,centerZ};
        for(int cx=minX>>4;cx<=maxX>>4;cx++){
            for(int cz=minZ>>4;cz<=maxZ>>4;cz++){
                if(!loadedChunk.test(cx,cz))continue;
                int x=Math.max(minX,Math.min(maxX,(cx<<4)+8));
                int z=Math.max(minZ,Math.min(maxZ,(cz<<4)+8));
                if((x>>4)==cx&&(z>>4)==cz)return new int[]{x,z};
            }
        }
        return null;
    }
}
