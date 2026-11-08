package ru.projectkingdom.npc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * MineColonies-like bounded central shaft:
 *  - Ten consecutive, walkable descending cells around the perimeter.
 *  - At depths 3, 6 and 10, excavate the INSIDE of the selected footprint,
 *    preserving the outer staircase and its support floor.
 *  - Each room cell has a previously cleared adjacent approach.
 *
 * Unlike a ten-block vertical pit, this allows a normal NPC to walk down.
 * Structure beams and ore rules remain the responsibility of the executor.
 */
public final class KingdomMineCentralShaftPlan {
    private KingdomMineCentralShaftPlan(){}
    public static final int DEPTH=10;
    public static final int GALLERY_LENGTH=12;
    public static final int[] LEVELS={3,6,10};
    public record Stage(int depth,BlockPos entrance,List<KingdomMineGalleryPlan.Cell> chamber) {}

    /** Direction from the enterprise core into the selected footprint. */
    public static Direction entryDirection(KingdomMineSettings.Shaft area,BlockPos core) {
        if(area==null||core==null||area.width()<5||area.length()<5
            ||area.width()>12||area.length()>12
            ||core.getY()!=area.surfaceY() || area.contains(core))return null;
        for(Direction side:Direction.Plane.HORIZONTAL) {
            BlockPos entry=core.relative(side);
            if(!area.contains(entry))continue;
            if(side==Direction.NORTH && entry.getZ()==area.maxZ()
                ||side==Direction.SOUTH && entry.getZ()==area.minZ()
                ||side==Direction.EAST && entry.getX()==area.minX()
                ||side==Direction.WEST && entry.getX()==area.maxX())return side;
        }
        return null;
    }

    public static boolean valid(KingdomMineSettings.Shaft area,BlockPos core) {
        return entryDirection(area,core)!=null;
    }

    public static BlockPos entrance(KingdomMineSettings.Shaft area,BlockPos core) {
        Direction side=entryDirection(area,core);
        return side==null?null:core.relative(side);
    }

    /** Rotate into a canonical north-facing footprint; the original MineColonies
     * excavation order is reused unchanged for all four sides of the core.
     */
    private static BlockPos intoNorth(BlockPos pos,Direction side) {
        return switch(side) {
            case SOUTH -> new BlockPos(-pos.getX(),pos.getY(),-pos.getZ());
            case EAST -> new BlockPos(pos.getZ(),pos.getY(),-pos.getX());
            case WEST -> new BlockPos(-pos.getZ(),pos.getY(),pos.getX());
            default -> pos;
        };
    }
    private static BlockPos fromNorth(BlockPos pos,Direction side) {
        return switch(side) {
            case SOUTH -> new BlockPos(-pos.getX(),pos.getY(),-pos.getZ());
            case EAST -> new BlockPos(-pos.getZ(),pos.getY(),pos.getX());
            case WEST -> new BlockPos(pos.getZ(),pos.getY(),-pos.getX());
            default -> pos;
        };
    }
    private static KingdomMineSettings.Shaft canonical(
            KingdomMineSettings.Shaft area,Direction side) {
        BlockPos a=intoNorth(new BlockPos(area.minX(),area.surfaceY(),area.minZ()),side);
        BlockPos b=intoNorth(new BlockPos(area.maxX(),area.surfaceY(),area.maxZ()),side);
        return new KingdomMineSettings.Shaft(
            Math.min(a.getX(),b.getX()),Math.max(a.getX(),b.getX()),
            Math.min(a.getZ(),b.getZ()),Math.max(a.getZ(),b.getZ()),
            area.surfaceY(),area.dimension());
    }

    /** The inner anchor at the far side, in the direction of mining. */
    public static BlockPos galleryAnchor(KingdomMineSettings.Shaft area,BlockPos core,int y) {
        Direction side=entryDirection(area,core);
        if(side==null)return null;
        var rotated=canonical(area,side);
        return fromNorth(new BlockPos((rotated.minX()+rotated.maxX())/2,
                y,rotated.minZ()+1),side);
    }
    public static boolean beyondGallery(KingdomMineSettings.Shaft area,BlockPos core,BlockPos cell) {
        Direction side=entryDirection(area,core);
        if(side==null)return false;
        var rotated=canonical(area,side);
        BlockPos p=intoNorth(cell,side);
        return p.getX()==(rotated.minX()+rotated.maxX())/2
            && p.getZ()<rotated.minZ();
    }

    public static List<KingdomMineGalleryPlan.Cell> cells(
            BlockPos core,KingdomMineSettings.Shaft area) {
        Direction side=entryDirection(area,core);
        if(side==null)return List.of();
        if(side==Direction.NORTH)return northCells(core,area);
        var original=northCells(intoNorth(core,side),canonical(area,side));
        return original.stream()
            .map(c->new KingdomMineGalleryPlan.Cell(
                fromNorth(c.target(),side),fromNorth(c.approach(),side)))
            .toList();
    }

    private static List<KingdomMineGalleryPlan.Cell> northCells(
            BlockPos core,KingdomMineSettings.Shaft area) {
        if(core==null || area==null || core.north().getZ()!=area.maxZ())return List.of();
        List<BlockPos> rim=new ArrayList<>();
        BlockPos entrance=core.north();
        // Move west, north, east, south, using each perimeter coordinate once.
        for(int x=entrance.getX()-1;x>=area.minX();x--)
            rim.add(new BlockPos(x,area.surfaceY(),area.maxZ()));
        for(int z=area.maxZ()-1;z>=area.minZ();z--)
            rim.add(new BlockPos(area.minX(),area.surfaceY(),z));
        for(int x=area.minX()+1;x<=area.maxX();x++)
            rim.add(new BlockPos(x,area.surfaceY(),area.minZ()));
        for(int z=area.minZ()+1;z<area.maxZ();z++)
            rim.add(new BlockPos(area.maxX(),area.surfaceY(),z));
        for(int x=area.maxX()-1;x>entrance.getX();x--)
            rim.add(new BlockPos(x,area.surfaceY(),area.maxZ()));
        if(rim.size()<DEPTH)return List.of();
        List<KingdomMineGalleryPlan.Cell> result=new ArrayList<>();
        BlockPos previous=entrance;
        for(int step=1;step<=DEPTH;step++) {
            BlockPos down=rim.get(step-1).below(step);
            result.add(new KingdomMineGalleryPlan.Cell(down,previous));
            previous=down;
            for(int level:LEVELS)if(step==level)
                result.addAll(chamber(area,down));
        }
        return List.copyOf(result);
    }

    private static List<KingdomMineGalleryPlan.Cell> chamber(
            KingdomMineSettings.Shaft area,BlockPos entrance) {
        List<KingdomMineGalleryPlan.Cell> plan=new ArrayList<>();
        Set<BlockPos> visited=new HashSet<>();
        ArrayDeque<BlockPos> queue=new ArrayDeque<>();
        // Seed from a walkable rim position; BFS produces reachable approaches.
        BlockPos approach=entrance;
        BlockPos seed=null;
        for(Direction direction:Direction.Plane.HORIZONTAL) {
            BlockPos possible=entrance.relative(direction);
            if(interior(area,possible)){seed=possible;break;}
        }
        // Corner stair landings have no directly adjacent inner cell.
        // Open exactly one rim connector, then enter the interior.
        if(seed==null)outer:
            for(Direction direction:Direction.Plane.HORIZONTAL) {
                BlockPos connector=entrance.relative(direction);
                if(!inside(area,connector))continue;
                for(Direction into:Direction.Plane.HORIZONTAL) {
                    BlockPos possible=connector.relative(into);
                    if(interior(area,possible)){
                        plan.add(new KingdomMineGalleryPlan.Cell(connector,entrance));
                        approach=connector;
                        seed=possible;
                        break outer;
                    }
                }
            }
        if(seed==null)return List.of();
        visited.add(seed);
        queue.add(seed);
        plan.add(new KingdomMineGalleryPlan.Cell(seed,approach));
        while(!queue.isEmpty()) {
            BlockPos parent=queue.removeFirst();
            for(Direction direction:Direction.Plane.HORIZONTAL) {
                BlockPos target=parent.relative(direction);
                if(interior(area,target)&&visited.add(target)){
                    plan.add(new KingdomMineGalleryPlan.Cell(target,parent));
                    queue.addLast(target);
                }
            }
        }
        // Each level opens one north-facing extraction gallery beyond the
        // central shaft; the miner still checks legal ownership at every cell.
        int x=(area.minX()+area.maxX())/2;
        BlockPos galleryApproach=new BlockPos(x,entrance.getY(),area.minZ()+1);
        BlockPos exit=new BlockPos(x,entrance.getY(),area.minZ());
        // At some entrance offsets the descending stair already cleared the
        // north rim cell at this Y. Never schedule it twice.
        if(!exit.equals(entrance))
            plan.add(new KingdomMineGalleryPlan.Cell(exit,galleryApproach));
        for(int distance=1;distance<=GALLERY_LENGTH;distance++){
            BlockPos target=exit.north(distance);
            BlockPos previous=distance==1?exit:exit.north(distance-1);
            plan.add(new KingdomMineGalleryPlan.Cell(target,previous));
        }
        return plan;
    }

    private static boolean inside(KingdomMineSettings.Shaft a,BlockPos p) {
        return p.getX()>=a.minX()&&p.getX()<=a.maxX()
            &&p.getZ()>=a.minZ()&&p.getZ()<=a.maxZ();
    }

    private static boolean interior(KingdomMineSettings.Shaft a,BlockPos p) {
        return p.getX()>a.minX()&&p.getX()<a.maxX()
            &&p.getZ()>a.minZ()&&p.getZ()<a.maxZ();
    }
}
