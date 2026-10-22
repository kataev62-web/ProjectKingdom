import ru.projectkingdom.npc.*;
import java.util.UUID;

public class DemandPhysicalInventoryExchangeTest {
 static void ok(boolean b) { if (!b) throw new AssertionError(); }
 public static void main(String[] args) throws Exception {
  UUID enterprise=UUID.randomUUID(), listingId=UUID.randomUUID();
  var listing=new EnterpriseDemandBoard.Listing(new EnterpriseDemandBoard.Key(enterprise,"ore"),listingId,20,6);
  var delivery=new EnterpriseDemandBoard.Delivery(UUID.randomUUID(),listingId,4,5);
  class Store implements DemandPhysicalInventoryExchange.Ports {
   long goods=10, space=20, coins=100, coinSpace=200;
   public long cashboxCopper(UUID id) { return coins; }
   public DemandPhysicalTransferPlan.Snapshot snapshot(EnterpriseDemandBoard.Listing l,EnterpriseDemandBoard.Delivery d) {
    return new DemandPhysicalTransferPlan.Snapshot("ore",goods,space,coins,coinSpace);
   }
   public void moveGoods(EnterpriseDemandBoard.Listing l,int q) {goods-=q;space-=q;}
   public void moveCopper(UUID id,long q) {coins-=q;coinSpace-=q;}
  }
  Store store=new Store(); var exchange=new DemandPhysicalInventoryExchange(store);
  ok(exchange.exchange(listing,delivery)); ok(exchange.verified(listing,delivery));
  ok(store.goods==6 && store.space==16 && store.coins==80 && store.coinSpace==180);
  ok(!exchange.exchange(listing,delivery));
  Store broke=new Store(); var broken=new DemandPhysicalInventoryExchange(broke);
  ok(broken.exchange(listing,delivery)); broke.coins++;
  ok(!broken.verified(listing,delivery));
  Store poor=new Store();poor.coins=0;var rejected=new DemandPhysicalInventoryExchange(poor);
  ok(!rejected.exchange(listing,delivery));ok(poor.goods==10);
  System.out.println("PASS physical exchange adapter");
 }
}
