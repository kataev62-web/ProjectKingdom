package ru.projectkingdom.npc;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomMineCentralShaftPlanTest {
    private static KingdomMineSettings.Shaft area(int width,int length,int entranceX) {
        return new KingdomMineSettings.Shaft(0,width-1,0,length-1,80,"minecraft:overworld");
    }

    private static void check(int width,int length,int entranceX) {
        var shaft=area(width,length,entranceX);
        BlockPos core=new BlockPos(entranceX,80,length);
        assertTrue(KingdomMineCentralShaftPlan.valid(shaft,core));
        var cells=KingdomMineCentralShaftPlan.cells(core,shaft);
        Set<BlockPos> visited=new HashSet<>();
        visited.add(core.north());
        int[] levels={3,6,10};
        int[] perLevel=new int[3];
        int drops=0;
        for(var cell:cells) {
            assertTrue(visited.contains(cell.approach()),"unreachable approach: "+cell);
            int horizontal=Math.abs(cell.target().getX()-cell.approach().getX())
                +Math.abs(cell.target().getZ()-cell.approach().getZ());
            int deltaY=cell.approach().getY()-cell.target().getY();
            assertEquals(1,horizontal,"diagonal step: "+cell);
            assertTrue(deltaY==0||deltaY==1,"unsafe drop: "+cell);
            assertTrue(shaft.contains(cell.target())
                || cell.target().getX()==(shaft.minX()+shaft.maxX())/2
                    && cell.target().getZ()<shaft.minZ()
                    && (cell.target().getY()==77
                        || cell.target().getY()==74
                        || cell.target().getY()==70),
                "out-of-range excavation: "+cell);
            assertTrue(cell.target().getY()>=70&&cell.target().getY()<=80);
            assertTrue(visited.add(cell.target()),"duplicate work cell: "+cell);
            if(deltaY==1)drops++;
            else for(int i=0;i<levels.length;i++)
                if(cell.target().getY()==80-levels[i])perLevel[i]++;
        }
        assertEquals(10,drops,"must descend exactly ten blocks");
        for(int depth:new int[]{3,6,10}) {
            for(int x=shaft.minX()+1;x<shaft.maxX();x++)
                for(int z=shaft.minZ()+1;z<shaft.maxZ();z++)
                    assertTrue(visited.contains(new BlockPos(x,80-depth,z)),
                        "missing chamber cell at depth "+depth+": "+x+","+z);
            for(int distance=0;distance<=12;distance++)
                assertTrue(visited.contains(new BlockPos((shaft.minX()+shaft.maxX())/2,
                    80-depth,shaft.minZ()-distance)),"missing gallery cell: "+distance);
        }
        for(int count:perLevel)assertTrue(count>=(width-2)*(length-2));
    }

    @Test void centralShaftHasTenStepDescentAndThreeConnectedWorkingLevels() {
        check(5,5,2);
        check(6,7,3);
        check(12,12,6);
        check(5,5,0);
        check(5,5,4);
    }

    @Test void rejectsSmallShaftAndEntranceOutsideSouthBoundary() {
        var small=new KingdomMineSettings.Shaft(0,3,0,3,80,"minecraft:overworld");
        assertFalse(KingdomMineCentralShaftPlan.valid(small,new BlockPos(1,80,4)));
        assertFalse(KingdomMineCentralShaftPlan.valid(area(5,5,2),
            new BlockPos(2,79,5)));
        assertTrue(KingdomMineCentralShaftPlan.cells(null,area(5,5,2)).isEmpty());
    }

    @Test void shaftSettingsCannotChangeAfterFirstExcavation() {
        var settings=new KingdomMineSettings();
        var mine=java.util.UUID.randomUUID();
        var footprint=area(5,5,2);
        assertTrue(settings.setShaft(mine,footprint));
        assertEquals(5,settings.shaft(mine).width());
        settings.markStarted(mine);
        assertFalse(settings.setShaft(mine,area(6,6,2)));
    }

    @Test void galleryExitIsUniqueAndConnectedOnEveryLevel() {
        for(int entranceX:new int[]{0,2,4}) {
            var shaft=area(5,5,entranceX);
            var cells=KingdomMineCentralShaftPlan.cells(new BlockPos(entranceX,80,5),shaft);
            for(int depth:new int[]{3,6,10}) {
                BlockPos exit=new BlockPos(2,80-depth,0);
                BlockPos expectedApproach=new BlockPos(2,80-depth,1);
                int exitIndex=-1;
                for(int i=0;i<cells.size();i++)
                    if(cells.get(i).target().equals(exit)){exitIndex=i;break;}
                assertTrue(exitIndex>0,"missing gallery exit at depth "+depth);
                assertEquals(1,cells.stream().filter(c->c.target().equals(exit)).count(),
                    "a stair landing shared with the gallery must not be excavated twice");
                var exitCell=cells.get(exitIndex);
                if(exitCell.approach().getY()==exit.getY()) {
                    assertEquals(expectedApproach,exitCell.approach(),
                        "gallery exit must use its inner cell, not the chamber seed approach");
                    Set<BlockPos> cleared=new HashSet<>();
                    for(int i=0;i<exitIndex;i++)cleared.add(cells.get(i).target());
                    assertTrue(cleared.contains(expectedApproach),"gallery approach must already be cleared");
                } else {
                    assertEquals(exit.getY()+1,exitCell.approach().getY(),"shared exit is a stair landing");
                }
                var firstGallery=cells.stream().filter(c->c.target().equals(exit.north())).findFirst().orElseThrow();
                assertEquals(exit,firstGallery.approach());
            }
        }
    }

    @Test void shaftContainsInclusiveFootprintAtAnyHeight() {
        var shaft=new KingdomMineSettings.Shaft(-4,0,9,13,80,"minecraft:overworld");
        assertTrue(shaft.contains(new BlockPos(-4,-64,9)));
        assertTrue(shaft.contains(new BlockPos(0,320,13)));
        assertFalse(shaft.contains(new BlockPos(-5,80,9)));
        assertFalse(shaft.contains(new BlockPos(1,80,13)));
        assertFalse(shaft.contains(new BlockPos(-4,80,8)));
        assertFalse(shaft.contains(new BlockPos(0,80,14)));
    }
}
