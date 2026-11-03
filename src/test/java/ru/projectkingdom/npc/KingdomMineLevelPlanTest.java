package ru.projectkingdom.npc;

import java.util.HashSet;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomMineLevelPlanTest {
    @Test void twoFloorsAreConnectedByAnAdjacentWalkableDescent() {
        BlockPos core=new BlockPos(42,80,-15);
        var plan=KingdomMineLevelPlan.cells(core);
        assertEquals(147,plan.size()); // 71 + 5 steps + 71
        assertEquals(core.north(2),plan.get(0).target());
        assertEquals(core.north(24),plan.get(22).target());
        assertEquals(core.north(25).below(),plan.get(71).target());
        assertEquals(core.north(29).below(4),plan.get(75).target());
        assertEquals(core.north(30).below(4),plan.get(76).target());
        var reached=new HashSet<BlockPos>();
        reached.add(core.north());
        for(var cell:plan){
            assertTrue(reached.contains(cell.approach()),"unreachable: "+cell);
            assertEquals(1,Math.abs(cell.target().getZ()-cell.approach().getZ())
                +Math.abs(cell.target().getX()-cell.approach().getX()));
            int height=cell.approach().getY()-cell.target().getY();
            assertTrue(height>=0&&height<=1,"unsafe level change: "+cell);
            assertTrue(reached.add(cell.target()),"duplicated excavation: "+cell);
        }
        assertEquals(147,reached.size()-1);
        assertEquals(core.getY()-4,plan.get(plan.size()-1).target().getY());
    }

    @Test void noPlanWithoutCore() {
        assertTrue(KingdomMineLevelPlan.cells(null).isEmpty());
    }
}
