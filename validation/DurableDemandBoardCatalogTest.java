import java.nio.file.*;
import java.util.*;
import ru.projectkingdom.npc.*;
public class DurableDemandBoardCatalogTest {
 public static void main(String[] args) throws Exception {
  Path path=Files.createTempDirectory("kingdom-board-test").resolve("board.bin");
  UUID owner=UUID.randomUUID(), id=UUID.randomUUID(), deliveryId=UUID.randomUUID();
  var listing=new EnterpriseDemandBoard.Listing(new EnterpriseDemandBoard.Key(owner,"tfc:ore"),id,20,6);
  var board=new DurableDemandBoardCatalog(path); board.upsert(listing);
  board=new DurableDemandBoardCatalog(path);
  if(board.size()!=1 || board.page(0,10).get(0).remaining()!=20)throw new AssertionError("restore");
  var delivery=new EnterpriseDemandBoard.Delivery(deliveryId,id,7,5);
  if(!board.acknowledgeCommitted(delivery))throw new AssertionError("commit");
  board=new DurableDemandBoardCatalog(path);
  if(board.page(0,10).get(0).remaining()!=13 || !board.acknowledged(deliveryId))throw new AssertionError("durable delivery");
  if(board.acknowledgeCommitted(delivery))throw new AssertionError("duplicate accepted");
  if(board.page(0,10).get(0).remaining()!=13)throw new AssertionError("double decrement");
  byte[] data=Files.readAllBytes(path);data[data.length/2]^=1;Files.write(path,data);
  try {new DurableDemandBoardCatalog(path);throw new AssertionError("corruption accepted");}
  catch(java.io.IOException expected) {}
  System.out.println("PASS durable demand board");
 }
}
