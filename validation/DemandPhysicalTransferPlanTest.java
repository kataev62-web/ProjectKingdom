import java.util.UUID;
import ru.projectkingdom.npc.*;
public class DemandPhysicalTransferPlanTest {
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    public static void main(String[] args) {
        UUID listingId=UUID.randomUUID();
        var listing=new EnterpriseDemandBoard.Listing(new EnterpriseDemandBoard.Key(UUID.randomUUID(),"tfc:ore"),listingId,100,6);
        var delivery=new EnterpriseDemandBoard.Delivery(UUID.randomUUID(),listingId,30,5);
        var s=new DemandPhysicalTransferPlan.Snapshot("tfc:ore",30,30,150,150);
        var d=DemandPhysicalTransferPlan.inspect(listing,delivery,s);
        check(d.ready() && d.copperToMove()==150 && d.goodsToMove()==30);
        check(DemandPhysicalTransferPlan.inspect(listing,delivery,new DemandPhysicalTransferPlan.Snapshot("tfc:wood",30,30,150,150)).status()==DemandPhysicalTransferPlan.Status.WRONG_ITEM);
        check(DemandPhysicalTransferPlan.inspect(listing,delivery,new DemandPhysicalTransferPlan.Snapshot("tfc:ore",29,30,150,150)).status()==DemandPhysicalTransferPlan.Status.NO_GOODS);
        check(DemandPhysicalTransferPlan.inspect(listing,delivery,new DemandPhysicalTransferPlan.Snapshot("tfc:ore",30,29,150,150)).status()==DemandPhysicalTransferPlan.Status.NO_GOODS_SPACE);
        check(DemandPhysicalTransferPlan.inspect(listing,delivery,new DemandPhysicalTransferPlan.Snapshot("tfc:ore",30,30,149,150)).status()==DemandPhysicalTransferPlan.Status.NO_COINS);
        check(DemandPhysicalTransferPlan.inspect(listing,delivery,new DemandPhysicalTransferPlan.Snapshot("tfc:ore",30,30,150,149)).status()==DemandPhysicalTransferPlan.Status.NO_COIN_SPACE);
        check(DemandPhysicalTransferPlan.inspect(listing,new EnterpriseDemandBoard.Delivery(UUID.randomUUID(),listingId,30,7),s).status()==DemandPhysicalTransferPlan.Status.INVALID);
        check(DemandPhysicalTransferPlan.inspect(listing,new EnterpriseDemandBoard.Delivery(UUID.randomUUID(),listingId,101,5),s).status()==DemandPhysicalTransferPlan.Status.INVALID);
        check(DemandPhysicalTransferPlan.inspect(listing,new EnterpriseDemandBoard.Delivery(UUID.randomUUID(),listingId,100,Long.MAX_VALUE),s).status()==DemandPhysicalTransferPlan.Status.INVALID);
        System.out.println("PASS physical transfer preflight (9 checks)");
    }
}
