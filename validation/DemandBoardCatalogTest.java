import java.util.*;
import ru.projectkingdom.npc.*;
public class DemandBoardCatalogTest {
 public static void main(String[] args) {
  DemandBoardCatalog c=new DemandBoardCatalog();
  UUID enterprise=UUID.randomUUID();
  var key=new EnterpriseDemandBoard.Key(enterprise,"tfc:ore");
  var first=new EnterpriseDemandBoard.Listing(key,UUID.randomUUID(),20,6);
  c.upsert(first); c.upsert(first);
  if(c.size()!=1)throw new AssertionError("duplicate");
  var updated=new EnterpriseDemandBoard.Listing(key,UUID.randomUUID(),15,6);
  c.upsert(updated);
  if(c.size()!=1 || !c.page(0,10).get(0).listingId().equals(updated.listingId()))throw new AssertionError("stale listing");
  c.upsert(new EnterpriseDemandBoard.Listing(new EnterpriseDemandBoard.Key(UUID.randomUUID(),"tfc:ore"),UUID.randomUUID(),2,7));
  if(c.page(0,1).size()!=1 || c.page(1,1).size()!=1 || !c.page(2,1).isEmpty())throw new AssertionError("pagination");
  c.remove(updated.listingId());if(c.size()!=1)throw new AssertionError("remove");
  try{c.page(-1,5);throw new AssertionError("negative offset");}catch(IllegalArgumentException expected){}
  System.out.println("PASS demand board catalog");
 }
}
