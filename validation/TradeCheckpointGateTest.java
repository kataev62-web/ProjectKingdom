import ru.projectkingdom.npc.*;
import java.nio.file.*;
import java.util.*;
public class TradeCheckpointGateTest {
  public static void main(String[] args) throws Exception {
    Path dir=Files.createTempDirectory("pk-checkpoint-gate-");
    var ledger=new TradeCheckpointLedger(dir);
    UUID trade=UUID.randomUUID(); String digest="a".repeat(64);
    var gate=new TradeCheckpointGate(ledger);
    if(gate.candidate(trade,digest,"world_1",7))throw new AssertionError("missing receipt");
    ledger.record(new TradeCheckpointLedger.Receipt(trade,digest,"world_1",7));
    if(!gate.candidate(trade,digest,"world_1",7))throw new AssertionError("matching receipt");
    if(gate.candidate(trade,digest,"world_2",7))throw new AssertionError("other world");
    if(gate.candidate(trade,digest,"world_1",6))throw new AssertionError("stale epoch");
    if(gate.candidate(trade,"b".repeat(64),"world_1",7))throw new AssertionError("other inventory");
    // A matching receipt is only a candidate: no API on this gate may mutate or unlock firms.
    System.out.println("PASS TradeCheckpointGateTest");
  }
}
