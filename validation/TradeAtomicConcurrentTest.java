import ru.projectkingdom.npc.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
public class TradeAtomicConcurrentTest {
 static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception {
  var dir=Files.createTempDirectory("kingdom-concurrent");var world=UUID.randomUUID();var buyer=UUID.randomUUID();var seller=UUID.randomUUID();
  var economy=new TradeAtomicEconomy(dir,world);
  economy.initialize(Map.of(buyer,new TradeAtomicEconomy.Account(buyer,100,Arrays.asList((TradeAtomicEconomy.Stack)null)),seller,new TradeAtomicEconomy.Account(seller,0,List.of(new TradeAtomicEconomy.Stack("tfc:ore",new byte[0],1,64)))));
  var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  Callable<TradeAtomicEconomy.Outcome> trade=()->{start.await();return new TradeAtomicEconomy(dir,world).purchase(UUID.randomUUID(),buyer,seller,0,1,10);};
  var a=pool.submit(trade);var b=pool.submit(trade);start.countDown();
  try {var x=a.get();var y=b.get();check((x==TradeAtomicEconomy.Outcome.COMMITTED && y==TradeAtomicEconomy.Outcome.INSUFFICIENT_STOCK)||(y==TradeAtomicEconomy.Outcome.COMMITTED && x==TradeAtomicEconomy.Outcome.INSUFFICIENT_STOCK),"one purchase only");}
  finally{pool.shutdownNow();}
  check(new TradeAtomicEconomy(dir,world).read().accounts().get(seller).copper()==10,"cash conserved");
  // Same transaction ID retried concurrently must never pay twice.
  var dir2=Files.createTempDirectory("kingdom-same-tx");var e2=new TradeAtomicEconomy(dir2,world);
  e2.initialize(Map.of(buyer,new TradeAtomicEconomy.Account(buyer,100,Arrays.asList((TradeAtomicEconomy.Stack)null)),seller,new TradeAtomicEconomy.Account(seller,0,List.of(new TradeAtomicEconomy.Stack("tfc:ore",new byte[0],2,64)))));
  var sameTx=UUID.randomUUID();var latch2=new CountDownLatch(1);var pool2=Executors.newFixedThreadPool(2);
  Callable<TradeAtomicEconomy.Outcome> retry=()->{latch2.await();return new TradeAtomicEconomy(dir2,world).purchase(sameTx,buyer,seller,0,1,10);};
  var r1=pool2.submit(retry);var r2=pool2.submit(retry);latch2.countDown();
  try {var x=r1.get();var y=r2.get();check((x==TradeAtomicEconomy.Outcome.COMMITTED && y==TradeAtomicEconomy.Outcome.ALREADY_COMMITTED)||(y==TradeAtomicEconomy.Outcome.COMMITTED && x==TradeAtomicEconomy.Outcome.ALREADY_COMMITTED),"same tx idempotent");}
  finally{pool2.shutdownNow();}
  var after=e2.read();check(after.accounts().get(seller).copper()==10 && after.accounts().get(seller).slots().get(0).count()==1,"same tx no double charge or delivery");
  System.out.println("PASS TradeAtomicConcurrentTest");
 }
}
