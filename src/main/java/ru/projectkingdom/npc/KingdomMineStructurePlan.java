package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/** Exact construction coordinates. Floor is two blocks IN from the walls;
 * a central opening remains to serve as the continuation of the shaft.
 */
public final class KingdomMineStructurePlan {
    private KingdomMineStructurePlan(){}
    public static final int FLOOR_BAND=2;
    public static boolean isLevel(int depth){
        return depth>0&&depth<=KingdomMineSettings.MAX_LEVELS*KingdomMineSettings.LEVEL_INTERVAL
            &&depth%KingdomMineSettings.LEVEL_INTERVAL==0;
    }
    public static boolean requiresLadder(int depth){
        return depth>0&&depth<=KingdomMineSettings.MAX_LEVELS*KingdomMineSettings.LEVEL_INTERVAL&&depth%2==0;
    }
    public static List<BlockPos> flooring(KingdomMineSettings.Shaft area,int depth){
        return flooring(area,depth,null,3,KingdomMineSettings.Heading.NORTH);
    }

    public static List<BlockPos> flooring(
            KingdomMineSettings.Shaft area,int depth,BlockPos core){
        return flooring(area,depth,core,3,KingdomMineSettings.Heading.NORTH);
    }
    public static List<BlockPos> flooring(
            KingdomMineSettings.Shaft area,int depth,BlockPos core,
            int levels,KingdomMineSettings.Heading heading){
        if(area==null||!isLevel(depth)||depth>levels*KingdomMineSettings.LEVEL_INTERVAL
            ||area.width()<7||area.length()<7)
            return List.of();
        int y=area.surfaceY()-depth-1;
        BlockPos reserved=null;
        if(core!=null&&depth<levels*KingdomMineSettings.LEVEL_INTERVAL)
            for(var cell:KingdomMineCentralShaftPlan.cells(core,area,levels,heading))
                if(cell.target().getY()==y
                    &&cell.approach().getY()==y+1){
                    reserved=cell.target();break;
                }
        List<BlockPos> result=new ArrayList<>();
        for(int x=area.minX();x<=area.maxX();x++)
            for(int z=area.minZ();z<=area.maxZ();z++){
                int edge=Math.min(Math.min(x-area.minX(),area.maxX()-x),
                                  Math.min(z-area.minZ(),area.maxZ()-z));
                if(edge<FLOOR_BAND){
                    BlockPos at=new BlockPos(x,y,z);
                    // The following descending stair step must stay open.
                    if(at.equals(reserved))continue;
                    result.add(at);
                }
            }
        return List.copyOf(result);
    }
}