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
        int[] levels={4,8,12};
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
                    && (cell.target().getY()==76
                        || cell.target().getY()==72
                        || cell.target().getY()==68),
                "out-of-range excavation: "+cell);
            assertTrue(cell.target().getY()>=68&&cell.target().getY()<=80);
            assertTrue(visited.add(cell.target()),"duplicate work cell: "+cell);
            if(deltaY==1)drops++;
            else for(int i=0;i<levels.length;i++)
                if(cell.target().getY()==80-levels[i])perLevel[i]++;
        }
        assertEquals(12,drops,"must descend exactly twelve blocks");
        assertTrue(cells.size()>=12+3*((width-2)*(length-2)+12));
        for(int depth:new int[]{4,8,12}) {
            BlockPos end=new BlockPos((shaft.minX()+shaft.maxX())/2,
                80-depth,shaft.minZ()-12);
            assertTrue(visited.contains(end),"missing level gallery: "+depth);
        }
        for(int count:perLevel)assertTrue(count>=(width-2)*(length-2));
    }

    @Test void centralShaftHasTwelveStepDescentAndThreeConnectedWorkingLevels() {
        check(7,7,3);
        check(8,9,4);
        check(12,12,6);
        check(7,7,0);
        check(7,7,6);
    }

    @Test void everySupportedFootprintAndEntranceOffsetHasNoDuplicateOrDisconnectedCells() {
        for(int width=7;width<=12;width++)
            for(int length=7;length<=12;length++)
                for(int entranceX=0;entranceX<width;entranceX++)
                    check(width,length,entranceX);
    }

    @Test void rejectsSmallShaftAndEntranceOutsideSouthBoundary() {
        var small=new KingdomMineSettings.Shaft(0,3,0,3,80,"minecraft:overworld");
        assertFalse(KingdomMineCentralShaftPlan.valid(small,new BlockPos(1,80,4)));
        assertFalse(KingdomMineCentralShaftPlan.valid(area(7,7,3),
            new BlockPos(3,79,7)));
        assertTrue(KingdomMineCentralShaftPlan.cells(null,area(7,7,3)).isEmpty());
    }

    @Test void shaftSettingsCannotChangeAfterFirstExcavation() {
        var settings=new KingdomMineSettings();
        var mine=java.util.UUID.randomUUID();
        var footprint=area(7,7,3);
        assertTrue(settings.setShaft(mine,footprint));
        assertEquals(7,settings.shaft(mine).width());
        settings.markStarted(mine);
        assertFalse(settings.setShaft(mine,area(8,8,3)));
    }
}
