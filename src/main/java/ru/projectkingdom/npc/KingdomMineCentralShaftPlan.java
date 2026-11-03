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

    public static boolean valid(KingdomMineSettings.Shaft area,BlockPos core) {
        if(area==null||core==null||area.width()<5||area.length()<5
            ||area.width()>12||area.length()>12)return false;
        BlockPos entrance=core.north();
        // The NPC enters from outside through the SOUTH wall of the shaft.
        return entrance.getY()==area.surfaceY()
            && entrance.getZ()==area.maxZ()
            && entrance.getX()>=area.minX()&&entrance.getX()<=area.maxX();
    }

    public static List<KingdomMineGalleryPlan.Cell> cells(
            BlockPos core,KingdomMineSettings.Shaft area) {
        if(!valid(area,core))return List.of();
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
        BlockPos approach=new BlockPos(x,entrance.getY(),area.minZ()+1);
        BlockPos exit=new BlockPos(x,entrance.getY(),area.minZ());
        plan.add(new KingdomMineGalleryPlan.Cell(exit,approach));
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
