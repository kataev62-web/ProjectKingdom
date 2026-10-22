import ru.projectkingdom.npc.NpcSimulation;
import ru.projectkingdom.npc.NpcEconomy;
public final class NpcSimulationTest {
 public static void main(String[] a) {
  require(NpcSimulation.production("farmer",2,true)==8);
  require(NpcSimulation.production("farmer",2,false)==0);
  require(NpcSimulation.production("guard",2,true)==0);
  require(NpcSimulation.elapsed(100,20)==30);
  require(NpcSimulation.elapsed(20,100)==0);
  require(NpcSimulation.normalizeOrder("FOLLOW").equals("follow"));
  require(NpcEconomy.payableDays(10,2,8)==5);
  boolean thrown=false;try{NpcSimulation.normalizeOrder("destroy");}catch(IllegalArgumentException e){thrown=true;}require(thrown);
  System.out.println("8 simulation/economy checks passed");
 }
 static void require(boolean yes){if(!yes)throw new AssertionError();}
}
