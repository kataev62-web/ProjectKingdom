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

    @Test void onlyTfcSupportItemsAreEligible() {
        assertTrue(KingdomMineSupportPlan.supports("tfc:wood/support/oak"));
        assertTrue(KingdomMineSupportPlan.supports("tfc:wood/support/ash"));
        assertFalse(KingdomMineSupportPlan.supports("minecraft:oak_fence"));
        assertFalse(KingdomMineSupportPlan.supports("tfc:wood/vertical_support/oak"));
        assertFalse(KingdomMineSupportPlan.supports(""));
        assertFalse(KingdomMineSupportPlan.supports(null));
    }
}
