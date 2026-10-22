import ru.projectkingdom.npc.NpcAutonomyPlanner;
import ru.projectkingdom.npc.NpcAutonomyPlanner.*;
public final class NpcAutonomyPlannerTest {
  static void eq(Intention want,Snapshot s) {if (NpcAutonomyPlanner.plan(s)!=want) throw new AssertionError(want+" != "+NpcAutonomyPlanner.plan(s));}
  public static void main(String[] args) {
    eq(Intention.SEEK_SHELTER,new Snapshot(false,false,90,90,10,false,false,false,false,false,false));
    eq(Intention.EAT,new Snapshot(false,false,10,90,90,true,false,false,false,false,false));
    eq(Intention.REST,new Snapshot(false,false,90,10,90,true,false,false,false,false,false));
    eq(Intention.SEEK_SHELTER,new Snapshot(false,false,90,90,90,false,false,false,false,false,false));
    eq(Intention.FIND_WORK,new Snapshot(false,false,90,90,90,true,false,false,false,false,false));
    eq(Intention.REPORT_BLOCKED,new Snapshot(true,false,90,90,90,true,true,true,true,false,true));
    eq(Intention.REPORT_BLOCKED,new Snapshot(true,false,90,90,90,true,true,true,true,true,false));
    eq(Intention.REPORT_BLOCKED,new Snapshot(true,true,90,90,90,true,true,true,true,true,true));
    eq(Intention.CONTINUE_ASSIGNED_JOB,new Snapshot(true,false,90,90,90,true,true,true,true,true,true));
    System.out.println("9 tests OK: NPC autonomy policy");
  }
}
