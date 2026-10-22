import ru.projectkingdom.npc.*;
import java.nio.file.*;
import java.util.*;
public class TradePreflightRaceTest {
  static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
  public static void main(String[] args)throws Exception {
    var e=new TradeAtomicEconomy(Files.createTempDirectory("race-").resolve("economy"),UUID.randomUUID());
    UUID b=UUID.randomUUID(),s=UUID.randomUUID();
    e.initialize(Map.of(b,new TradeAtomicEconomy.Account(b,100,Arrays.asList((TradeAtomicEconomy.Stack)null)),s,new TradeAtomicEconomy.Account(s,0,List.of(new TradeAtomicEconomy.Stack("minecraft:stone",new byte[0],2,64)))));
    var snapshot=e.read();
    var result=TradePhysicalPurchaseGate.purchase(e,UUID.randomUUID(),b,s,0,1,10,id->{
      // Simulate another trade committing after preflight begins, before both physical accounts are read.
      if(id.equals(b))check(e.purchase(UUID.randomUUID(),b,s,0,1,10)==TradeAtomicEconomy.Outcome.COMMITTED,"intervening trade");
      return snapshot.accounts().get(id);
    });
    check(result==TradePhysicalPurchaseGate.Result.STALE_VIEW,"stale physical preflight rejected");
    check(e.read().completed().size()==1,"no second trade after stale preflight");
    System.out.println("PASS preflight race rejected");
  }
}
