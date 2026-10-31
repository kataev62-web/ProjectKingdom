package ru.projectkingdom.npc;

/** Exercises real receipt planning without requiring a running Minecraft world. */
public final class TownBankFundsHarness {
    private static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
    public static void main(String[] args) {
        var before=new TownBankFunds.Balances(500,800,0,0,800);
        var donated=TownBankFunds.plan(before,TownBankFunds.Operation.DONATE,200);
        check(donated!=null,"existing personal bank receipt must be explicitly transferable to town");
        check(donated.personal()==300&&donated.personalReserve()==600,"only donor receipt is debited");
        check(donated.municipal()==200&&donated.municipalReserve()==200,"town receives backed balance");
        check(donated.physical()==800,"donation must not mint or remove bank coins");
        check(donated.personalReserve()+donated.municipalReserve()==800,"total liability conserved");
        check(TownBankFunds.plan(before,TownBankFunds.Operation.WITHDRAW,100)==null,"private bank funds are not town funds");
        check(TownBankFunds.plan(before,TownBankFunds.Operation.DONATE,501)==null,"cannot donate others' reserve");
        var deposit=TownBankFunds.plan(before,TownBankFunds.Operation.DEPOSIT,100);
        check(deposit.personal()==500&&deposit.municipal()==100&&deposit.physical()==900,"wallet deposit credits only town");
        var withdrawn=TownBankFunds.plan(donated,TownBankFunds.Operation.WITHDRAW,100);
        check(withdrawn.personal()==300&&withdrawn.municipal()==100&&withdrawn.physical()==700,"town withdrawal leaves personal receipt alone");
        for(long bad:new long[]{-1,0,1_000_001,Long.MAX_VALUE})
            check(TownBankFunds.plan(before,TownBankFunds.Operation.DEPOSIT,bad)==null,"reject invalid amount");
        check(TownBankFunds.plan(new TownBankFunds.Balances(500,800,20,20,810),TownBankFunds.Operation.DONATE,1)==null,"reject unbacked vault");
        check(TownBankFunds.plan(new TownBankFunds.Balances(1,1,Long.MAX_VALUE,0,1),TownBankFunds.Operation.DONATE,1)==null,"no ledger overflow");
        for(int i=1;i<=500;i++) {
            var s=new TownBankFunds.Balances(i,i+100,200,200,i+300);
            var n=TownBankFunds.plan(s,TownBankFunds.Operation.DONATE,i);
            check(n!=null&&n.physical()==s.physical()&&n.personalReserve()+n.municipalReserve()==s.personalReserve()+s.municipalReserve(),"receipt conservation property");
        }
        System.out.println("TownBankFundsHarness PASS");
    }
}
