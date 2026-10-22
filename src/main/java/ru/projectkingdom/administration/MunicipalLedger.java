package ru.projectkingdom.administration;

public final class MunicipalLedger {
    public record State(long bankAccount,long bankReserve,long treasuryBalance,long treasuryReserve,long debt){
        public State {if(bankAccount<0||bankReserve<0||treasuryBalance<0||treasuryReserve<0||debt<0)throw new IllegalArgumentException("negative ledger value");}
    }
    public record Result(boolean success,State state){}
    private MunicipalLedger(){}
    private static Result fail(State state){return new Result(false,state);}
    public static Result depositBank(State state,long amount){
        if(amount<=0||state.bankAccount()>Long.MAX_VALUE-amount||state.bankReserve()>Long.MAX_VALUE-amount)return fail(state);
        return new Result(true,new State(state.bankAccount()+amount,state.bankReserve()+amount,state.treasuryBalance(),state.treasuryReserve(),state.debt()));
    }
    public static Result withdrawBank(State state,long amount){
        if(amount<=0||state.bankAccount()<amount||state.bankReserve()<amount)return fail(state);
        return new Result(true,new State(state.bankAccount()-amount,state.bankReserve()-amount,state.treasuryBalance(),state.treasuryReserve(),state.debt()));
    }
    public static Result depositTreasury(State state,long amount){
        if(amount<=0||state.treasuryBalance()>Long.MAX_VALUE-amount||state.treasuryReserve()>Long.MAX_VALUE-amount)return fail(state);
        return new Result(true,new State(state.bankAccount(),state.bankReserve(),state.treasuryBalance()+amount,state.treasuryReserve()+amount,state.debt()));
    }
    public static Result withdrawTreasury(State state,long amount){
        if(amount<=0||state.treasuryBalance()<amount||state.treasuryReserve()<amount)return fail(state);
        return new Result(true,new State(state.bankAccount(),state.bankReserve(),state.treasuryBalance()-amount,state.treasuryReserve()-amount,state.debt()));
    }
    public static Result transferBankToTreasury(State state,long amount){
        if(amount<=0||state.bankReserve()<amount||state.treasuryBalance()>Long.MAX_VALUE-amount||state.treasuryReserve()>Long.MAX_VALUE-amount)return fail(state);
        return new Result(true,new State(state.bankAccount(),state.bankReserve()-amount,state.treasuryBalance()+amount,state.treasuryReserve()+amount,state.debt()));
    }
    public static Result settleDebt(State state,long amount){
        if(amount<=0||state.debt()<amount||state.treasuryBalance()<amount||state.treasuryReserve()<amount)return fail(state);
        return new Result(true,new State(state.bankAccount(),state.bankReserve(),state.treasuryBalance()-amount,state.treasuryReserve()-amount,state.debt()-amount));
    }
}
