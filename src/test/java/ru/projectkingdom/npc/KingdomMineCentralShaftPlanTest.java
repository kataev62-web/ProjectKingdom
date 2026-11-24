package ru.projectkingdom.npc;

import java.util.HashSet;
import java.util.List;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The layer plan of the mine.
 *
 * <p>The mine of the enterprise is dug layer by layer. The plan must cover
 * every cell of the surveyed footprint exactly once per layer, the cells must
 * be reachable one after the other and the descent must always lie on the
 * southern edge, where the ladder finds the rock to lean on.
 */
class KingdomMineCentralShaftPlanTest {
    private static KingdomMineSettings.Shaft area(int width,int length) {
        return new KingdomMineSettings.Shaft(0,width-1,0,length-1,80,"minecraft:overworld");
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
                    assertEquals(79,cell.target().getY(),"the first layer is under the surface");
                }
            }
    }

    @Test void theCellsOfTheLayerFollowEachOther() {
        var shaft=area(7,7);
        var cells=KingdomMineCentralShaftPlan.layer(shaft,2);
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

    @Test void theDescentLiesOnTheSouthernEdge() {
        var shaft=area(7,7);
        var descent=KingdomMineCentralShaftPlan.descent(shaft,1);
        assertEquals(shaft.maxZ(),descent.getZ(),"the ladder leans on the southern rock");
        assertEquals(79,descent.getY());
        assertEquals(3,descent.getX());
        // The descent is part of the layer and therefore dug with it.
        var cells=KingdomMineCentralShaftPlan.layer(shaft,1);
        assertTrue(cells.stream().anyMatch(cell->cell.target().equals(descent)),
            "the descent is dug with the layer");
    }

    @Test void theWholePlanGoesLayerByLayer() {
        var shaft=area(7,7);
        var plan=KingdomMineCentralShaftPlan.cells(new BlockPos(3,80,7),shaft,2,
            KingdomMineSettings.Heading.NORTH);
        int depth=2*KingdomMineSettings.LEVEL_INTERVAL;
        assertEquals(depth*49,plan.size(),"every layer of seven by seven is planned");
        var seen=new HashSet<BlockPos>();
        for (var cell:plan) assertTrue(seen.add(cell.target()),"duplicate: "+cell);
        // The first layer is completely planned before the second one starts.
        int firstLayer=0;
        for (var cell:plan) if (cell.target().getY()==79) firstLayer++;
        assertEquals(49,firstLayer);
    }

    @Test void rejectsSmallAndUnreachableShafts() {
        assertFalse(KingdomMineCentralShaftPlan.valid(
            area(6,7),new BlockPos(3,80,7)));
        // The business block can be elsewhere and at a different elevation.
        assertTrue(KingdomMineCentralShaftPlan.valid(
            area(7,7),new BlockPos(100,65,-200)));
        assertEquals(new BlockPos(3,80,7),
            KingdomMineCentralShaftPlan.entrance(area(7,7)));
        assertTrue(KingdomMineCentralShaftPlan.cells(
            null,area(7,7),3,KingdomMineSettings.Heading.NORTH).isEmpty());
        assertTrue(KingdomMineCentralShaftPlan.layer(area(7,7),0).isEmpty(),
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
}
