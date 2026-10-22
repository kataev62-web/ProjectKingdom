package ru.projectkingdom.npc;

import java.util.*;
/** Deterministic, dependency-free design and territory validation core. No blocks are created here. */
public final class KingdomDesignEngine {
    private KingdomDesignEngine() {}
    public record Cell(int x, int y, int z, String material) {}
    public record Design(String name, int width, int length, int height, List<Cell> blocks) {
        public Design { blocks = List.copyOf(blocks); }
        public Map<String,Integer> bill() {
            Map<String,Integer> bill = new TreeMap<>();
            for (Cell c : blocks) bill.merge(c.material(), 1, Integer::sum);
            return bill;
        }
    }
    public interface LandRights { boolean allowed(int x, int z); }
    public static boolean footprintAllowed(Design d, int x, int z, LandRights rights) {
        if(d.width()<1 || d.length()<1 || d.width()>64 || d.length()>64) return false;
        for(int dx=0;dx<d.width();dx++) for(int dz=0;dz<d.length();dz++)
            if(!rights.allowed(x+dx,z+dz)) return false;
        return true;
    }
    /** Architect generates a coherent two-floor medieval shell; interior stations are installed separately. */
    public static Design medievalHouse(String name, int width, int length, int floors) {
        if(width<7 || length<7 || width>31 || length>31 || floors<1 || floors>3) throw new IllegalArgumentException("Dimensions outside supported range");
        int wallHeight=floors*4;
        Map<Long,Cell> cells=new LinkedHashMap<>();
        for(int x=0;x<width;x++) for(int z=0;z<length;z++) {
            put(cells,x,0,z,"minecraft:cobblestone");
            for(int floor=1;floor<floors;floor++) put(cells,x,floor*4,z,"minecraft:spruce_planks");
        }
        for(int y=1;y<=wallHeight;y++) for(int x=0;x<width;x++) for(int z=0;z<length;z++) {
            if(x!=0 && x!=width-1 && z!=0 && z!=length-1) continue;
            if(z==0 && x==width/2 && (y==1 || y==2)) continue; // door
            boolean beam=(x==0 || x==width-1) && (z==0 || z==length-1) || y%4==0;
            boolean window=(y%4==2 && ((x==0 || x==width-1) && z%4==2 || (z==0 || z==length-1) && x%4==2));
            String material=beam?"minecraft:spruce_log":window?"minecraft:glass_pane":y<=4?"minecraft:stone_bricks":"minecraft:white_terracotta";
            put(cells,x,y,z,material);
        }
        // Symmetric gabled roof along Z axis, with one-block eaves.
        int ridge=(width+1)/2;
        for(int rise=0;rise<=ridge;rise++) {
            int left=rise-1,right=width-rise;
            if(left>right) break;
            for(int z=-1;z<=length;z++) {
                put(cells,left,wallHeight+1+rise,z,"minecraft:dark_oak_planks");
                put(cells,right,wallHeight+1+rise,z,"minecraft:dark_oak_planks");
            }
        }
        return new Design(name,width,length,wallHeight+ridge+2,new ArrayList<>(cells.values()));
    }
    private static void put(Map<Long,Cell> m,int x,int y,int z,String material) {
        long key=(((long)(x+128))<<42)|(((long)(y+128))<<21)|(z+128);
        m.put(key,new Cell(x,y,z,material));
    }
}
