import ru.projectkingdom.npc.TradeWriteAheadJournal;
import java.nio.file.*;
import java.util.*;
public class TradeWriteAheadJournalTest {
 public static void main(String[] args) throws Exception {
  Path p=Files.createTempDirectory("pk-trades");
  var j=new TradeWriteAheadJournal(p);
  UUID id=UUID.randomUUID(), a=UUID.randomUUID(), b=UUID.randomUUID();
  String hash="a".repeat(64);
  j.prepare(id,a,b,hash);
  if(j.unresolved().size()!=1 || !j.read(id).state().equals("PREPARED"))throw new AssertionError();
  try {j.prepare(id,a,b,hash);throw new AssertionError("duplicate accepted");}catch(FileAlreadyExistsException expected){}
  j.markForAudit(id);
  if(!j.read(id).state().equals("NEEDS_RECONCILIATION"))throw new AssertionError();
  j.closeReconciled(id);
  if (!"RECONCILED".equals(j.read(id).state()) || !j.unresolved().isEmpty()) throw new AssertionError("not closed");
  try {j.prepare(id,a,b,hash);throw new AssertionError("closed id reused");}catch(FileAlreadyExistsException expected){}
  try {j.closeReconciled(id);throw new AssertionError("closed twice");}catch(java.io.IOException expected){}
  if (!new TradeWriteAheadJournal(p).unresolved().isEmpty()) throw new AssertionError("restart reopened receipt");
  if (new TradeWriteAheadJournal(p).allEntries().size()!=1) throw new AssertionError("terminal receipt must be discoverable after crash");
  Files.writeString(p.resolve(id+".txn"),"corrupt");
  try {j.read(id);throw new AssertionError("corrupt record accepted");}catch(java.io.IOException expected){}
  Files.delete(p.resolve(id+".txn"));
  UUID second=UUID.randomUUID();
  String before="1".repeat(64), after="2".repeat(64);
  var restored=new TradeWriteAheadJournal(p);
  restored.prepare(second,a,b,before+":"+after);
  var entry=restored.read(second);
  if (TradeWriteAheadJournal.assess(entry,before)!=TradeWriteAheadJournal.RecoveryAssessment.BEFORE) throw new AssertionError("before");
  if (TradeWriteAheadJournal.assess(entry,after)!=TradeWriteAheadJournal.RecoveryAssessment.AFTER) throw new AssertionError("after");
  if (TradeWriteAheadJournal.assess(entry,"3".repeat(64))!=TradeWriteAheadJournal.RecoveryAssessment.MIXED_OR_CHANGED) throw new AssertionError("mixed");
  if (TradeWriteAheadJournal.assess(new TradeWriteAheadJournal.Entry(second,a,b,hash,"PREPARED"),hash)!=TradeWriteAheadJournal.RecoveryAssessment.LEGACY_UNVERIFIABLE) throw new AssertionError("legacy");
  if (restored.unresolved().size()!=1) throw new AssertionError("recovery intent missing");
  System.out.println("PASS: journal durable record / duplicate / audit / corruption / before-after-mixed recovery");
 }
}
