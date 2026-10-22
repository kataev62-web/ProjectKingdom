import ru.projectkingdom.npc.*;
import java.nio.file.*;
import java.util.*;
public class TradePhysicalReconciliationTest {
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception {
  var world=UUID.randomUUID();var buyer=UUID.randomUUID();var seller=UUID.randomUUID();
  var econ=new TradeAtomicEconomy(Files.createTempDirectory("physical-gate"),world);
  var b=new TradeAtomicEconomy.Account(buyer,100,Arrays.asList((TradeAtomicEconomy.Stack)null));
  var s=new TradeAtomicEconomy.Account(seller,0,List.of(new TradeAtomicEconomy.Stack("tfc:ore",new byte[]{1,2},2,64)));
  econ.initialize(Map.of(buyer,b,seller,s));
  Map<UUID,TradeAtomicEconomy.Account> physical=new HashMap<>(Map.of(buyer,b,seller,s));
  ok(TradePhysicalReconciliation.inspectAll(econ,List.of(buyer,seller),physical::get)==TradePhysicalReconciliation.Result.MATCH,"same physical state");
  physical.put(seller,new TradeAtomicEconomy.Account(seller,0,List.of(new TradeAtomicEconomy.Stack("tfc:ore",new byte[]{9},2,64))));
  ok(TradePhysicalReconciliation.inspectAll(econ,List.of(buyer,seller),physical::get)==TradePhysicalReconciliation.Result.MISMATCH,"NBT mismatch");
  physical.put(seller,s);
  physical.remove(buyer);
  ok(TradePhysicalReconciliation.inspectAll(econ,List.of(buyer,seller),physical::get)==TradePhysicalReconciliation.Result.UNAVAILABLE,"unloaded chest");
  ok(TradePhysicalReconciliation.inspect(econ,UUID.randomUUID(),physical::get)==TradePhysicalReconciliation.Result.UNKNOWN_ACCOUNT,"unknown account");
  ok(TradePhysicalReconciliation.inspectAll(econ,List.of(),physical::get)==TradePhysicalReconciliation.Result.UNAVAILABLE,"empty scope cannot pass");
  System.out.println("PASS TradePhysicalReconciliationTest");
 }
}
