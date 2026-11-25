package ru.projectkingdom.npc;

import java.util.HashMap;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomMineLayerWorkTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    private static KingdomMineSettings.Shaft shaft() {
        return new KingdomMineSettings.Shaft(0,6,0,6,80,"minecraft:overworld");
    }
    @Test void allFortyNinePositionsAreAuditedEvenWhenFirstPositionIsBlocked() {
        var read = new HashSet<BlockPos>();
        assertFalse(KingdomMineLayerWork.complete(shaft(),1,at -> {
            assertTrue(read.add(at));
            return at.equals(new BlockPos(0,79,0)) ? Blocks.STONE.defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
        }));
        assertEquals(49,read.size());
    }
    @Test void everySingleRemainingPositionPreventsDescent() {
        for (var cell : KingdomMineCentralShaftPlan.layer(shaft(),1)) {
            assertFalse(KingdomMineLayerWork.complete(shaft(),1,at ->
                    at.equals(cell.target()) ? Blocks.DIRT.defaultBlockState()
                            : Blocks.AIR.defaultBlockState()),cell.target().toString());
        }
    }
    @Test void waterInAnExistingCavePreventsDescent() {
        assertFalse(KingdomMineLayerWork.complete(shaft(),1,at ->
                at.equals(new BlockPos(6,79,6)) ? Blocks.WATER.defaultBlockState()
                        : Blocks.CAVE_AIR.defaultBlockState()));
    }
    @Test void nativeDescentStructuresSurviveResumeAndOnlyExpectedLocationsAreExempt() {
        var shaft = shaft();
        var descent = KingdomMineCentralShaftPlan.descent(shaft,5);
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.NORTH);
        var world = new HashMap<BlockPos,BlockState>();
        world.put(descent,ladder);
        world.put(descent.north(),Blocks.OAK_PLANKS.defaultBlockState());
        assertTrue(KingdomMineLayerWork.complete(shaft,5,
                at -> world.getOrDefault(at,Blocks.AIR.defaultBlockState())));
        world.put(descent.east(),Blocks.OAK_PLANKS.defaultBlockState());
        assertFalse(KingdomMineLayerWork.complete(shaft,5,
                at -> world.getOrDefault(at,Blocks.AIR.defaultBlockState())));
        assertFalse(KingdomMineLayerWork.cleared(shaft,5,descent,
                ladder.setValue(LadderBlock.FACING,Direction.SOUTH)));
    }
    @Test void reopenedCellAfterLastCutRequiresAnotherAuditAfterRestart() {
        var world = new HashMap<BlockPos,BlockState>();
        assertTrue(KingdomMineLayerWork.complete(shaft(),2,
                at -> world.getOrDefault(at,Blocks.AIR.defaultBlockState())));
        world.put(new BlockPos(1,78,1),Blocks.GRAVEL.defaultBlockState());
        assertFalse(KingdomMineLayerWork.complete(shaft(),2,
                at -> world.getOrDefault(at,Blocks.AIR.defaultBlockState())));
    }
}
