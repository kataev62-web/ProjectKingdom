import ru.projectkingdom.npc.*;
import java.nio.file.*;
import java.util.*;

public class TradePhysicalProjectionAuditTest {
  static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
  static TradePhysicalProjectionAudit.Status audit(TradeAtomicEconomy e,UUID tx,
      TradeAtomicEconomy.Account b,TradeAtomicEconomy.Account s,
      TradeAtomicEconomy.Account bp,TradeAtomicEconomy.Account sp)throws Exception {
    return TradePhysicalProjectionAudit.inspect(e,tx,b,s,id -> id.equals(b.id())?bp:sp).status();
  }
  public static void main(String[] args)throws Exception {
    Path dir=Files.createTempDirectory("projection-audit-");
    UUID buyer=UUID.randomUUID(),seller=UUID.randomUUID(),world=UUID.randomUUID(),tx=UUID.randomUUID();
    TradeAtomicEconomy e=new TradeAtomicEconomy(dir.resolve("store"),world);
    var item=new TradeAtomicEconomy.Stack("minecraft:stone",new byte[]{7},2,64);
    var b=new TradeAtomicEconomy.Account(buyer,100,Arrays.asList((TradeAtomicEconomy.Stack)null));
    var s=new TradeAtomicEconomy.Account(seller,0,List.of(item));
    e.initialize(Map.of(buyer,b,seller,s));
    check(audit(e,tx,b,s,b,s)==TradePhysicalProjectionAudit.Status.INVALID_RECEIPT,"missing receipt");
    check(e.purchase(tx,buyer,seller,0,1,10)==TradeAtomicEconomy.Outcome.COMMITTED,"commit");
    var after=e.read();var ba=after.accounts().get(buyer);var sa=after.accounts().get(seller);
    check(audit(e,tx,b,s,b,s)==TradePhysicalProjectionAudit.Status.BOTH_BEFORE,"before");
    check(audit(e,tx,b,s,ba,sa)==TradePhysicalProjectionAudit.Status.BOTH_AFTER,"after");
    check(audit(e,tx,b,s,ba,s)==TradePhysicalProjectionAudit.Status.BUYER_ONLY_AFTER,"buyer partial");
    check(audit(e,tx,b,s,b,sa)==TradePhysicalProjectionAudit.Status.SELLER_ONLY_AFTER,"seller partial");
    check(audit(e,tx,b,s,null,sa)==TradePhysicalProjectionAudit.Status.UNAVAILABLE,"unloaded");
    var tampered=new TradeAtomicEconomy.Account(buyer,91,ba.slots());
    check(audit(e,tx,b,s,tampered,sa)==TradePhysicalProjectionAudit.Status.UNEXPECTED,"tamper");
    check(!TradePhysicalProjectionAudit.inspect(e,tx,b,s,id->after.accounts().get(id)).safeToTrade(),"no automatic unlock");
    System.out.println("PASS projection audit 8 assertions");
  }
}
