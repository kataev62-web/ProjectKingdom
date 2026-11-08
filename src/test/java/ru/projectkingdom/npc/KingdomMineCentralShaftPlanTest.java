package ru.projectkingdom.npc;

import java.util.HashSet;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomMineCentralShaftPlanTest {
    private static KingdomMineSettings.Shaft area(int width,int length) {
        return new KingdomMineSettings.Shaft(0,width-1,0,length-1,80,"minecraft:overworld");
    }

    private static void check(int width,int length,int entranceX,
            int levels,KingdomMineSettings.Heading heading) {
        var shaft=area(width,length);
        BlockPos core=new BlockPos(entranceX,80,length);
        assertTrue(KingdomMineCentralShaftPlan.valid(shaft,core));
        var plan=KingdomMineCentralShaftPlan.cells(core,shaft,levels,heading);
        int depth=levels*KingdomMineSettings.LEVEL_INTERVAL;
        assertFalse(plan.isEmpty());
        assertTrue(plan.size()>depth);
        var visited=new HashSet<BlockPos>();
        visited.add(core.north());
        int stepsDown=0;
        for(int i=0;i<plan.size();i++){
            var cell=plan.get(i);
            assertTrue(visited.contains(cell.approach()),"unreachable: "+cell);
            int h=Math.abs(cell.target().getX()-cell.approach().getX())
                +Math.abs(cell.target().getZ()-cell.approach().getZ());
            int drop=cell.approach().getY()-cell.target().getY();
            assertEquals(1,h,"diagonal: "+cell);
            assertTrue(drop==0||drop==1,"unsafe change of elevation: "+cell);
            if(i<depth){assertEquals(1,drop,"shaft is excavated before levels");stepsDown++;}
            else assertEquals(0,drop,"level must not descend after shaft completion");
            assertTrue(visited.add(cell.target()),"duplicate: "+cell);
            assertTrue(cell.target().getY()>=80-depth&&cell.target().getY()<=80);
        }
        assertEquals(depth,stepsDown);
        for(int level=1;level<=levels;level++){
            int d=level*5;
            assertTrue(visited.contains(
                KingdomMineCentralShaftPlan.galleryEnd(shaft,d,heading)),
                "missing gallery at depth "+d+" toward "+heading);
        }
    }

    @Test void shaftIsDugCompletelyBeforeItsLevels() {
        check(7,7,3,3,KingdomMineSettings.Heading.NORTH);
        check(9,10,4,2,KingdomMineSettings.Heading.EAST);
        check(12,12,6,4,KingdomMineSettings.Heading.SOUTH);
        check(7,7,0,1,KingdomMineSettings.Heading.WEST);
    }

    @Test void allShaftSizesAndEntrancesStayConnected() {
        for(int width=7;width<=12;width++)
            for(int length=7;length<=12;length++)
                for(int x=0;x<width;x++)
                    for(var heading:KingdomMineSettings.Heading.values())
                        check(width,length,x,3,heading);
    }

    @Test void rejectsSmallAndUnreachableShafts() {
        assertFalse(KingdomMineCentralShaftPlan.valid(
            area(6,7),new BlockPos(3,80,7)));
        assertFalse(KingdomMineCentralShaftPlan.valid(
            area(7,7),new BlockPos(3,79,7)));
        assertTrue(KingdomMineCentralShaftPlan.cells(
            null,area(7,7),3,KingdomMineSettings.Heading.NORTH).isEmpty());
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
