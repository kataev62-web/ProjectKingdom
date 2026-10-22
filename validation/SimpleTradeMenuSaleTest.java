import java.io.IOException;
import java.util.UUID;
import ru.projectkingdom.npc.SimpleTradeMenuSale;

public class SimpleTradeMenuSaleTest {
    static class Port implements SimpleTradeMenuSale.PhysicalPort {
        int offered=20, stock=0, capacity=64;
        long cash=100, wallet=0, walletCapacity=1000;
        int calls;
        public int offered(String item) { return offered; }
        public int warehouseSpace(String item) { return capacity-stock; }
        public long cashboxCopper() { return cash; }
        public long walletSpaceCopper() { return walletCapacity-wallet; }
        public void settle(String item,int qty,long amount) throws IOException {
            if(offered<qty || stock+qty>capacity || cash<amount || wallet+amount>walletCapacity) throw new IOException("changed");
            offered-=qty;stock+=qty;cash-=amount;wallet+=amount;calls++;
        }
    }
    static void check(boolean ok) { if(!ok) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        var engine=new SimpleTradeMenuSale();var port=new Port();
        var offer=new SimpleTradeMenuSale.Offer(UUID.randomUUID(),"tfc:ore",20,5);
        check(engine.sell(offer,port).sold());
        check(port.offered==0 && port.stock==20 && port.cash==0 && port.wallet==100);
        check(!engine.sell(offer,port).sold() && port.calls==1);
        var second=new SimpleTradeMenuSale.Offer(UUID.randomUUID(),"tfc:ore",1,5);
        check(engine.sell(second,port).reason().equals("NOT_ENOUGH_GOODS"));
        port.offered=1;
        check(engine.sell(second,port).reason().equals("CASHBOX_EMPTY"));
        port.cash=5;port.walletCapacity=100;
        check(engine.sell(second,port).reason().equals("WALLET_FULL"));
        port.walletCapacity=101;port.capacity=20;
        check(engine.sell(second,port).reason().equals("WAREHOUSE_FULL"));
        System.out.println("PASS simple menu physical sale contract");
    }
}
