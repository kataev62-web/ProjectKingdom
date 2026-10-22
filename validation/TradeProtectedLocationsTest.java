import ru.projectkingdom.npc.TradeInventoryMutationGuard;
import ru.projectkingdom.npc.TradeProtectedLocations;
import java.util.UUID;
public class TradeProtectedLocationsTest {
  public static void main(String[] args) {
    var g=new TradeInventoryMutationGuard(); var index=new TradeProtectedLocations(g);
    var a=UUID.randomUUID();var b=UUID.randomUUID();
    var x=new TradeProtectedLocations.Location("minecraft:overworld",12);
    var nether=new TradeProtectedLocations.Location("minecraft:the_nether",12);
    if(!index.mayMutate(x))throw new AssertionError("unregistered location");
    if(!index.enroll(x,a)||index.enroll(x,b))throw new AssertionError("ownership collision");
    try(var lease=g.lock(a,b)) {
      if(index.mayMutate(x))throw new AssertionError("must deny while locked");
      if(!index.mayMutate(nether))throw new AssertionError("dimension isolation");
      if(index.remove(x,b))throw new AssertionError("foreign removal");
    }
    if(!index.mayMutate(x)||!index.remove(x,a))throw new AssertionError("unlock and remove");
    System.out.println("PASS protected location index");
  }
}
