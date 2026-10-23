package ru.projectkingdom.npc;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomArchitectDesignRotationTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void rotatesStairsAndPillarsWithoutLosingStateProperties() {
        var schematic=new KingdomDesignEngine.Design("test", 2, 4, 2, List.of(
            new KingdomDesignEngine.Cell(0,0,0,"minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"),
            new KingdomDesignEngine.Cell(1,1,3,"minecraft:spruce_log[axis=x]")
        ));
        var turned=KingdomArchitectDesignRotation.rotate(schematic,1);
        assertEquals(4,turned.width());
        assertEquals(2,turned.length());
        assertEquals(2,turned.height());
        assertEquals(2,turned.blocks().size());
        var stair=turned.blocks().get(0);
        assertEquals(3,stair.x());
        assertEquals(0,stair.z());
        var state=KingdomBlockStateCodec.parse(stair.material());
        assertEquals(Direction.EAST, state.getValue(StairBlock.FACING));
        var log=turned.blocks().get(1);
        assertEquals(0,log.x());
        assertEquals(1,log.z());
        assertEquals(Direction.Axis.Z, KingdomBlockStateCodec.parse(log.material())
            .getValue(RotatedPillarBlock.AXIS));
    }

    @Test void negativeRoofOverhangsNormalizeWithoutDroppingBlocks() {
        var design=new KingdomDesignEngine.Design("home",9,11,7,List.of(
            new KingdomDesignEngine.Cell(-1,5,-1,"minecraft:dark_oak_planks"),
            new KingdomDesignEngine.Cell(9,5,11,"minecraft:dark_oak_planks"),
            new KingdomDesignEngine.Cell(4,0,5,"minecraft:cobblestone")
        ));
        var turned=KingdomArchitectDesignRotation.rotate(design,1);
        assertEquals(13,turned.width());
        assertEquals(11,turned.length());
        assertEquals(6,turned.height());
        assertEquals(3,turned.blocks().size());
        for (var cell:turned.blocks()) {
            assertTrue(cell.x()>=0 && cell.x()<turned.width());
            assertTrue(cell.z()>=0 && cell.z()<turned.length());
            assertTrue(cell.y()>=0 && cell.y()<turned.height());
        }
    }

    @Test void rejectsTooLargeOrImpossibleStagedDesigns() {
        var large=new KingdomDesignEngine.Design("invalid",49,1,1,List.of(
            new KingdomDesignEngine.Cell(0,0,0,"minecraft:stone"),
            new KingdomDesignEngine.Cell(48,0,0,"minecraft:stone")
        ));
        assertThrows(IllegalArgumentException.class,
            ()->KingdomArchitectDesignRotation.rotate(large,1));
    }
}
