package ru.projectkingdom.npc;

import java.io.IOException;
import java.util.*;

/** Read-only gate between authoritative economy data and Minecraft physical containers.
 * No inventory is overwritten during recovery: a mismatch requires manual audit.
 */
public final class TradePhysicalReconciliation {
    private TradePhysicalReconciliation() {}
    public enum Result { MATCH, MISMATCH, UNAVAILABLE, UNKNOWN_ACCOUNT }
    public interface PhysicalView {
        /** Null means that a chunk/container or balance is not safely readable. */
        TradeAtomicEconomy.Account read(UUID accountId) throws IOException;
    }
    public static Result inspect(TradeAtomicEconomy economy, UUID accountId, PhysicalView physical) throws IOException {
        Objects.requireNonNull(economy); Objects.requireNonNull(accountId); Objects.requireNonNull(physical);
        TradeAtomicEconomy.Account expected=economy.read().accounts().get(accountId);
        if(expected==null)return Result.UNKNOWN_ACCOUNT;
        TradeAtomicEconomy.Account actual=physical.read(accountId);
        if(actual==null)return Result.UNAVAILABLE;
        if(!accountId.equals(actual.id()) || actual.copper()!=expected.copper() || actual.slots().size()!=expected.slots().size())return Result.MISMATCH;
        for(int i=0;i<expected.slots().size();i++){
            TradeAtomicEconomy.Stack a=expected.slots().get(i),b=actual.slots().get(i);
            if(a==null || b==null){if(a!=b)return Result.MISMATCH;continue;}
            if(!a.sameItem(b)||a.count()!=b.count())return Result.MISMATCH;
        }
        return Result.MATCH;
    }
    /** All participating accounts must match; never accept a partially loaded view. */
    public static Result inspectAll(TradeAtomicEconomy economy, Collection<UUID> accounts, PhysicalView physical) throws IOException {
        return inspectSnapshot(economy.read(),accounts,physical);
    }
    /** All accounts are checked against the SAME authoritative generation. */
    public static Result inspectSnapshot(TradeAtomicEconomy.State snapshot, Collection<UUID> accounts, PhysicalView physical) throws IOException {
        Objects.requireNonNull(snapshot); Objects.requireNonNull(accounts); Objects.requireNonNull(physical);
        if(accounts.isEmpty())return Result.UNAVAILABLE;
        for(UUID id:accounts){
            if(id==null)return Result.UNKNOWN_ACCOUNT;
            TradeAtomicEconomy.Account expected=snapshot.accounts().get(id);
            if(expected==null)return Result.UNKNOWN_ACCOUNT;
            TradeAtomicEconomy.Account actual=physical.read(id);
            if(actual==null)return Result.UNAVAILABLE;
            Result result=matches(expected,actual)?Result.MATCH:Result.MISMATCH;
            if(result!=Result.MATCH)return result;
        }
        return Result.MATCH;
    }
    private static boolean matches(TradeAtomicEconomy.Account expected,TradeAtomicEconomy.Account actual) {
        if(!expected.id().equals(actual.id()) || expected.copper()!=actual.copper() || expected.slots().size()!=actual.slots().size())return false;
        for(int i=0;i<expected.slots().size();i++){
            var a=expected.slots().get(i);var b=actual.slots().get(i);
            if(a==null || b==null){if(a!=b)return false;}
            else if(!a.sameItem(b)||a.count()!=b.count())return false;
        }
        return true;
    }
}
