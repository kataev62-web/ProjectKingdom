import ru.projectkingdom.npc.*;
import java.nio.file.*;
import java.util.*;

public class DemandDeliveryCoordinatorTest {
 static void yes(boolean x){if(!x)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("demand-exchange-test");
  UUID enterprise=UUID.randomUUID(), listingId=UUID.randomUUID(), deliveryId=UUID.randomUUID();
  var board=new DurableDemandBoardCatalog(dir.resolve("board.bin"));
  board.upsert(new EnterpriseDemandBoard.Listing(new EnterpriseDemandBoard.Key(enterprise,"ore"),listingId,10,6));
  var coordinator=new DemandDeliveryCoordinator(board,dir.resolve("pending"));
  var delivery=new EnterpriseDemandBoard.Delivery(deliveryId,listingId,3,5);
  class Physical implements DemandDeliveryCoordinator.PhysicalExchange {
   int calls=0; boolean verify=true; long coins=100;
   public long availableCopper(UUID id){return coins;}
   public boolean exchange(EnterpriseDemandBoard.Listing l,EnterpriseDemandBoard.Delivery d){calls++;return true;}
   public boolean verified(EnterpriseDemandBoard.Listing l,EnterpriseDemandBoard.Delivery d){return verify;}
  }
  Physical p=new Physical();
  yes(coordinator.deliver(new EnterpriseDemandBoard.Delivery(UUID.randomUUID(),listingId,3,7),p)==DemandDeliveryCoordinator.Result.PRICE_TOO_HIGH);
  yes(p.calls==0);
  p.coins=1;
  yes(coordinator.deliver(delivery,p)==DemandDeliveryCoordinator.Result.INSUFFICIENT_COINS);
  p.coins=100; p.verify=false;
  yes(coordinator.deliver(delivery,p)==DemandDeliveryCoordinator.Result.NEEDS_REVIEW);
  yes(p.calls==1);
  yes(coordinator.deliver(delivery,p)==DemandDeliveryCoordinator.Result.NEEDS_REVIEW);
  yes(p.calls==1);
  var reopened=new DemandDeliveryCoordinator(new DurableDemandBoardCatalog(dir.resolve("board.bin")),dir.resolve("pending"));
  yes(reopened.deliver(delivery,p)==DemandDeliveryCoordinator.Result.NEEDS_REVIEW);
  yes(p.calls==1);
  var other=new EnterpriseDemandBoard.Delivery(UUID.randomUUID(),listingId,2,5);
  p.verify=true;
  // Unresolved earlier payment freezes the whole enterprise, not just its delivery ID.
  yes(reopened.deliver(other,p)==DemandDeliveryCoordinator.Result.NEEDS_REVIEW);
  yes(p.calls==1);
  // Explicit operator reconciliation in this test only: remove the marker after physical audit.
  Files.delete(dir.resolve("pending").resolve(deliveryId+".pending"));
  yes(reopened.deliver(other,p)==DemandDeliveryCoordinator.Result.COMMITTED);
  yes(reopened.deliver(other,p)==DemandDeliveryCoordinator.Result.DUPLICATE);
  yes(p.calls==2);
  yes(new DurableDemandBoardCatalog(dir.resolve("board.bin")).page(0,10).get(0).remaining()==8);
  System.out.println("PASS demand physical exchange coordination");
 }
}
