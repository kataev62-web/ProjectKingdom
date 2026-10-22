import ru.projectkingdom.npc.*;
import java.util.*;
public class EnterpriseDemandBoardTest {
 static void yes(boolean ok) { if(!ok) throw new AssertionError(); }
 public static void main(String[] args) {
  var key=new EnterpriseDemandBoard.Key(UUID.randomUUID(),"tfc:ore");
  var limits=new EnterpriseStockPolicy.Thresholds(20,100);
  var a=EnterpriseDemandBoard.refresh(null,key,limits,15,10,6);
  yes(a.remaining()==75);
  var b=EnterpriseDemandBoard.refresh(a,key,limits,15,20,6);
  yes(b.remaining()==65 && b.listingId().equals(a.listingId()));
  var ids=new HashSet<UUID>();
  var delivery=new EnterpriseDemandBoard.Delivery(UUID.randomUUID(),b.listingId(),25,6);
  yes(EnterpriseDemandBoard.check(b,delivery,ids,150)==EnterpriseDemandBoard.Decision.ACCEPT);
  yes(EnterpriseDemandBoard.check(b,delivery,ids,149)==EnterpriseDemandBoard.Decision.INSUFFICIENT_COINS);
  yes(EnterpriseDemandBoard.check(b,new EnterpriseDemandBoard.Delivery(UUID.randomUUID(),b.listingId(),25,7),ids,1000)==EnterpriseDemandBoard.Decision.PRICE_TOO_HIGH);
  var c=EnterpriseDemandBoard.committed(b,delivery,ids);
  yes(c.remaining()==40);
  yes(EnterpriseDemandBoard.check(c,delivery,ids,1000)==EnterpriseDemandBoard.Decision.DUPLICATE);
  yes(EnterpriseDemandBoard.committed(c,delivery,ids)==c);
  yes(EnterpriseDemandBoard.refresh(c,key,limits,20,0,6)==null);
  System.out.println("PASS demand board 9 checks");
 }
}
