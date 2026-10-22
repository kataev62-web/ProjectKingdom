import ru.projectkingdom.npc.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
public class DemandCrossCoordinatorLockTest {
 static void yes(boolean ok) { if (!ok) throw new AssertionError(); }
 public static void main(String[] args) throws Exception {
  Path dir=Files.createTempDirectory("kingdom-cross-coordinator");
  UUID enterprise=UUID.randomUUID(), listingId=UUID.randomUUID();
  Path boardFile=dir.resolve("board.bin"), pending=dir.resolve("pending");
  var initial=new DurableDemandBoardCatalog(boardFile);
  initial.upsert(new EnterpriseDemandBoard.Listing(new EnterpriseDemandBoard.Key(enterprise,"ore"),listingId,10,6));
  var first=new DemandDeliveryCoordinator(new DurableDemandBoardCatalog(boardFile),pending);
  var second=new DemandDeliveryCoordinator(new DurableDemandBoardCatalog(boardFile),pending);
  CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
  class Physical implements DemandDeliveryCoordinator.PhysicalExchange {
   int transfers=0;
   public long availableCopper(UUID e){return 100;}
   public synchronized boolean exchange(EnterpriseDemandBoard.Listing l, EnterpriseDemandBoard.Delivery d) {
    transfers++; entered.countDown();
    try { if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("timeout"); }
    catch(InterruptedException ex){Thread.currentThread().interrupt();throw new AssertionError(ex);}
    return true;
   }
   public boolean verified(EnterpriseDemandBoard.Listing l, EnterpriseDemandBoard.Delivery d){return true;}
  }
  Physical physical=new Physical();
  var a=new EnterpriseDemandBoard.Delivery(UUID.randomUUID(),listingId,3,5);
  var b=new EnterpriseDemandBoard.Delivery(UUID.randomUUID(),listingId,3,5);
  ExecutorService pool=Executors.newSingleThreadExecutor();
  try {
   Future<DemandDeliveryCoordinator.Result> f=pool.submit(()->first.deliver(a,physical));
   yes(entered.await(5,TimeUnit.SECONDS));
   yes(second.deliver(b,physical)==DemandDeliveryCoordinator.Result.NEEDS_REVIEW);
   release.countDown();
   yes(f.get(5,TimeUnit.SECONDS)==DemandDeliveryCoordinator.Result.COMMITTED);
   yes(physical.transfers==1);
   yes(second.deliver(a,physical)==DemandDeliveryCoordinator.Result.DUPLICATE);
   yes(new DurableDemandBoardCatalog(boardFile).page(0,10).get(0).remaining()==7);
  } finally { release.countDown();pool.shutdownNow(); }
  System.out.println("PASS cross coordinator exclusive delivery");
 }
}
