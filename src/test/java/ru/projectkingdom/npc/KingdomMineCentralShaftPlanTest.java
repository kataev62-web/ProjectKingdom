package ru.projectkingdom.npc;

import java.util.HashSet;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The layer plan of the mine.
 *
 * <p>The mine of the enterprise is dug layer by layer. The surface of the
 * surveyed footprint is the first layer: the cap is cleared before the
 * excavation goes deeper, so that TFC never has to refuse a block that
 * would hang in the air. Every layer is walked as a serpentine that starts
 * at the descent, so that the miner always stands on the cell it dug before.
 */
class KingdomMineCentralShaftPlanTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static KingdomMineSettings.Shaft area(int width,int length) {
        return new KingdomMineSettings.Shaft(0,width-1,0,length-1,80,"minecraft:overworld");
    }

    @Test void theSurfaceIsTheFirstLayer() {
        var shaft=area(7,7);
        var surface=KingdomMineCentralShaftPlan.layer(shaft,0);
        assertEquals(50,surface.size(),"the shaft under the entrance and the whole surface");
        for(var cell:surface)
            assertEquals(80,cell.target().getY(),"the first layer is the surface");
        var deeper=KingdomMineCentralShaftPlan.layer(shaft,1);
        assertEquals(49,deeper.size(),"every layer below the surface has one cell per square");
        for(var cell:deeper)
            assertEquals(79,cell.target().getY());
    }

    @Test void everyCellOfTheLayerIsPlannedExactlyOnce() {
        for (int width=7;width<=12;width++)
            for (int length=7;length<=12;length++) {
                var shaft=area(width,length);
                var cells=KingdomMineCentralShaftPlan.layer(shaft,1);
                assertEquals(width*length,cells.size(),"the whole layer is planned");
                var seen=new HashSet<BlockPos>();
                for (var cell:cells) {
                    assertTrue(seen.add(cell.target()),"duplicate: "+cell);
                    assertTrue(KingdomMineCentralShaftPlan.inside(shaft,cell.target()),
                        "outside of the footprint: "+cell);
                    assertEquals(79,cell.target().getY());
                }
            }
    }

    @Test void theCellsOfTheLayerFollowEachOther() {
        var shaft=area(7,7);
        var cells=KingdomMineCentralShaftPlan.layer(shaft,1);
        for (int i=0;i<cells.size();i++) {
            var cell=cells.get(i);
            if (i==0) {
                // The first cell of a layer is dug from the layer above.
                assertEquals(1,cell.approach().getY()-cell.target().getY());
                continue;
            }
            var previous=cells.get(i-1).target();
            assertEquals(previous,cell.approach(),"the miner stands where it dug before");
            int distance=Math.abs(previous.getX()-cell.target().getX())
                +Math.abs(previous.getZ()-cell.target().getZ());
            assertEquals(1,distance,"only a step to the side: "+cell);
        }
    }

    @Test void theDescentLiesOnTheSouthernEdgeWithRockOnTwoSides() {
        var shaft=area(7,7);
        var descent=KingdomMineCentralShaftPlan.descent(shaft,1);
        assertEquals(shaft.minX(),descent.getX(),"the descent is the western corner");
        assertEquals(shaft.maxZ(),descent.getZ(),"the descent is on the southern edge");
        assertEquals(79,descent.getY());
        // The corner is the only cell with rock on two sides: the ladder of the
        // descent needs that support.
        assertFalse(KingdomMineCentralShaftPlan.inside(shaft,descent.west()));
        assertFalse(KingdomMineCentralShaftPlan.inside(shaft,descent.south()));
        // The entrance stands right outside of the corner.
        var entrance=KingdomMineCentralShaftPlan.entrance(shaft);
        assertEquals(shaft.minX(),entrance.getX());
        assertEquals(shaft.maxZ()+1,entrance.getZ());
        assertEquals(80,entrance.getY());
        // The shaft under the entrance is the first cell of the surface.
        var surface=KingdomMineCentralShaftPlan.layer(shaft,0);
        assertEquals(entrance,surface.get(0).target(),"the miner comes in through the entrance");
        assertEquals(entrance,surface.get(0).approach().below(),
            "the shaft is dug from the entrance");
    }

    @Test void theWholePlanGoesLayerByLayer() {
        var shaft=area(7,7);
        var plan=KingdomMineCentralShaftPlan.cells(new BlockPos(3,80,7),shaft,2,
            KingdomMineSettings.Heading.NORTH);
        int depth=2*KingdomMineSettings.LEVEL_INTERVAL;
        assertEquals(1+(depth+1)*49,plan.size(),
            "the shaft under the entrance, the surface and the layers below");
        var seen=new HashSet<BlockPos>();
        for (var cell:plan) assertTrue(seen.add(cell.target()),"duplicate: "+cell);
    }

    @Test void rejectsSmallAndUnreachableShafts() {
        assertFalse(KingdomMineCentralShaftPlan.valid(
            area(6,7),new BlockPos(3,80,7)));
        // The business block can be elsewhere and at a different elevation.
        assertTrue(KingdomMineCentralShaftPlan.valid(
            area(7,7),new BlockPos(100,65,-200)));
        assertEquals(new BlockPos(0,80,7),
            KingdomMineCentralShaftPlan.entrance(area(7,7)));
        assertTrue(KingdomMineCentralShaftPlan.cells(
            null,area(7,7),3,KingdomMineSettings.Heading.NORTH).isEmpty());
        assertTrue(KingdomMineCentralShaftPlan.layer(area(7,7),-1).isEmpty(),
            "there is no layer above the surface");
    }

    @Test void surveyedShaftDoesNotDependOnBusinessBlockCoordinates() {
        var shaft=area(7,7);
        var near=new BlockPos(3,80,7);
        var remote=new BlockPos(-1991,78,731);
        assertEquals(KingdomMineCentralShaftPlan.cells(near,shaft,3,
                KingdomMineSettings.Heading.NORTH),
            KingdomMineCentralShaftPlan.cells(remote,shaft,3,
                KingdomMineSettings.Heading.NORTH));
        assertTrue(KingdomMineCentralShaftPlan.valid(shaft,remote));
    }

    @Test void locksChangesAfterRealWorkBegins() {
        var data=new KingdomMineSettings();
        var id=java.util.UUID.randomUUID();
        assertTrue(data.setShaft(id,area(7,7)));
        assertTrue(data.chooseLevels(id,4));
        assertTrue(data.chooseHeading(id,KingdomMineSettings.Heading.WEST));
        assertEquals(4,data.levels(id));
        assertEquals(KingdomMineSettings.Heading.WEST,data.heading(id));
        data.markStarted(id);
        assertFalse(data.chooseLevels(id,2));
        assertFalse(data.chooseHeading(id,KingdomMineSettings.Heading.NORTH));
        assertFalse(data.setShaft(id,area(8,8)));
    }

    @Test void installedDescentLadderAndDeckAreNotMinedAgain() {
        var shaft = area(7, 7);
        int layer = 1;
        BlockPos descent = KingdomMineCentralShaftPlan.descent(shaft, layer);
        assertTrue(KingdomMineLayerWork.completedStructure(shaft, layer, descent,
            net.minecraft.world.level.block.Blocks.LADDER.defaultBlockState()));
        // The deck of the descent lies east of the descent: the north cell
        // belongs to the northern post of the support of TFC.
        assertTrue(KingdomMineLayerWork.completedStructure(shaft, layer, descent.east(),
            net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState()));
        assertFalse(KingdomMineLayerWork.completedStructure(shaft, layer, descent.east(),
            net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()));
        assertFalse(KingdomMineLayerWork.completedStructure(shaft, layer, descent.north(),
            net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState()));
    }
}
