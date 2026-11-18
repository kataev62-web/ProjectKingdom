package ru.projectkingdom.npc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PhysicalCashCommitGateTest {
    @Test void successfulTransferKeepsTotalPhysicalCoins() {
        int[] slots={100,0};
        assertTrue(PhysicalCashCommitGate.commit(
                ()->slots[0]-=60,()->slots[1]+=60,
                ()->slots[1]=0,()->slots[0]=100,()->slots[0]==100&&slots[1]==0));
        assertArrayEquals(new int[]{40,60},slots);
    }

    @Test void destinationSetItemThrowBeforeWriteRestoresExactBalances() {
        int[] slots={100,0};
        boolean success=PhysicalCashCommitGate.commit(
                ()->slots[0]-=60,
                ()->{throw new IllegalStateException("target setter failed");},
                ()->slots[1]=0,()->slots[0]=100,()->slots[0]==100&&slots[1]==0);
        assertFalse(success);
        assertArrayEquals(new int[]{100,0},slots);
    }

    @Test void destinationSetItemThrowAfterPartialWriteAlsoRestores() {
        int[] slots={100,0};
        assertFalse(PhysicalCashCommitGate.commit(
                ()->slots[0]-=60,
                ()->{slots[1]+=20;throw new IllegalStateException("partial target write");},
                ()->slots[1]=0,()->slots[0]=100,()->slots[0]==100&&slots[1]==0));
        assertArrayEquals(new int[]{100,0},slots);
    }

    @Test void sourceSetItemThrowAfterPartialWriteIsNotReportedAsSuccess() {
        int[] slots={100,0};
        assertFalse(PhysicalCashCommitGate.commit(
                ()->{slots[0]=50;throw new IllegalStateException("partial source write");},
                ()->slots[1]+=50,
                ()->slots[1]=0,()->slots[0]=100,()->slots[0]==100&&slots[1]==0));
        assertArrayEquals(new int[]{100,0},slots);
    }

    @Test void silentNoopSetterMustBeCaughtByPhysicalVerification() {
        int[] slots={100,0};
        assertFalse(PhysicalCashCommitGate.commit(
                ()->{/* source rejects write */
                    if(slots[0]!=40)throw new IllegalStateException("source did not debit");
                },
                ()->slots[1]+=60,
                ()->slots[1]=0,()->slots[0]=100,()->slots[0]==100&&slots[1]==0));
        assertArrayEquals(new int[]{100,0},slots);
    }

    @Test void rollbackFailureIsExplicitlyQuarantinedAndNeverRetried() {
        int[] slots={100,0};
        var error=assertThrows(IllegalStateException.class,()->PhysicalCashCommitGate.commit(
                ()->slots[0]-=60,()->{slots[1]+=60;throw new IllegalStateException("write failed");},
                ()->{throw new IllegalStateException("target restore refused");},
                ()->slots[0]=100,()->slots[0]==100&&slots[1]==0));
        assertTrue(error.getMessage().contains("quarantine"));
        assertEquals(60,slots[1]); // visible uncertainty, never claim success
    }

    @Test void snapshotMismatchOnRollbackCannotBeHidden() {
        int[] slots={100,0};
        assertThrows(IllegalStateException.class,()->PhysicalCashCommitGate.commit(
                ()->slots[0]-60,()->{slots[1]+=60;throw new IllegalStateException("target");},
                ()->{},()->slots[0]=100,()->slots[0]==100&&slots[1]==0));
    }

    @Test void repeatedIndependentTransfersConserveTotals() {
        int[] slots={150,0};
        for(int i=0;i<3;i++){
            assertTrue(PhysicalCashCommitGate.commit(
                    ()->slots[0]-=50,()->slots[1]+=50,
                    ()->slots[1]-=50,()->slots[0]+=50,()->slots[0]+slots[1]==150));
            assertEquals(150,slots[0]+slots[1]);
        }
        assertArrayEquals(new int[]{0,150},slots);
    }

    @Test void nullCommitStageCannotMutateAnyInventory() {
        int[] slots={10,0};
        assertFalse(PhysicalCashCommitGate.commit(null,()->slots[1]=10,
                ()->{},()->{},()->true));
        assertArrayEquals(new int[]{10,0},slots);
    }
}
