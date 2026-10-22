import ru.projectkingdom.npc.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class TradeProjectionConcurrencyTest {
  static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
  static boolean same(TradeAtomicEconomy.Account a, TradeAtomicEconomy.Account b) {
    if (a == null || b == null) return a == b;
    if (!a.id().equals(b.id()) || a.copper() != b.copper() || a.slots().size() != b.slots().size()) return false;
    for (int i=0;i<a.slots().size();i++) {
      var x=a.slots().get(i);var y=b.slots().get(i);
      if (x==null||y==null) {if(x!=y)return false;}
      else if(x.count()!=y.count()||!x.sameItem(y))return false;
    }
    return true;
  }
  public static void main(String[] args) throws Exception {
    var buyer=UUID.randomUUID();var seller=UUID.randomUUID();var tx=UUID.randomUUID();
    var economy=new TradeAtomicEconomy(Files.createTempDirectory("projection-concurrent"),UUID.randomUUID());
    var b=new TradeAtomicEconomy.Account(buyer,100,Arrays.asList((TradeAtomicEconomy.Stack)null));
    var s=new TradeAtomicEconomy.Account(seller,0,List.of(new TradeAtomicEconomy.Stack("minecraft:stone",new byte[]{1},2,64)));
    economy.initialize(Map.of(buyer,b,seller,s));
    check(economy.purchase(tx,buyer,seller,0,1,10)==TradeAtomicEconomy.Outcome.COMMITTED,"committed");
    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
    class Physical implements TradePhysicalProjectionCoordinator.PhysicalWriter {
      final Map<UUID,TradeAtomicEconomy.Account> map=new HashMap<>(Map.of(buyer,b,seller,s));
      int writes;
      public TradeAtomicEconomy.Account read(UUID id){return map.get(id);}
      public boolean compareAndSet(UUID id,TradeAtomicEconomy.Account before,TradeAtomicEconomy.Account after) throws IOException {
        if (!same(map.get(id),before)) return false;
        if (writes==0) {
          entered.countDown();
          try {if (!release.await(5,TimeUnit.SECONDS)) throw new IOException("timeout");}
          catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
        }
        map.put(id,after);writes++;return true;
      }
    }
    var physical=new Physical();var pool=Executors.newFixedThreadPool(2);
    try {
      Future<TradePhysicalProjectionCoordinator.Result> first=pool.submit(()->TradePhysicalProjectionCoordinator.project(economy,tx,b,s,physical));
      check(entered.await(5,TimeUnit.SECONDS),"first write entered");
      Future<TradePhysicalProjectionCoordinator.Result> second=pool.submit(()->TradePhysicalProjectionCoordinator.project(economy,tx,b,s,physical));
      Thread.sleep(100);
      check(!second.isDone(),"second projection must wait while first writes");
      release.countDown();
      check(first.get(5,TimeUnit.SECONDS)==TradePhysicalProjectionCoordinator.Result.APPLIED,"first applies");
      check(second.get(5,TimeUnit.SECONDS)==TradePhysicalProjectionCoordinator.Result.ALREADY_APPLIED,"second idempotent");
      check(physical.writes==2,"each enterprise written once");
      System.out.println("PASS projection concurrency: serialized, idempotent, 2 writes");
    } finally {release.countDown();pool.shutdownNow();}
  }
}
