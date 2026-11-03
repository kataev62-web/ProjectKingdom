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
        for(Direction direction:Direction.Plane.HORIZONTAL) {
            BlockPos first=entrance.relative(direction);
            if(interior(area,first)) {
                visited.add(first);
                queue.add(first);
                plan.add(new KingdomMineGalleryPlan.Cell(first,entrance));
                break;
            }
        }
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
        return plan;
    }

    private static boolean interior(KingdomMineSettings.Shaft a,BlockPos p) {
        return p.getX()>a.minX()&&p.getX()<a.maxX()
            &&p.getZ()>a.minZ()&&p.getZ()<a.maxZ();
    }
}
