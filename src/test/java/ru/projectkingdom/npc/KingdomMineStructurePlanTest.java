package ru.projectkingdom.npc;

import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomMineStructurePlanTest {
    @Test void laddersEveryTwoAndDecksEveryFour() {
        for(int depth=1;depth<=12;depth++){
            assertEquals(depth%2==0,KingdomMineStructurePlan.requiresLadder(depth));
            assertEquals(depth%4==0,KingdomMineStructurePlan.isLevel(depth));
        }
        assertFalse(KingdomMineStructurePlan.requiresLadder(14));
        assertFalse(KingdomMineStructurePlan.isLevel(16));
    }

    @Test void threeBlockBandLeavesCentralOpeningAndNextStairClear() {
        for(int size=7;size<=12;size++){
            var shaft=new KingdomMineSettings.Shaft(0,size-1,0,size-1,
                80,"minecraft:overworld");
            BlockPos core=new BlockPos(size/2,80,size);
            for(int depth:new int[]{4,8,12}){
                var floor=KingdomMineStructurePlan.flooring(shaft,depth,core);
                var unique=new HashSet<>(floor);
                assertEquals(unique.size(),floor.size());
                assertEquals(80-depth-1,floor.get(0).getY());
                assertEquals(size*size-(size-6)*(size-6)-(depth<12?1:0),
                    floor.size());
                BlockPos middle=new BlockPos(size/2,80-depth-1,size/2);
                assertFalse(unique.contains(middle));
                if(depth<12)
                    for(var cell:KingdomMineCentralShaftPlan.cells(core,shaft))
                        if(cell.target().getY()==80-depth-1
                            &&cell.approach().getY()==80-depth)
                            assertFalse(unique.contains(cell.target()));
            }
        }
    }

    @Test void shovelForSoilPickaxeForStone() {
        assertEquals(KingdomMineTools.Tool.SHOVEL,
            KingdomMineTools.required(Blocks.DIRT.defaultBlockState()));
        assertEquals(KingdomMineTools.Tool.SHOVEL,
            KingdomMineTools.required(Blocks.GRAVEL.defaultBlockState()));
        assertEquals(KingdomMineTools.Tool.PICKAXE,
            KingdomMineTools.required(Blocks.STONE.defaultBlockState()));
        assertEquals(KingdomMineTools.Tool.BLOCKED,
            KingdomMineTools.required(Blocks.OAK_PLANKS.defaultBlockState()));
    }
}
