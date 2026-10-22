import ru.projectkingdom.administration.MunicipalLedger;

public final class MunicipalLedgerContractTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        var start=new MunicipalLedger.State(100,50,80,40,30);
        var blocked=MunicipalLedger.withdrawBank(start,60);check(!blocked.success()&&blocked.state().equals(start),"unbacked withdrawal must be atomic");
        var deposit=MunicipalLedger.depositBank(start,20);check(deposit.success()&&deposit.state().bankAccount()==120&&deposit.state().bankReserve()==70,"bank deposit");
        var debt=MunicipalLedger.settleDebt(start,20);check(debt.success()&&debt.state().debt()==10&&debt.state().treasuryReserve()==20,"backed debt settlement");
        check(!MunicipalLedger.depositBank(new MunicipalLedger.State(Long.MAX_VALUE,0,0,0,0),1).success(),"overflow denied");
        System.out.println("PASS municipal ledger contract");
    }
}
