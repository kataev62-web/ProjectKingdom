package ru.projectkingdom.npc;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomMineCardinalEntrancesTest {
    private static final KingdomMineSettings.Shaft SHAFT =
        new KingdomMineSettings.Shaft(0,6,0,6,80,"minecraft:overworld");

    @Test void acceptsAllFourAdjacentCoreSidesAndProducesConnectedCells() {
        BlockPos[] cores = {
            new BlockPos(3,80,7), new BlockPos(3,80,-1),
            new BlockPos(-1,80,3), new BlockPos(7,80,3)
        };
        Direction[] directions = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
        };
        for(int i=0;i<cores.length;i++) {
            BlockPos core=cores[i];
            Direction side=directions[i];
            assertEquals(side,KingdomMineCentralShaftPlan.entryDirection(SHAFT,core));
            assertEquals(core.relative(side),KingdomMineCentralShaftPlan.entrance(SHAFT,core));
            var cells=KingdomMineCentralShaftPlan.cells(core,SHAFT);
            assertFalse(cells.isEmpty());
            Set<BlockPos> reachable=new HashSet<>();
            reachable.add(core.relative(side));
            int drops=0;
            int galleries=0;
            for(var cell:cells) {
                assertTrue(reachable.contains(cell.approach()),"unreachable "+side+": "+cell);
                assertEquals(1,Math.abs(cell.target().getX()-cell.approach().getX())
                    +Math.abs(cell.target().getZ()-cell.approach().getZ()));
                int down=cell.approach().getY()-cell.target().getY();
                assertTrue(down==0 || down==1);
                if(down==1)drops++;
                assertTrue(reachable.add(cell.target()),"duplicate "+side+": "+cell);
                if(KingdomMineCentralShaftPlan.beyondGallery(SHAFT,core,cell.target()))
                    galleries++;
                else assertTrue(SHAFT.contains(cell.target()),"outside footprint "+cell);
            }
            assertEquals(10,drops,"wrong descent for "+side);
            assertTrue(galleries>=36,"missing three galleries for "+side);
            var anchor=KingdomMineCentralShaftPlan.galleryAnchor(SHAFT,core,77);
            assertNotNull(anchor);
            for(var support:KingdomMineSupportPlan.frames(anchor,side)) {
                assertTrue(KingdomMineSupportPlan.valid(support));
                assertEquals(anchor.relative(side,support.depth()),support.walkway());
            }
        }
    }

    @Test void rejectsNonAdjacentCoreWithoutSchedulingExcavation() {
        BlockPos diagonal=new BlockPos(-1,80,-1);
        BlockPos remote=new BlockPos(3,80,12);
        BlockPos wrongHeight=new BlockPos(3,79,7);
        for(var core:new BlockPos[]{diagonal,remote,wrongHeight}) {
            assertNull(KingdomMineCentralShaftPlan.entryDirection(SHAFT,core));
            assertTrue(KingdomMineCentralShaftPlan.cells(core,SHAFT).isEmpty());
        }
    }
}
