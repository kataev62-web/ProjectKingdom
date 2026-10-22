import ru.projectkingdom.npc.TradeInventoryMutationGuard;
import java.util.UUID;
public class TradeInventoryMutationGuardTest {
  public static void main(String[] args) throws Exception {
    var guard=new TradeInventoryMutationGuard();
    UUID a=UUID.randomUUID(), b=UUID.randomUUID();
    if(!guard.canMutate(a))throw new AssertionError("initially available");
    try(var lease=guard.lock(a,b)){
      if(guard.canMutate(a)||guard.canMutate(b))throw new AssertionError("locked inventories must deny external mutations");
      if(guard.tryLock(a,b)!=null)throw new AssertionError("cannot acquire twice");
      if(guard.tryLock(a,UUID.randomUUID())!=null)throw new AssertionError("overlapping lock");
      if(!lease.owns(a)||!lease.owns(b))throw new AssertionError("ownership");
    }
    if(!guard.canMutate(a)||!guard.canMutate(b))throw new AssertionError("released");
    try(var lease=guard.lock(a,b)){
      try { lease.close(); lease.close(); } catch(Exception ex){throw new AssertionError("idempotent close",ex);}
    }
    if(guard.tryLock(a,a)!=null)throw new AssertionError("duplicate account lock rejected");
    System.out.println("PASS mutation guard");
  }
}
