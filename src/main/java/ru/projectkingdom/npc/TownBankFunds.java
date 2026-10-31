package ru.projectkingdom.npc;

/** Pure preflight for separately owned receipts in the same physical bank vault. */
public final class TownBankFunds {
    public enum Operation { DEPOSIT, WITHDRAW, DONATE }
    public record Balances(long personal,long personalReserve,long municipal,long municipalReserve,long physical) {}
    private TownBankFunds() {}
    public static Balances plan(Balances b,Operation operation,long amount) {
        if(b==null||operation==null||amount<1||amount>1_000_000
                ||b.personal()<0||b.personalReserve()<0||b.municipal()<0||b.municipalReserve()<0||b.physical()<0
                ||b.personalReserve()>Long.MAX_VALUE-b.municipalReserve()
                ||b.physical()<b.personalReserve()+b.municipalReserve())return null;
        try {
            return switch(operation){
                case DEPOSIT->new Balances(b.personal(),b.personalReserve(),Math.addExact(b.municipal(),amount),
                        Math.addExact(b.municipalReserve(),amount),Math.addExact(b.physical(),amount));
                case WITHDRAW->b.municipal()<amount||b.municipalReserve()<amount?null:
                        new Balances(b.personal(),b.personalReserve(),b.municipal()-amount,
                                b.municipalReserve()-amount,b.physical()-amount);
                case DONATE->b.personal()<amount||b.personalReserve()<amount?null:
                        new Balances(b.personal()-amount,b.personalReserve()-amount,Math.addExact(b.municipal(),amount),
                                Math.addExact(b.municipalReserve(),amount),b.physical());
            };
        }catch(ArithmeticException overflow){return null;}
    }
}
