package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/** Exact construction coordinates. Floor is three blocks IN from the walls;
 * a central opening remains to serve as the continuation of the shaft.
 */
public final class KingdomMineStructurePlan {
    private KingdomMineStructurePlan(){}
    public static final int FLOOR_BAND=3;
    public static boolean isLevel(int depth){
        return depth>0&&depth<=KingdomMineCentralShaftPlan.DEPTH&&depth%4==0;
    }
    public static boolean requiresLadder(int depth){
        return depth>0&&depth<=KingdomMineCentralShaftPlan.DEPTH&&depth%2==0;
    }
    public static List<BlockPos> flooring(KingdomMineSettings.Shaft area,int depth){
        if(area==null||!isLevel(depth)||area.width()<7||area.length()<7)
            return List.of();
        int y=area.surfaceY()-depth-1;
        List<BlockPos> result=new ArrayList<>();
        for(int x=area.minX();x<=area.maxX();x++)
            for(int z=area.minZ();z<=area.maxZ();z++){
                int edge=Math.min(Math.min(x-area.minX(),area.maxX()-x),
                                  Math.min(z-area.minZ(),area.maxZ()-z));
                if(edge<FLOOR_BAND)
                    result.add(new BlockPos(x,y,z));
            }
        return List.copyOf(result);
    }
}