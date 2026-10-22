import java.util.*;
import ru.projectkingdom.enterprise.*;
import ru.projectkingdom.npc.KingdomEnterpriseData;

public final class EnterpriseLifecycleContractTest {
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        check(EnterpriseKind.placeableKinds().size()==8,"eight types");
        check(EnterpriseKind.placeableKinds().stream().noneMatch(k->k.id().equals("warehouse")||k.id().equals("market")),"administrative exclusion");
        var data=new KingdomEnterpriseData();UUID owner=UUID.randomUUID();
        var firm=data.create(owner,"Ферма",EnterpriseKind.FARM);
        check(data.activateCore(owner,firm.id(),EnterpriseKind.FARM,"minecraft:overworld",42)==EnterpriseActivation.ACTIVATED,"activate");
        check(data.activateCore(owner,firm.id(),EnterpriseKind.FARM,"minecraft:overworld",43)==EnterpriseActivation.ALREADY_ACTIVE,"duplicate blocked");
        check(!data.suspendCore(owner,firm.id(),"minecraft:overworld",43),"stale position blocked");
        check(data.suspendCore(owner,firm.id(),"minecraft:overworld",42),"exact suspend");
        System.out.println("PASS typed enterprise lifecycle contract");
    }
}
