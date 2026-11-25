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
        assertEquals(previous.south(),north.walkway());
        assertEquals(previous.south().west(),north.leftPost());
        assertEquals(previous.south().east(),north.rightPost());
        assertEquals(1,north.beamHeight());
        assertEquals(previous.getY()+1,north.crossbeam().getY());
        var east=KingdomMineSupportPlan.emergency(previous,previous.east());
        assertNotNull(east);
        assertTrue(KingdomMineSupportPlan.valid(east));
        assertEquals(previous.north(),east.leftPost());
        assertEquals(previous.south(),east.rightPost());
        assertNull(KingdomMineSupportPlan.emergency(previous,previous.above()));
        assertNull(KingdomMineSupportPlan.emergency(null,previous));
    }

    @Test void downwardEmergencyFramesStayWithinPinnedNativeTwoBlockSupportDepth() {
        BlockPos target=new BlockPos(10,70,10);
        for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL) {
            for(int distance=1;distance<=2;distance++) {
                BlockPos approach=target.relative(direction,distance).above();
                var frame=KingdomMineSupportPlan.emergency(approach,target);
                assertNotNull(frame);
                assertTrue(KingdomMineSupportPlan.valid(frame));
                assertEquals(2,frame.crossbeam().getY()-target.getY(),
                    "native horizontal_support_beam support_down=2");
                assertTrue(Math.abs(frame.crossbeam().getX()-target.getX())<=4);
                assertTrue(Math.abs(frame.crossbeam().getZ()-target.getZ())<=4);
                assertNotEquals(approach,frame.walkway(),"low beam cannot overlap miner staging square");
                assertEquals(approach.getY(),frame.leftPost().getY(),
                    "posts use existing safe ground without cutting unstable target layer");
            }
        }
        assertNull(KingdomMineSupportPlan.emergency(target.above(2).north(),target));
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
