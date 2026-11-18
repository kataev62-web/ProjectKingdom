package ru.projectkingdom.npc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class BankOperationLedgerTest {
    @TempDir Path temp;
    private final UUID town=UUID.fromString("00000000-0000-0000-0000-000000000078");
    private final UUID actor=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID landlord=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String H0="a".repeat(64),H1="b".repeat(64);
    private BankOperationLedger.Snapshot before() {
        return new BankOperationLedger.Snapshot(1000,1000,500,500,600,200,200,0,H0,H0);
    }
    private BankOperationLedger.Snapshot personalWithdrawalAfter() {
        return new BankOperationLedger.Snapshot(1100,900,400,400,500,200,200,0,H1,H1);
    }
    private BankOperationLedger open(String name) throws IOException {
        return BankOperationLedger.open(temp.resolve(name+".log"));
    }

    @Test void elevenRestartBoundariesAlwaysFailClosedUnlessExplicitlyResolved() throws Exception {
        // 0: before intent; 1-2: after durable PREPARED/before or during physical write;
        // 3-8: after in-memory writes, account writes, receipt, save phases and restart;
        // 9: unchanged abort; 10: verified operator acknowledgement.
        for(int stage=0;stage<11;stage++) {
            String name="boundary-"+stage;
            BankOperationLedger live=open(name);
            boolean expectBlocked=stage!=0&&stage!=9&&stage!=10;
            if(stage>0) {
                var tx=live.prepare(town,actor,actor,
                        BankOperationLedger.Kind.PERSONAL_WITHDRAW,100,before());
                assertNotNull(tx);
                assertNull(live.prepare(town,actor,actor,
                        BankOperationLedger.Kind.PERSONAL_WITHDRAW,100,before()),
                        "Concurrent request must not spend same vault twice");
                if(stage==9) assertTrue(live.abortUnchanged(tx,before()));
                if(stage>=3&&stage!=9)
                    assertTrue(live.receipt(tx,personalWithdrawalAfter()));
                if(stage==10) {
                    BankOperationLedger recovery=open(name);
                    assertTrue(recovery.blocked(town));
                    assertTrue(recovery.reconcile(tx.id(),personalWithdrawalAfter()));
                }
            }
            BankOperationLedger afterRestart=open(name);
            assertEquals(expectBlocked,afterRestart.blocked(town),"Boundary "+stage);
            assertEquals(expectBlocked,!afterRestart.unresolved().isEmpty(),"Boundary "+stage);
        }
    }

    @Test void preparedCannotBeAcknowledgedAfterMoneyMovedWithoutReceipt() throws Exception {
        var live=open("prepared");
        var tx=live.prepare(town,actor,actor,BankOperationLedger.Kind.PERSONAL_WITHDRAW,100,before());
        var restart=open("prepared");
        assertFalse(restart.reconcile(tx.id(),personalWithdrawalAfter()));
        assertTrue(restart.blocked(town));
        assertTrue(restart.reconcile(tx.id(),before()));
        assertFalse(open("prepared").blocked(town));
    }

    @Test void receiptCannotBeReplayedOrAcknowledgedAgainstPreDebitWallet() throws Exception {
        var live=open("receipt");
        var tx=live.prepare(town,actor,actor,BankOperationLedger.Kind.PERSONAL_WITHDRAW,100,before());
        assertTrue(live.receipt(tx,personalWithdrawalAfter()));
        var restart=open("receipt");
        assertTrue(restart.blocked(town));
        assertFalse(restart.reconcile(tx.id(),before()));
        assertFalse(restart.reconcile(tx.id(),new BankOperationLedger.Snapshot(
                1100,900,400,400,500,200,200,0,H0,H1)));
        assertTrue(restart.reconcile(tx.id(),personalWithdrawalAfter()));
        assertFalse(open("receipt").blocked(town));
    }

    @Test void distinctTownRemainsAvailableDuringOtherTownsRecovery() throws Exception {
        var live=open("towns");
        assertNotNull(live.prepare(town,actor,actor,
                BankOperationLedger.Kind.PERSONAL_WITHDRAW,100,before()));
        var secondTown=UUID.randomUUID();
        assertFalse(live.blocked(secondTown));
        assertNotNull(live.prepare(secondTown,actor,actor,
                BankOperationLedger.Kind.PERSONAL_WITHDRAW,100,before()));
    }

    @Test void impossibleLedgerDeltasStayPreparedAndCannotIssueReceipt() throws Exception {
        var live=open("invalid");
        var tx=live.prepare(town,actor,actor,BankOperationLedger.Kind.PERSONAL_WITHDRAW,100,before());
        var minted=new BankOperationLedger.Snapshot(1200,900,400,400,500,200,200,0,H1,H1);
        assertFalse(live.receipt(tx,minted));
        assertTrue(open("invalid").blocked(town));
    }

    @Test void physicallyFreeCoinsMayBeAssignedWithoutDoubleSpendingPersonalReserves() throws Exception {
        var live=open("city");
        var tx=live.prepare(town,actor,actor,BankOperationLedger.Kind.FREE_CASH_ASSIGN,50,before());
        assertTrue(live.receipt(tx,new BankOperationLedger.Snapshot(
                1000,1000,500,500,600,250,250,0,H0,H0)));
        assertTrue(open("city").blocked(town));
    }

    @Test void donationChangesClaimsWithoutCreatingCoinsOrModifyingInventories() throws Exception {
        var live=open("donation");
        var tx=live.prepare(town,actor,actor,BankOperationLedger.Kind.DONATE,100,before());
        var after=new BankOperationLedger.Snapshot(1000,1000,400,400,500,300,300,0,H0,H0);
        assertTrue(live.receipt(tx,after));
        assertFalse(live.receipt(tx,after));
        assertFalse(live.blocked(town));
        assertTrue(open("donation").blocked(town));
    }

    @Test void beneficiaryIsDistinctFromPayerForLeaseCredits() throws Exception {
        var live=open("land");
        var b=new BankOperationLedger.Snapshot(1000,1000,500,20,600,200,200,0,H0,H0);
        var tx=live.prepare(town,actor,landlord,BankOperationLedger.Kind.THIRD_PARTY_CREDIT,50,b);
        assertTrue(live.receipt(tx,new BankOperationLedger.Snapshot(
                950,1050,500,70,650,200,200,0,H1,H1)));
    }

    @Test void corruptedOrTruncatedTailBlocksAllNewBankOperations() throws Exception {
        var live=open("corrupt");
        assertNotNull(live.prepare(town,actor,actor,BankOperationLedger.Kind.PERSONAL_WITHDRAW,100,before()));
        Path file=temp.resolve("corrupt.log");
        Files.writeString(file,"C|truncated",StandardOpenOption.APPEND);
        assertThrows(IOException.class,()->BankOperationLedger.open(file));
    }

    @Test void rejectedNoMutationDoesNotLeavePermanentQuarantine() throws Exception {
        var live=open("rejected");
        var tx=live.prepare(town,actor,actor,BankOperationLedger.Kind.PERSONAL_WITHDRAW,100,before());
        assertFalse(live.abortUnchanged(tx,personalWithdrawalAfter()));
        assertTrue(live.blocked(town));
        assertTrue(live.abortUnchanged(tx,before()));
        assertFalse(open("rejected").blocked(town));
    }

    @Test void invalidAmountsAndMissingWitnessAreNeverDurablyPrepared() throws Exception {
        var live=open("limits");
        assertNull(live.prepare(town,actor,actor,BankOperationLedger.Kind.CITY_WITHDRAW,0,before()));
        assertNull(live.prepare(town,actor,actor,BankOperationLedger.Kind.CITY_WITHDRAW,1_000_001,before()));
        assertNull(live.prepare(town,actor,actor,BankOperationLedger.Kind.CITY_WITHDRAW,100,null));
        assertFalse(live.blocked(town));
    }
}
