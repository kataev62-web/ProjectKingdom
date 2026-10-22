import ru.projectkingdom.npc.*;
import java.nio.file.*;
import java.util.*;

public class TradePhysicalPurchaseGateTest {
  static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
  public static void main(String[] args)throws Exception{
    Path dir=Files.createTempDirectory("physical-gate-");
    UUID buyer=UUID.randomUUID(),seller=UUID.randomUUID(),world=UUID.randomUUID();
    TradeAtomicEconomy economy=new TradeAtomicEconomy(dir.resolve("economy"),world);
    TradeAtomicEconomy.Stack stack=new TradeAtomicEconomy.Stack("minecraft:stone",new byte[]{1},3,64);
    economy.initialize(Map.of(buyer,new TradeAtomicEconomy.Account(buyer,100,Arrays.asList((TradeAtomicEconomy.Stack)null)),seller,new TradeAtomicEconomy.Account(seller,0,List.of(stack))));
    var tx=UUID.randomUUID();
    var blocked=TradePhysicalPurchaseGate.purchase(economy,tx,buyer,seller,0,1,10,id->null);
    check(blocked==TradePhysicalPurchaseGate.Result.UNAVAILABLE,"missing physical inventory blocked");
    check(economy.read().completed().isEmpty(),"no durable mutation on unavailable");
    var wrong=TradePhysicalPurchaseGate.purchase(economy,tx,buyer,seller,0,1,10,id->new TradeAtomicEconomy.Account(id,999,Arrays.asList((TradeAtomicEconomy.Stack)null)));
    check(wrong==TradePhysicalPurchaseGate.Result.MISMATCH,"mismatch blocked");
    check(economy.read().completed().isEmpty(),"no durable mutation on mismatch");
    var before=economy.read();
    var accepted=TradePhysicalPurchaseGate.purchase(economy,tx,buyer,seller,0,1,10,id->before.accounts().get(id));
    check(accepted==TradePhysicalPurchaseGate.Result.COMMITTED_REQUIRES_PROJECTION,"commit requires physical projection");
    check(economy.read().completed().contains(tx),"receipt persisted");
    var repeated=TradePhysicalPurchaseGate.purchase(economy,tx,buyer,seller,0,1,10,id->before.accounts().get(id));
    check(repeated==TradePhysicalPurchaseGate.Result.ALREADY_COMMITTED,"repeat does not transfer again");
    System.out.println("PASS physical purchase preflight and fail-closed tests");
  }
}
