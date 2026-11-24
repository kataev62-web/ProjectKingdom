package ru.projectkingdom.npc;

import java.util.HashSet;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomMineSupportPlanTest {
    @Test void fourFramesPerLevelAreThreeWideAndLeaveWalkwayFree() {
        BlockPos entrance=new BlockPos(30,70,-19);
        var frames=KingdomMineSupportPlan.frames(entrance);
        assertEquals(4,frames.size());
        assertEquals(5,frames.get(0).depth());
        assertEquals(20,frames.get(3).depth());
        var occupied=new HashSet<BlockPos>();
        for(var frame:frames) {
            assertTrue(KingdomMineSupportPlan.valid(frame));
            assertNotEquals(frame.walkway(),frame.leftPost());
            assertNotEquals(frame.walkway(),frame.rightPost());
            assertTrue(occupied.add(frame.leftPost()));
            assertTrue(occupied.add(frame.rightPost()));
        }
    }

    @Test void emergencySupportHandlesBothAxes() {
        BlockPos previous=new BlockPos(8,64,12);
        var north=KingdomMineSupportPlan.emergency(previous,previous.north().below());
        assertNotNull(north);
        assertTrue(KingdomMineSupportPlan.valid(north));
        assertEquals(previous,north.walkway());
        assertEquals(previous.west(),north.leftPost());
        assertEquals(previous.east(),north.rightPost());
        var east=KingdomMineSupportPlan.emergency(previous,previous.east());
        assertNotNull(east);
        assertTrue(KingdomMineSupportPlan.valid(east));
        assertEquals(previous.north(),east.leftPost());
        assertEquals(previous.south(),east.rightPost());
        // The miner also digs straight down: the frame then stands around the
        // rock itself, because the ceiling of the walkway is the place of the
        // miner.
        var down=KingdomMineSupportPlan.emergency(previous,previous.above());
        assertNotNull(down);
        assertTrue(KingdomMineSupportPlan.valid(down));
        // The frame of the corridor stands beside the miner and carries the
        // beam over the rock that the miner digs.
        assertEquals(previous,down.walkway());
        assertEquals(previous.north(),down.leftPost());
        assertEquals(previous.south(),down.rightPost());
        assertEquals(previous.above(2),down.crossbeam());
        assertEquals(previous.above(),down.protectedCell());
        assertNull(KingdomMineSupportPlan.emergency(null,previous));
    }

    @Test void onlyTfcSupportItemsAreEligible() {
        assertTrue(KingdomMineSupportPlan.supports("tfc:wood/support/oak"));
        assertTrue(KingdomMineSupportPlan.supports("tfc:wood/support/ash"));
        assertFalse(KingdomMineSupportPlan.supports("minecraft:oak_fence"));
        assertFalse(KingdomMineSupportPlan.supports("tfc:wood/vertical_support/oak"));
        assertFalse(KingdomMineSupportPlan.supports(""));
        assertFalse(KingdomMineSupportPlan.supports(null));
    }
}
