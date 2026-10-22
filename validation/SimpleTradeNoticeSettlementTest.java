import ru.projectkingdom.npc.*;
import java.nio.file.*;
import java.util.*;

public class SimpleTradeNoticeSettlementTest {
    static void check(boolean b) { if (!b) throw new AssertionError(); }
    static final class Physical implements SimpleTradeNoticeSettlement.PhysicalTrade {
        int sellerOre=40, warehouseOre=0; long cash=1000, wallet=0;
        public boolean hasGoods(String id,int n) { return sellerOre>=n; }
        public boolean hasCoins(long n) { return cash>=n; }
        public boolean canReceiveGoods(String id,int n) { return warehouseOre+n<=100; }
        public boolean canReceiveCoins(long n) { return wallet+n<=10000; }
        public boolean transfer(String id,int n,long cost) {
            if (!hasGoods(id,n)||!hasCoins(cost)||!canReceiveGoods(id,n)||!canReceiveCoins(cost)) return false;
            sellerOre-=n; warehouseOre+=n; cash-=cost; wallet+=cost; return true;
        }
    }
    public static void main(String[] args) throws Exception {
        Path p=Files.createTempDirectory("kingdom-local-sale").resolve("board.bin");
        UUID buyer=UUID.randomUUID(), notice=UUID.randomUUID();
        var board=new DurableSimpleTradeNoticeBoard(p);
        board.post(new SimpleTradeNoticeBoard.Notice(notice,buyer,SimpleTradeNoticeBoard.Side.BUY,
            "tfc:ore/normal_hematite",100,5,"Кузница"));
        var service=new SimpleTradeNoticeSettlement(board); var inv=new Physical();
        check(service.sell(notice,buyer,"minecraft:stone",20,inv)==SimpleTradeNoticeSettlement.Result.WRONG_ITEM);
        check(service.sell(notice,buyer,"tfc:ore/normal_hematite",101,inv)==SimpleTradeNoticeSettlement.Result.INVALID_AMOUNT);
        check(service.sell(notice,buyer,"tfc:ore/normal_hematite",20,inv)==SimpleTradeNoticeSettlement.Result.SOLD);
        check(inv.sellerOre==20 && inv.warehouseOre==20 && inv.cash==900 && inv.wallet==100);
        check(board.notice(notice).quantity()==80);
        check(new DurableSimpleTradeNoticeBoard(p).notice(notice).quantity()==80);
        check(service.sell(notice,buyer,"tfc:ore/normal_hematite",30,inv)==SimpleTradeNoticeSettlement.Result.NOT_ENOUGH_GOODS);
        check(service.sell(notice,UUID.randomUUID(),"tfc:ore/normal_hematite",1,inv)==SimpleTradeNoticeSettlement.Result.WRONG_BUYER);
        check(!service.needsReview());
        System.out.println("PASS local physical notice settlement (7 checks)");
    }
}
