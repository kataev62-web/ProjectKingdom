package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

/**
 * The layer plan of the shaft of the mine.
 *
 * <p>The shaft of the mine is dug layer by layer. The miner clears the whole
 * horizontal layer of the surveyed footprint, walks to the next layer and digs
 * it. The layers are walked in a serpentine, so that the miner always stands
 * next to the block it digs and never above a hole.
 *
 * <p>One cell of the southern edge is the descent: it is dug on every layer
 * and carries the ladder, so that there is always a safe way down and a safe
 * way up. The ladder leans against the untouched rock south of the footprint.
 *
 * <p>The plan is derived from the footprint and the surface, never from a
 * counter: the progress of the excavation is the world itself, so it survives
 * a rest, an unloaded chunk and a restart of the server.
 */
public final class KingdomMineCentralShaftPlan {
    private KingdomMineCentralShaftPlan(){}
    /** Kept for the callers of the old plan; the gallery of the mine is the layer now. */
    public static final int GALLERY_LENGTH=12;

    public static boolean valid(KingdomMineSettings.Shaft area,BlockPos core) {
        if(area==null||core==null||area.width()<7||area.length()<7
            ||area.width()>12||area.length()>12)return false;
        // Enterprise core is a controller, not a corner or entrance marker.
        // Only the surveyed footprint determines the shaft placement.
        return area.minX()<=area.maxX() && area.minZ()<=area.maxZ();
    }

    /**
     * The independent, walkable entry is one cell SOUTH of the surveyed
     * rectangle, centered on its southern edge.
     */
    public static BlockPos entrance(KingdomMineSettings.Shaft area) {
        if(area==null)return null;
        // The entrance stands on the southern edge of the western corner: the
        // corner is the only cell of the footprint that has rock on two
        // sides, so that the ladder of the descent always finds its support.
        return new BlockPos(area.minX(),area.surfaceY(),area.maxZ()+1);
    }

    /**
     * The descent cell of a layer.
     *
     * <p>The cell lies on the southern edge of the footprint, so that the
     * ladder finds the untouched rock to lean on.
     */
    public static BlockPos descent(KingdomMineSettings.Shaft area,int layer) {
        if(area==null)return null;
        return new BlockPos(area.minX(),area.surfaceY()-layer,area.maxZ());
    }

    /** The depth of a layer. */
    public static int depthOf(KingdomMineSettings.Shaft area,int layer) {
        return area==null?0:layer;
    }

    /**
     * The cells of one horizontal layer, in the order of the excavation.
     *
     * <p>The rows are walked as a serpentine: the first row from west to east,
     * the second one back, and so on. The approach of a cell is the cell that
     * was dug before it, so that the miner always stands on solid ground next
     * to the block it digs.
     */
    public static List<KingdomMineGalleryPlan.Cell> layer(
            KingdomMineSettings.Shaft area,int layer) {
        // The layer zero is the surface of the footprint: the cap is cleared
        // before the excavation goes deeper.
        if(area==null||layer<0)return List.of();
        int y=area.surfaceY()-layer;
        List<KingdomMineGalleryPlan.Cell> cells=new ArrayList<>();
        BlockPos descent=descent(area,layer);
        if(layer==0){
            // The first layer is the surface of the footprint. The miner
            // walks in through the entrance and opens the shaft of the mine.
            // The surface is cleared first, so that no block of the cap is
            // left hanging over the excavation.
            BlockPos shaft=new BlockPos(descent.getX(),y,area.maxZ()+1);
            cells.add(new KingdomMineGalleryPlan.Cell(shaft,shaft.above()));
            cells.add(new KingdomMineGalleryPlan.Cell(descent,shaft));
        }else{
            // The descent cell is dug from the layer above, where the ladder
            // of the previous layer stands.
            cells.add(new KingdomMineGalleryPlan.Cell(descent,descent.above()));
        }
        // The rest of the layer is a serpentine that starts next to the
        // descent, so that the miner always stands on the cell it dug before.
        BlockPos previous=descent;
        boolean eastward=true;
        for(int z=area.maxZ();z>=area.minZ();z--){
            if(eastward){
                for(int x=area.minX();x<=area.maxX();x++){
                    BlockPos target=new BlockPos(x,y,z);
                    if(target.equals(descent))continue;
                    cells.add(new KingdomMineGalleryPlan.Cell(target,previous));
                    previous=target;
                }
            }else{
                for(int x=area.maxX();x>=area.minX();x--){
                    BlockPos target=new BlockPos(x,y,z);
                    if(target.equals(descent))continue;
                    cells.add(new KingdomMineGalleryPlan.Cell(target,previous));
                    previous=target;
                }
            }
            eastward=!eastward;
        }
        return List.copyOf(cells);
    }

    /**
     * Every cell of the shaft, layer by layer.
     *
     * <p>The order matters: the whole layer is cleared before the next one
     * starts, so that the miner never digs below its own feet.
     */
    public static List<KingdomMineGalleryPlan.Cell> cells(
            BlockPos core,KingdomMineSettings.Shaft area,int levels,
            KingdomMineSettings.Heading heading) {
        if(!valid(area,core)||levels<1||levels>KingdomMineSettings.MAX_LEVELS
            ||heading==null)return List.of();
        int depth=levels*KingdomMineSettings.LEVEL_INTERVAL;
        List<KingdomMineGalleryPlan.Cell> result=new ArrayList<>();
        // The surface is the first layer, the shaft under the entrance with it.
        for(int layer=0;layer<=depth;layer++)
            result.addAll(layer(area,layer));
        return List.copyOf(result);
    }

    /** The cells of the first layers that still hold a block. */
    public static List<KingdomMineGalleryPlan.Cell> cells(
            BlockPos core,KingdomMineSettings.Shaft area) {
        return cells(core,area,3,KingdomMineSettings.Heading.NORTH);
    }

    /** Last physical cell of a gallery for a chosen horizon and heading. */
    public static BlockPos galleryEnd(KingdomMineSettings.Shaft area,
            int depth,KingdomMineSettings.Heading heading) {
        if(area==null||heading==null||depth<1)return null;
        int x=(area.minX()+area.maxX())/2;
        int z=(area.minZ()+area.maxZ())/2;
        BlockPos edge=switch(heading){
            case NORTH -> new BlockPos(x,area.surfaceY()-depth,area.minZ());
            case SOUTH -> new BlockPos(x,area.surfaceY()-depth,area.maxZ());
            case WEST -> new BlockPos(area.minX(),area.surfaceY()-depth,z);
            case EAST -> new BlockPos(area.maxX(),area.surfaceY()-depth,z);
        };
        return edge.relative(heading.direction(),GALLERY_LENGTH);
    }

    /** Whether the cell belongs to the footprint. */
    public static boolean inside(KingdomMineSettings.Shaft a,BlockPos p) {
        return p.getX()>=a.minX()&&p.getX()<=a.maxX()
            &&p.getZ()>=a.minZ()&&p.getZ()<=a.maxZ();
    }

    /** Whether the cell lies inside the footprint without its border. */
    public static boolean interior(KingdomMineSettings.Shaft a,BlockPos p) {
        return p.getX()>a.minX()&&p.getX()<a.maxX()
            &&p.getZ()>a.minZ()&&p.getZ()<a.maxZ();
    }
}
