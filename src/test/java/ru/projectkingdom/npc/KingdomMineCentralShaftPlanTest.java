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
        // The extraction exit may already be one of the ten perimeter cells; it must not be counted twice.
        assertTrue(cells.size()>=10+3*((width-2)*(length-2)+12));
        for(int depth:new int[]{3,6,10}) {
            BlockPos end=new BlockPos((shaft.minX()+shaft.maxX())/2,
                80-depth,shaft.minZ()-12);
            assertTrue(visited.contains(end),"missing level gallery: "+depth);
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
    @Test void centralShaftSchedulesRealSupportFramesInEveryExtractionGallery() {
        var shaft=area(8,8,4);
        var anchors=KingdomMineCentralShaftPlan.supportAnchors(shaft);
        assertEquals(6,anchors.size(),"two TFC support frames for each of three working levels");
        Set<Integer> levels=new HashSet<>();
        for(BlockPos anchor:anchors){
            assertTrue(anchor.getZ()==shaft.minZ()-5||anchor.getZ()==shaft.minZ()-10);
            assertEquals((shaft.minX()+shaft.maxX())/2,anchor.getX());
            levels.add(anchor.getY());
        }
        assertEquals(Set.of(77,74,70),levels);
    }

}
