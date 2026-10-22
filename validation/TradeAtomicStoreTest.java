import ru.projectkingdom.npc.TradeAtomicStore;
import java.nio.file.*;
import java.util.*;
public class TradeAtomicStoreTest {
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("kingdom-atomic-test");
  UUID world=UUID.randomUUID(); TradeAtomicStore store=new TradeAtomicStore(root,world);
  check(store.read().isEmpty(),"initially empty");
  byte[] first="cash=100;goods=5".getBytes();
  check(store.commit(0,first)==1,"first generation");
  check(Arrays.equals(store.read().orElseThrow().payload(),first),"round trip");
  try {store.commit(0,"double-spend".getBytes());throw new AssertionError("stale write accepted");}catch(TradeAtomicStore.StaleGenerationException expected){}
  Files.write(root.resolve("interrupted-write.tmp"),"partial".getBytes());
  check(Arrays.equals(new TradeAtomicStore(root,world).read().orElseThrow().payload(),first),"orphan temp ignored");
  byte[] second="cash=120;goods=3".getBytes();
  check(store.commit(1,second)==2,"second generation");
  check(new TradeAtomicStore(root,world).read().orElseThrow().generation()==2,"restart generation");
  try {new TradeAtomicStore(root,UUID.randomUUID()).read();throw new AssertionError("foreign world accepted");}catch(java.io.IOException expected){}
  Path file=root.resolve("economy.snapshot");byte[] broken=Files.readAllBytes(file);broken[broken.length-1]^=1;Files.write(file,broken);
  try {store.read();throw new AssertionError("corruption accepted");}catch(java.io.IOException expected){}
  System.out.println("PASS TradeAtomicStoreTest");
 }
}
