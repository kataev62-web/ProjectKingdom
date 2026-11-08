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
 *  - Five cells per level of walkable descent, with selectable level count around the perimeter.
 *  - At every fifth depth, excavate the INSIDE of the selected footprint,
 *    preserving the outer staircase and its support floor.
 *  - Each room cell has a previously cleared adjacent approach.
 *
 * Unlike a ten-block vertical pit, this allows a normal NPC to walk down.
 * Structure beams and ore rules remain the responsibility of the executor.
 */
public final class KingdomMineCentralShaftPlan {
    private KingdomMineCentralShaftPlan(){}
    public static final int DEPTH=15; // Default: 3 levels, five blocks each.
    public static final int GALLERY_LENGTH=12;
    public static final int[] LEVELS={5,10,15};
    public record Stage(int depth,BlockPos entrance,List<KingdomMineGalleryPlan.Cell> chamber) {}

    public static boolean valid(KingdomMineSettings.Shaft area,BlockPos core) {
        if(area==null||core==null||area.width()<7||area.length()<7
            ||area.width()>12||area.length()>12)return false;
        // Enterprise core is a controller, not a corner or entrance marker.
        // Only the surveyed footprint determines the shaft placement.
        return area.minX()<=area.maxX() && area.minZ()<=area.maxZ();
    }

    /** The independent, walkable entry is one cell SOUTH of the surveyed
     * rectangle, centered on its southern edge. Core position does not matter.
     */
    public static BlockPos entrance(KingdomMineSettings.Shaft area) {
        if(area==null)return null;
        return new BlockPos((area.minX()+area.maxX())/2,
            area.surfaceY(),area.maxZ()+1);
    }

    public static List<KingdomMineGalleryPlan.Cell> cells(
            BlockPos core,KingdomMineSettings.Shaft area) {
        return cells(core,area,3,KingdomMineSettings.Heading.NORTH);
    }

    public static List<KingdomMineGalleryPlan.Cell> cells(
            BlockPos core,KingdomMineSettings.Shaft area,int levels,
            KingdomMineSettings.Heading heading) {
        if(!valid(area,core)||levels<1||levels>KingdomMineSettings.MAX_LEVELS
            ||heading==null)return List.of();
        int depth=levels*KingdomMineSettings.LEVEL_INTERVAL;
        BlockPos entrance=entrance(area);
        List<BlockPos> perimeter=new ArrayList<>();
        // A complete clockwise cycle with no repeated corner cells.
        for(int x=area.minX();x<=area.maxX();x++)
            perimeter.add(new BlockPos(x,area.surfaceY(),area.maxZ()));
        for(int z=area.maxZ()-1;z>=area.minZ();z--)
            perimeter.add(new BlockPos(area.maxX(),area.surfaceY(),z));
        for(int x=area.maxX()-1;x>=area.minX();x--)
            perimeter.add(new BlockPos(x,area.surfaceY(),area.minZ()));
        for(int z=area.minZ()+1;z<area.maxZ();z++)
            perimeter.add(new BlockPos(area.minX(),area.surfaceY(),z));
        // Begin at the centre of the south wall, immediately next to entry.
        BlockPos first=entrance.north();
        int origin=perimeter.indexOf(first);
        if(origin<0)return List.of();
        List<BlockPos> rim=new ArrayList<>(perimeter.size());
        for(int k=0;k<perimeter.size();k++)
            rim.add(perimeter.get((origin+k)%perimeter.size()));
        List<KingdomMineGalleryPlan.Cell> result=new ArrayList<>();
        List<BlockPos> landings=new ArrayList<>();
        BlockPos previous=entrance;
        for(int step=1;step<=depth;step++) {
            BlockPos down=rim.get((step-1)%rim.size()).below(step);
            result.add(new KingdomMineGalleryPlan.Cell(down,previous));
            previous=down;
            if(step%KingdomMineSettings.LEVEL_INTERVAL==0)landings.add(down);
        }
        // All shaft excavation runs first. Only then do NPCs open levels.
        // Structural wooden decks are installed after each gallery completes.
        for(BlockPos landing:landings)result.addAll(chamber(area,landing,heading));
        return List.copyOf(result);
    }

    private static List<KingdomMineGalleryPlan.Cell> chamber(
            KingdomMineSettings.Shaft area,BlockPos entrance,
            KingdomMineSettings.Heading heading) {
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
        // Create one physical gallery in the enterprise-selected cardinal
        // direction; use the already excavated central chamber as its approach.
        Direction direction=heading.direction();
        int x=(area.minX()+area.maxX())/2;
        int z=(area.minZ()+area.maxZ())/2;
        BlockPos exit=switch(heading){
            case NORTH -> new BlockPos(x,entrance.getY(),area.minZ());
            case SOUTH -> new BlockPos(x,entrance.getY(),area.maxZ());
            case WEST -> new BlockPos(area.minX(),entrance.getY(),z);
            case EAST -> new BlockPos(area.maxX(),entrance.getY(),z);
        };
        BlockPos inner=exit.relative(direction.getOpposite());
        if(!exit.equals(entrance))
            plan.add(new KingdomMineGalleryPlan.Cell(exit,inner));
        for(int distance=1;distance<=GALLERY_LENGTH;distance++)
            plan.add(new KingdomMineGalleryPlan.Cell(
                exit.relative(direction,distance),
                exit.relative(direction,distance-1)));
        return plan;
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

    private static boolean inside(KingdomMineSettings.Shaft a,BlockPos p) {
        return p.getX()>=a.minX()&&p.getX()<=a.maxX()
            &&p.getZ()>=a.minZ()&&p.getZ()<=a.maxZ();
    }

    private static boolean interior(KingdomMineSettings.Shaft a,BlockPos p) {
        return p.getX()>a.minX()&&p.getX()<a.maxX()
            &&p.getZ()>a.minZ()&&p.getZ()<a.maxZ();
    }
}
