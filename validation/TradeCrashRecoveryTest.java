import ru.projectkingdom.npc.TradeWriteAheadJournal;
import java.nio.file.*;
import java.util.*;

/** Deterministic restart simulation: journal is reopened after each interruption. */
public class TradeCrashRecoveryTest {
    private static final String BEFORE="1".repeat(64), AFTER="2".repeat(64), MIXED="3".repeat(64);
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    private static void scenario(String phase, boolean prepared, boolean audited, String worldDigest,
                                 TradeWriteAheadJournal.RecoveryAssessment expected) throws Exception {
        Path dir=Files.createTempDirectory("pk-crash-"+phase);
        UUID id=UUID.randomUUID(), buyer=UUID.randomUUID(), seller=UUID.randomUUID();
        TradeWriteAheadJournal original=new TradeWriteAheadJournal(dir);
        if (prepared) original.prepare(id,buyer,seller,BEFORE+":"+AFTER);
        if (audited) original.markForAudit(id);
        // Simulate process termination: discard instance, reopen on the same directory.
        TradeWriteAheadJournal restarted=new TradeWriteAheadJournal(dir);
        if (!prepared) {check(restarted.unresolved().isEmpty(),phase+": phantom trade"); return;}
        var unresolved=restarted.unresolved();
        check(unresolved.size()==1,phase+": missing quarantine");
        check(unresolved.get(0).buyer().equals(buyer) && unresolved.get(0).seller().equals(seller),phase+": wrong parties");
        check(TradeWriteAheadJournal.assess(unresolved.get(0),worldDigest)==expected,phase+": wrong assessment");
        check(!"RECONCILED".equals(unresolved.get(0).state()),phase+": unexpectedly settled");
        // Assessment must be read-only: no automatic replay or unlocking, even for AFTER.
        check(restarted.unresolved().size()==1,phase+": auto-unlocked");
        check(restarted.read(id).state().equals(audited?"NEEDS_RECONCILIATION":"PREPARED"),phase+": mutated during assessment");
    }
    public static void main(String[] args) throws Exception {
        scenario("before-intent",false,false,BEFORE,null);
        scenario("after-intent-before-transfer",true,false,BEFORE,TradeWriteAheadJournal.RecoveryAssessment.BEFORE);
        scenario("after-items-before-money",true,false,MIXED,TradeWriteAheadJournal.RecoveryAssessment.MIXED_OR_CHANGED);
        scenario("after-transfer-before-audit",true,false,AFTER,TradeWriteAheadJournal.RecoveryAssessment.AFTER);
        scenario("after-audit",true,true,AFTER,TradeWriteAheadJournal.RecoveryAssessment.AFTER);
        System.out.println("PASS: 5 crash/restart journal scenarios (no automatic replay)");
    }
}
