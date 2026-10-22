import ru.projectkingdom.npc.*;
import java.nio.file.*;
import java.util.*;

public class SimpleLocalTradeServiceTest {
    static int checks;
    static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("check " + checks); }
    static class Port implements SimpleLocalTradeService.PhysicalExchange {
        int visitorGoods=30, shopGoods=50, visitorGoodsCapacity=100, shopGoodsCapacity=100;
        long visitorMoney=200, shopMoney=500, visitorMoneyCapacity=1000, shopMoneyCapacity=1000;
        boolean fail, throwAfterTransfer;
        public boolean goodsAvailable(SimpleTradeNoticeBoard.Side s,String id,int q) { return (s==SimpleTradeNoticeBoard.Side.BUY?visitorGoods:shopGoods)>=q; }
        public boolean moneyAvailable(SimpleTradeNoticeBoard.Side s,long c) { return (s==SimpleTradeNoticeBoard.Side.BUY?shopMoney:visitorMoney)>=c; }
        public boolean goodsFit(SimpleTradeNoticeBoard.Side s,String id,int q) { return (s==SimpleTradeNoticeBoard.Side.BUY?shopGoods+q<=shopGoodsCapacity:visitorGoods+q<=visitorGoodsCapacity); }
        public boolean moneyFits(SimpleTradeNoticeBoard.Side s,long c) { return (s==SimpleTradeNoticeBoard.Side.BUY?visitorMoney+c<=visitorMoneyCapacity:shopMoney+c<=shopMoneyCapacity); }
        public boolean exchange(SimpleTradeNoticeBoard.Side s,String id,int q,long c) {
            if (fail) return false;
            if (s==SimpleTradeNoticeBoard.Side.BUY) { visitorGoods-=q;shopGoods+=q;shopMoney-=c;visitorMoney+=c; }
            else { shopGoods-=q;visitorGoods+=q;visitorMoney-=c;shopMoney+=c; }
            if (throwAfterTransfer) throw new IllegalStateException("uncertain transfer");
            return true;
        }
    }
    public static void main(String[] args) throws Exception {
        Path file=Files.createTempDirectory("kingdom-trade-both").resolve("board.dat");
        UUID shop=UUID.randomUUID(), buy=UUID.randomUUID(), sell=UUID.randomUUID();
        var board=new DurableSimpleTradeNoticeBoard(file);
        board.post(new SimpleTradeNoticeBoard.Notice(buy,shop,SimpleTradeNoticeBoard.Side.BUY,"tfc:ore/normal_hematite",40,5,"Кузница"));
        board.post(new SimpleTradeNoticeBoard.Notice(sell,shop,SimpleTradeNoticeBoard.Side.SELL,"minecraft:oak_log",20,3,"Кузница"));
        check(board.offers(shop,SimpleTradeNoticeBoard.Side.BUY).size()==1);
        check(board.offers(shop,SimpleTradeNoticeBoard.Side.SELL).size()==1);
        check(board.offers(UUID.randomUUID(),SimpleTradeNoticeBoard.Side.SELL).isEmpty());
        var service=new SimpleLocalTradeService(board); var port=new Port();
        check(service.trade(buy,shop,"minecraft:stone",2,port)==SimpleLocalTradeService.Result.WRONG_ITEM);
        check(service.trade(buy,UUID.randomUUID(),"tfc:ore/normal_hematite",2,port)==SimpleLocalTradeService.Result.WRONG_SHOP);
        check(service.trade(buy,shop,"tfc:ore/normal_hematite",41,port)==SimpleLocalTradeService.Result.INVALID_QUANTITY);
        check(service.trade(buy,shop,"tfc:ore/normal_hematite",20,port)==SimpleLocalTradeService.Result.TRADED);
        check(port.visitorGoods==10 && port.shopGoods==70 && port.shopMoney==400 && port.visitorMoney==300);
        check(board.notice(buy).quantity()==20);
        check(service.trade(sell,shop,"minecraft:oak_log",10,port)==SimpleLocalTradeService.Result.TRADED);
        check(port.shopGoods==60 && port.visitorGoods==20 && port.visitorMoney==270 && port.shopMoney==430);
        check(board.notice(sell).quantity()==10);
        check(new DurableSimpleTradeNoticeBoard(file).notice(buy).quantity()==20);
        check(new DurableSimpleTradeNoticeBoard(file).notice(sell).quantity()==10);
        port.fail=true;
        check(service.trade(sell,shop,"minecraft:oak_log",1,port)==SimpleLocalTradeService.Result.NO_SPACE);
        check(!service.needsReview());
        port.fail=false; port.throwAfterTransfer=true;
        check(service.trade(sell,shop,"minecraft:oak_log",1,port)==SimpleLocalTradeService.Result.NEEDS_REVIEW);
        check(service.needsReview());
        check(service.trade(buy,shop,"tfc:ore/normal_hematite",1,port)==SimpleLocalTradeService.Result.NEEDS_REVIEW);
        System.out.println("PASS two-way local physical trade: "+checks+" checks");
    }
}
