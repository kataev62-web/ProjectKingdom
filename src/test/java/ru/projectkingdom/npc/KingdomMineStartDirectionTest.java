package ru.projectkingdom.npc;

import java.util.HashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomMineStartDirectionTest {
    @Test void existingMinesRemainForwardByDefaultAndLockAfterStart() {
        KingdomMineSettings settings=new KingdomMineSettings();
        UUID mine=UUID.randomUUID();
        assertEquals(KingdomMineSettings.Start.FORWARD,settings.start(mine));
        assertTrue(settings.choose(mine,KingdomMineSettings.Start.DOWN));
        assertEquals(KingdomMineSettings.Start.DOWN,settings.start(mine));
        settings.markStarted(mine);
        assertTrue(settings.started(mine));
        assertFalse(settings.choose(mine,KingdomMineSettings.Start.FORWARD));
        assertEquals(KingdomMineSettings.Start.DOWN,settings.start(mine));
    }

    @Test void forwardRetainsExistingGeometryAndDownBeginsWithDescendingStairs() {
        BlockPos core=new BlockPos(10,80,20);
        var forward=KingdomMineLevelPlan.cells(core,KingdomMineSettings.Start.FORWARD);
        assertEquals(KingdomMineLevelPlan.cells(core),forward);
        assertEquals(core.north(2),forward.get(0).target());

        var down=KingdomMineLevelPlan.cells(core,KingdomMineSettings.Start.DOWN);
        assertEquals(forward.size()+5,down.size());
        assertEquals(core.north(2).below(),down.get(0).target());
        assertEquals(core.north(6).below(4),down.get(4).target());
        assertEquals(core.north(7).below(4),down.get(5).target());

        var anchors=KingdomMineLevelPlan.anchors(core,KingdomMineSettings.Start.DOWN);
        assertEquals(core.north(5).below(4),anchors.get(0));
        assertEquals(core.north(33).below(8),anchors.get(1));
        var reachable=new HashSet<BlockPos>();
        reachable.add(core.north());
        for(var cell:down){
            assertTrue(reachable.contains(cell.approach()),"unreachable: "+cell);
            int horizontal=Math.abs(cell.target().getX()-cell.approach().getX())
                +Math.abs(cell.target().getZ()-cell.approach().getZ());
            int descent=cell.approach().getY()-cell.target().getY();
            assertEquals(1,horizontal);
            assertTrue(descent==0||descent==1);
            assertTrue(reachable.add(cell.target()),"repeated location: "+cell);
        }
    }

    @Test void nullCoreNeverProducesAnExcavationPlan() {
        assertTrue(KingdomMineLevelPlan.cells(null,KingdomMineSettings.Start.DOWN).isEmpty());
        assertTrue(KingdomMineLevelPlan.anchors(null,KingdomMineSettings.Start.FORWARD).isEmpty());
    }
}
