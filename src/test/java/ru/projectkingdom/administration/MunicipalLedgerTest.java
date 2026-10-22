package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class MunicipalLedgerTest {
    private static MunicipalLedger.State state(){return new MunicipalLedger.State(100,50,80,40,30);}
    @Test void bankDepositAndWithdrawalMoveClaimAndReserveTogether() {
        var deposit=MunicipalLedger.depositBank(state(),20);assertTrue(deposit.success());
        assertEquals(120,deposit.state().bankAccount());assertEquals(70,deposit.state().bankReserve());
        var withdrawal=MunicipalLedger.withdrawBank(deposit.state(),60);assertTrue(withdrawal.success());
        assertEquals(60,withdrawal.state().bankAccount());assertEquals(10,withdrawal.state().bankReserve());
    }
    @Test void insufficientReserveDoesNotMutateState() {
        var result=MunicipalLedger.withdrawBank(state(),60);
        assertFalse(result.success());assertEquals(state(),result.state());
    }
    @Test void treasuryDebtSettlementRequiresBalanceAndBacking() {
        var result=MunicipalLedger.settleDebt(state(),20);assertTrue(result.success());
        assertEquals(60,result.state().treasuryBalance());assertEquals(20,result.state().treasuryReserve());assertEquals(10,result.state().debt());
        assertFalse(MunicipalLedger.settleDebt(state(),50).success());
    }
    @Test void transferIsAtomicAndOverflowOrInvalidAmountsFailClosed() {
        var moved=MunicipalLedger.transferBankToTreasury(state(),20);assertTrue(moved.success());
        assertEquals(30,moved.state().bankReserve());assertEquals(100,moved.state().treasuryBalance());assertEquals(60,moved.state().treasuryReserve());
        assertFalse(MunicipalLedger.depositBank(state(),0).success());
        var maximum=new MunicipalLedger.State(Long.MAX_VALUE,1,0,0,0);
        assertFalse(MunicipalLedger.depositBank(maximum,1).success());assertEquals(maximum,MunicipalLedger.depositBank(maximum,1).state());
    }
}
