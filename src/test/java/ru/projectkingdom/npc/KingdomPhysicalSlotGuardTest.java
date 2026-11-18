package ru.projectkingdom.npc;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomPhysicalSlotGuardTest {
    private static KingdomPhysicalSlotGuard.Slot slot(Object backing, int number) {
        return new KingdomPhysicalSlotGuard.Slot(backing, number);
    }

    @Test void independentWrappersOfExactlySamePhysicalChestAreRejected() {
        Object physicalChest = new Object();
        // Wrapper A and B are distinct objects but map to the same physical slots.
        Object wrapperA = new Object(), wrapperB = new Object();
        assertNotSame(wrapperA, wrapperB);
        assertFalse(KingdomPhysicalSlotGuard.disjoint(
                List.of(slot(physicalChest,0),slot(physicalChest,1)),
                List.of(slot(physicalChest,0),slot(physicalChest,1))));
    }

    @Test void twoDifferentViewsSharingOneHalfOfDoubleChestAreRejected() {
        Object left = new Object(), right = new Object(), third = new Object();
        assertFalse(KingdomPhysicalSlotGuard.disjoint(
                List.of(slot(left,0),slot(right,0)),
                List.of(slot(right,0),slot(third,0))));
        assertFalse(KingdomPhysicalSlotGuard.disjoint(
                List.of(slot(right,0),slot(third,0)),
                List.of(slot(left,0),slot(right,0))));
    }

    @Test void trulySeparateChestsAreAcceptedWithoutConfusingEqualSlotNumbers() {
        Object left = new Object(), right = new Object();
        assertTrue(KingdomPhysicalSlotGuard.disjoint(
                List.of(slot(left,0),slot(left,1)),
                List.of(slot(right,0),slot(right,1))));
    }

    @Test void wrappersOfSameBackingButSeparateSlotsDoNotOverlap() {
        Object backing = new Object();
        assertTrue(KingdomPhysicalSlotGuard.disjoint(
                List.of(slot(backing,0),slot(backing,1)),
                List.of(slot(backing,2),slot(backing,3))));
    }

    @Test void duplicateSlotsWithinOneWrapperAreRejected() {
        Object backing = new Object();
        assertFalse(KingdomPhysicalSlotGuard.disjoint(
                List.of(slot(backing,0),slot(backing,0)),
                List.of(slot(new Object(),0))));
        assertFalse(KingdomPhysicalSlotGuard.disjoint(
                List.of(slot(new Object(),0)),
                List.of(slot(backing,0),slot(backing,0))));
    }

    @Test void unknownProvenanceIsRejectedWithoutSideEffects() {
        assertFalse(KingdomPhysicalSlotGuard.disjoint(null,List.of(slot(new Object(),0))));
        assertFalse(KingdomPhysicalSlotGuard.disjoint(List.of(),List.of(slot(new Object(),0))));
        assertFalse(KingdomPhysicalSlotGuard.disjoint(
                List.of(slot(new Object(),0)),List.of()));
        assertFalse(KingdomPhysicalSlotGuard.disjoint(
                java.util.Arrays.asList(slot(new Object(),0),null),
                List.of(slot(new Object(),0))));
        assertThrows(IllegalArgumentException.class,()->slot(null,0));
        assertThrows(IllegalArgumentException.class,()->slot(new Object(),-1));
    }

    @Test void distinctBackingsWithSameEqualsAreStillDistinctPhysicalEntities() {
        Object left=new String("chest"), right=new String("chest");
        assertEquals(left,right);
        assertNotSame(left,right);
        assertTrue(KingdomPhysicalSlotGuard.disjoint(List.of(slot(left,0)),List.of(slot(right,0))));
    }

    @Test void noMutationOfCallerInput() {
        Object chest=new Object();
        var before=List.of(slot(chest,0));
        var other=List.of(slot(chest,1));
        assertTrue(KingdomPhysicalSlotGuard.disjoint(before,other));
        assertEquals(1,before.size());
        assertEquals(0,before.get(0).index());
    }
}
