import java.util.*;
import ru.projectkingdom.npc.SimpleTradeNoticeBoard;
public class SimpleTradeNoticeBoardTest {
  public static void main(String[] args) {
    var b = new SimpleTradeNoticeBoard(); UUID buyer=UUID.randomUUID(), seller=UUID.randomUUID();
    UUID bid=UUID.randomUUID(), ask=UUID.randomUUID();
    b.post(new SimpleTradeNoticeBoard.Notice(bid,buyer,SimpleTradeNoticeBoard.Side.BUY,"tfc:ore/normal_hematite",100,5,"Кузница"));
    b.post(new SimpleTradeNoticeBoard.Notice(ask,seller,SimpleTradeNoticeBoard.Side.SELL,"minecraft:oak_log",30,8,"Лавка"));
    try {
      b.post(new SimpleTradeNoticeBoard.Notice(bid,seller,SimpleTradeNoticeBoard.Side.BUY,"tfc:ore/normal_hematite",999,1,"Чужая кузница"));
      throw new AssertionError("owner hijack allowed");
    } catch (IllegalArgumentException expected) {}
    check(b.page(SimpleTradeNoticeBoard.Side.BUY,0,50).get(0).owner().equals(buyer));
    check(b.page(SimpleTradeNoticeBoard.Side.BUY,0,50).size()==1);
    check(b.page(SimpleTradeNoticeBoard.Side.SELL,0,50).size()==1);
    check(!b.sold(bid,101)); check(b.sold(bid,20));
    check(b.page(SimpleTradeNoticeBoard.Side.BUY,0,50).get(0).quantity()==80);
    check(b.sold(bid,80)); check(b.page(SimpleTradeNoticeBoard.Side.BUY,0,50).isEmpty());
    check(b.page(SimpleTradeNoticeBoard.Side.SELL,0,50).size()==1);
    try { new SimpleTradeNoticeBoard.Notice(UUID.randomUUID(),buyer,SimpleTradeNoticeBoard.Side.BUY,"not-an-id",1,1,"x"); throw new AssertionError(); }
    catch(IllegalArgumentException expected) {}
    System.out.println("PASS informational BUY/SELL board");
  }
  static void check(boolean ok) { if (!ok) throw new AssertionError(); }
}
