package ru.projectkingdom.npc;

import java.util.function.BooleanSupplier;

/**
 * Shared two-view in-memory commit protocol.
 * This is NOT power-loss atomic: worlds with an uncertain restore must be
 * quarantined by the caller, not retried as if no money moved.
 */
final class PhysicalCashCommitGate {
    private PhysicalCashCommitGate() {}

    static boolean commit(Runnable debit, Runnable credit, Runnable undoCredit,
                          Runnable undoDebit, BooleanSupplier restored) {
        if(debit==null||credit==null||undoCredit==null||undoDebit==null||restored==null)
            return false;
        try{
            debit.run();
            credit.run();
            return true;
        }catch(RuntimeException failure){
            RuntimeException rollbackError=null;
            try{undoCredit.run();}catch(RuntimeException ex){rollbackError=ex;}
            try{undoDebit.run();}catch(RuntimeException ex){
                if(rollbackError==null)rollbackError=ex;
                else rollbackError.addSuppressed(ex);
            }
            boolean exact=false;
            try{exact=restored.getAsBoolean();}catch(RuntimeException ex){
                if(rollbackError==null)rollbackError=ex;
                else rollbackError.addSuppressed(ex);
            }
            if(rollbackError!=null||!exact){
                IllegalStateException unsafe=new IllegalStateException(
                        "Physical cash move is uncertain; quarantine both storages and reconcile manually",failure);
                if(rollbackError!=null)unsafe.addSuppressed(rollbackError);
                throw unsafe;
            }
            return false;
        }
    }
}
