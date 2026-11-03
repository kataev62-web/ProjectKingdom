package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomMineMaintenanceTest {
    @Test void torchCheckpointsArePredictableAndDoNotCoverBranches() {
        BlockPos core=new BlockPos(25,65,-5);
        for(int distance=2;distance<=24;distance++) {
            assertEquals(distance%6==0,
                KingdomMineMaintenance.lightPoint(core,core.north(distance)),
                "main gallery distance "+distance);
        }
        assertFalse(KingdomMineMaintenance.lightPoint(core,core.north(6).east()));
        assertFalse(KingdomMineMaintenance.lightPoint(core,core.north(6).below()));
        assertFalse(KingdomMineMaintenance.lightPoint(null,core));
    }

    @Test void installedTorchesRemainTraversableDuringNextShift() {
        assertTrue(KingdomMineMaintenance.passable(Blocks.AIR.defaultBlockState()));
        assertTrue(KingdomMineMaintenance.passable(Blocks.TORCH.defaultBlockState()));
        assertFalse(KingdomMineMaintenance.passable(Blocks.STONE.defaultBlockState()));
        assertFalse(KingdomMineMaintenance.passable(Blocks.WATER.defaultBlockState()));
    }
}
