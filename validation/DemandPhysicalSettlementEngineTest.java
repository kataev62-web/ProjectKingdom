import java.util.*;
import ru.projectkingdom.npc.DemandPhysicalSettlementEngine;
import ru.projectkingdom.npc.DemandPhysicalSettlementEngine.*;
import static ru.projectkingdom.npc.DemandPhysicalSettlementEngine.*;
public class DemandPhysicalSettlementEngineTest {
    static void check(boolean b) { if (!b) throw new AssertionError(); }
    static Inventory inv(int n) { return new Inventory(n,64); }
    public static void main(String[] args) {
        Inventory supplier=inv(2), warehouse=inv(2), cashbox=inv(3), wallet=inv(3);
        supplier.set(0,"tfc:ore",30); cashbox.set(0,COPPER,50); cashbox.set(1,SILVER,1);
        Set<UUID> done=new HashSet<>(); UUID id=UUID.randomUUID();
        Outcome first=DemandPhysicalSettlementEngine.settle(id,"tfc:ore",20,5,supplier,warehouse,cashbox,wallet,done);
        check(first.result()==Result.COMMITTED);
        check(supplier.count("tfc:ore")==10 && warehouse.count("tfc:ore")==20);
        check(cashbox.count(SILVER)==0 && wallet.count(SILVER)==1);
        check(DemandPhysicalSettlementEngine.settle(id,"tfc:ore",20,5,supplier,warehouse,cashbox,wallet,done).result()==Result.DUPLICATE);
        check(DemandPhysicalSettlementEngine.settle(UUID.randomUUID(),"tfc:ore",20,5,supplier,warehouse,cashbox,wallet,done).result()==Result.INSUFFICIENT_GOODS);
        Inventory c2=inv(1); c2.set(0,SILVER,1);
        Inventory v2=inv(1); v2.set(0,GOLD,64);
        var s2=inv(1); s2.set(0,"tfc:ore",20);
        var w2=inv(1);
        check(DemandPhysicalSettlementEngine.settle(UUID.randomUUID(),"tfc:ore",20,5,s2,w2,c2,v2,new HashSet<>()).result()==Result.NO_COIN_SPACE);
        check(s2.count("tfc:ore")==20 && w2.count("tfc:ore")==0 && c2.count(SILVER)==1);
        Inventory c3=inv(1); c3.set(0,GOLD,1);
        check(DemandPhysicalSettlementEngine.settle(UUID.randomUUID(),"tfc:ore",20,5,s2,w2,c3,inv(2),new HashSet<>()).result()==Result.INSUFFICIENT_COINS);
        check(DemandPhysicalSettlementEngine.settle(UUID.randomUUID(),"tfc:ore",20,Long.MAX_VALUE,s2,w2,c3,inv(2),new HashSet<>()).result()==Result.INVALID);
        System.out.println("PASS physical denomination settlement: exact coins, conservation, capacity, duplicate, overflow");
    }
}
