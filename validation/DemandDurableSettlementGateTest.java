import java.nio.file.*;
import java.util.*;
import ru.projectkingdom.npc.*;
import static ru.projectkingdom.npc.DemandPhysicalSettlementEngine.*;
public class DemandDurableSettlementGateTest {
  static void check(boolean ok) { if (!ok) throw new AssertionError(); }
  public static void main(String[] args) throws Exception {
    Path root=Files.createTempDirectory("kingdom-settlement-");
    Inventory supplier=new Inventory(2,64), warehouse=new Inventory(2,64);
    Inventory cash=new Inventory(2,64), wallet=new Inventory(2,64);
    supplier.set(0,"tfc:ore",20); cash.set(0,SILVER,1);
    UUID id=UUID.randomUUID();
    var gate=new DemandDurableSettlementGate(root);
    check(gate.settle(id,"tfc:ore",20,5,supplier,warehouse,cash,wallet).status()==DemandDurableSettlementGate.Status.COMMITTED);
    check(supplier.count("tfc:ore")==0 && warehouse.count("tfc:ore")==20);
    check(cash.count(SILVER)==0 && wallet.count(SILVER)==1);
    var restarted=new DemandDurableSettlementGate(root);
    check(restarted.settle(id,"tfc:ore",20,5,supplier,warehouse,cash,wallet).status()==DemandDurableSettlementGate.Status.DUPLICATE);
    UUID interrupted=UUID.randomUUID();
    Files.writeString(root.resolve(interrupted+".txn"),"PREPARED\n"+interrupted+"\n");
    check(restarted.settle(interrupted,"tfc:ore",1,1,supplier,warehouse,cash,wallet).status()==DemandDurableSettlementGate.Status.NEEDS_REVIEW);
    check(restarted.inspect(interrupted)==DemandDurableSettlementGate.Status.NEEDS_REVIEW);
    // An unrelated delivery cannot spend from a potentially modified cashbox.
    UUID blocked=UUID.randomUUID();
    check(restarted.settle(blocked,"tfc:ore",1,1,supplier,warehouse,cash,wallet).status()==DemandDurableSettlementGate.Status.NEEDS_REVIEW);
    check(restarted.inspect(blocked)==null); // no new marker is written
    Files.writeString(root.resolve(interrupted+".txn"),"REJECTED\n"+interrupted+"\n");
    UUID bad=UUID.randomUUID();
    check(restarted.settle(bad,"tfc:ore",1,1,supplier,warehouse,cash,wallet).status()==DemandDurableSettlementGate.Status.REJECTED);
    check(new DemandDurableSettlementGate(root).inspect(bad)==DemandDurableSettlementGate.Status.REJECTED);
    System.out.println("PASS durable settlement gate: persisted receipt, restart duplicate, interruption quarantine, rejection");
  }
}
