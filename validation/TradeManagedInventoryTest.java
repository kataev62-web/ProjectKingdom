package ru.projectkingdom.npc;
import java.util.UUID;
public class TradeManagedInventoryTest {
 static void ok(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  var locks=new TradeInventoryMutationGuard(); var registry=new TradeProtectedLocations(locks);
  var gate=new TradeManagedInventory(locks,registry);
  var a=UUID.randomUUID();var b=UUID.randomUUID();
  var pos=new TradeProtectedLocations.Location("overworld",42L);
  ok(gate.register(pos,a),"register");
  ok(!gate.register(pos,b),"ownership conflict");
  ok(gate.mutate(pos,a,()->{}),"owner mutation");
  ok(!gate.mutate(pos,b,()->{}),"foreign mutation");
  ok(!gate.mutate(new TradeProtectedLocations.Location("overworld",43),a,()->{}),"unknown fail closed");
  try(var lease=locks.lock(a,b)){
   ok(!gate.mutate(pos,a,()->{}),"locked mutation denied");
   ok(gate.project(pos,a,lease,()->{}),"authorized projection");
   ok(!gate.project(pos,b,lease,()->{}),"wrong account denied");
  }
  ok(gate.mutate(pos,a,()->{}),"unlocked mutation");
  final int[] n={0};
  try{gate.mutate(pos,a,()->{n[0]++;throw new IllegalStateException("fault");});throw new AssertionError("exception swallowed");}
  catch(IllegalStateException expected){}
  ok(n[0]==1,"mutation exactly once");
  System.out.println("PASS TradeManagedInventoryTest");
 }
}
