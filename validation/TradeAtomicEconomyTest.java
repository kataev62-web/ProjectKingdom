import ru.projectkingdom.npc.*;
import java.nio.file.*;
import java.util.*;
public class TradeAtomicEconomyTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception {
  Path p=Files.createTempDirectory("kingdom-atomic-economy");UUID world=UUID.randomUUID(),buyer=UUID.randomUUID(),seller=UUID.randomUUID(),tx=UUID.randomUUID();
  var goods=new TradeAtomicEconomy.Stack("tfc:ingot/copper",new byte[]{1,2},5,64);
  var economy=new TradeAtomicEconomy(p,world);
  economy.initialize(Map.of(buyer,new TradeAtomicEconomy.Account(buyer,100,java.util.Arrays.asList((TradeAtomicEconomy.Stack)null)),seller,new TradeAtomicEconomy.Account(seller,20,List.of(goods))));
  check(economy.purchase(tx,buyer,seller,0,3,10)==TradeAtomicEconomy.Outcome.COMMITTED,"commit");
  var restarted=new TradeAtomicEconomy(p,world);var state=restarted.read();
  check(state.accounts().get(buyer).copper()==70 && state.accounts().get(seller).copper()==50,"cash conserved");
  check(state.accounts().get(buyer).slots().get(0).count()==3 && state.accounts().get(seller).slots().get(0).count()==2,"goods conserved");
  check(restarted.purchase(tx,buyer,seller,0,3,10)==TradeAtomicEconomy.Outcome.ALREADY_COMMITTED,"idempotent restart");
  check(restarted.purchase(UUID.randomUUID(),buyer,seller,0,3,10)==TradeAtomicEconomy.Outcome.INSUFFICIENT_STOCK,"no oversell");
  check(restarted.read().generation()==2,"no generation on rejected operation");
  check(restarted.purchase(UUID.randomUUID(),buyer,seller,0,2,100)==TradeAtomicEconomy.Outcome.INSUFFICIENT_FUNDS,"no overdraft");
  check(restarted.read().accounts().get(seller).copper()==50,"no side effects");
  System.out.println("PASS TradeAtomicEconomyTest");
 }
}
