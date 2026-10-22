import ru.projectkingdom.npc.NpcEconomy;
public class EconomyTest {
    static void ok(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        ok(NpcEconomy.dailyWage(3,4)==12);
        ok(NpcEconomy.payableDays(10,3,7)==3);
        ok(NpcEconomy.payableDays(0,3,7)==0);
        ok(NpcEconomy.payableDays(300,3,7)==7);
        ok("smith".equals(NpcEconomy.normalizeJob("SMITH")));
        boolean threw=false;
        try { NpcEconomy.normalizeJob("king"); } catch(IllegalArgumentException expected){threw=true;}
        ok(threw);
        System.out.println("6/6 economy checks OK");
    }
}
