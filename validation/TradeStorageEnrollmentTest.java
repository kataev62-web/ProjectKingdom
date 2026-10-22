import java.util.UUID;
import ru.projectkingdom.npc.*;
public class TradeStorageEnrollmentTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
  var guard=new TradeInventoryMutationGuard();
  var locations=new TradeProtectedLocations(guard);
  var managed=new TradeManagedInventory(guard,locations);
  var enrollment=new TradeStorageEnrollment(managed,locations);
  var loc=new TradeProtectedLocations.Location("minecraft:overworld",42L);
  var a=UUID.randomUUID();var b=UUID.randomUUID();
  check(!enrollment.mayAutomate(loc,a));
  check(!enrollment.restore(loc,null));
  check(enrollment.restore(loc,a));
  check(!enrollment.restore(loc,b));
  check(enrollment.mayAutomate(loc,a));
  try(var lease=guard.lock(a,b)){check(!enrollment.mayAutomate(loc,a));}
  check(enrollment.mayAutomate(loc,a));
  enrollment.unload(loc,b);check(enrollment.mayAutomate(loc,a));
  enrollment.unload(loc,a);check(!enrollment.mayAutomate(loc,a));
  System.out.println("PASS storage enrollment");
 }
}
