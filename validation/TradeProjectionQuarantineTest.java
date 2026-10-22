import ru.projectkingdom.npc.*;
import java.nio.file.*;
import java.util.*;
public class TradeProjectionQuarantineTest {
  static void check(boolean v) { if (!v) throw new AssertionError(); }
  public static void main(String[] args) throws Exception {
    Path path=Files.createTempDirectory("kingdom-quarantine-");
    UUID tx=UUID.randomUUID(), a=UUID.randomUUID(), b=UUID.randomUUID();
    var first=new TradeProjectionQuarantine(path);
    check(!first.isQuarantined(tx));
    check(first.begin(tx,a,b));
    check(first.isQuarantined(tx));
    var restarted=new TradeProjectionQuarantine(path);
    check(restarted.isQuarantined(tx));
    check(!restarted.begin(tx,a,b));
    check(Files.readString(path.resolve(tx+".pending")).contains(a.toString()));
    restarted.verifiedComplete(tx);
    check(!first.isQuarantined(tx));
    check(first.begin(tx,a,b));
    System.out.println("PASS durable quarantine 8 assertions");
  }
}
