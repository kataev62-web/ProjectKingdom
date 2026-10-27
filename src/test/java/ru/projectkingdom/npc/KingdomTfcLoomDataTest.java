package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import ru.projectkingdom.enterprise.EnterpriseKind;

class KingdomTfcLoomDataTest {
    @Test void realWeaverHasWorkshopDutyAndEnterpriseAuthorization(){
        assertEquals(KingdomNpcDuty.Kind.WORKSHOP,KingdomNpcDuty.kind("weaver"));
        assertTrue(KingdomNpcDuty.hasPhysicalExecutor("weaver"));
        assertTrue(KingdomProfessionWorkRules.loom("weaver",EnterpriseKind.TEXTILE));
        assertFalse(KingdomProfessionWorkRules.loom("weaver",EnterpriseKind.FARM));
        assertFalse(KingdomProfessionWorkRules.loom("farmer",EnterpriseKind.TEXTILE));
        assertFalse(KingdomNpcDuty.mayExecute("weaver","stay",
            "CONTINUE_ASSIGNED_JOB",true));
    }
    @Test void durableLoomClaimsBlockDoubleOwnershipAfterRestart(){
        var data=new KingdomTfcLoomData();
        UUID worker=UUID.randomUUID(),other=UUID.randomUUID();
        var job=new KingdomTfcLoomData.Job(worker,"minecraft:overworld",567L,
            "tfc:wool_yarn","tfc:wool_cloth",4);
        assertTrue(data.claim(job));
        assertTrue(data.claimedByOther(other,"minecraft:overworld",567L));
        assertFalse(data.claimedByOther(worker,"minecraft:overworld",567L));
        var restored=KingdomTfcLoomData.load(data.save(new CompoundTag()));
        assertEquals(job,restored.job(worker));
        assertTrue(restored.claimedByOther(other,"minecraft:overworld",567L));
        restored.release(worker);
        assertNull(restored.job(worker));
    }
    @Test void corruptClaimCannotCreateFakeLoomRights(){
        assertThrows(IllegalArgumentException.class,()->
            new KingdomTfcLoomData.Job(UUID.randomUUID(),"",9L,"yarn","cloth",2));
        assertThrows(IllegalArgumentException.class,()->
            new KingdomTfcLoomData.Job(UUID.randomUUID(),"minecraft:overworld",9L,"","cloth",2));
        assertThrows(IllegalArgumentException.class,()->
            new KingdomTfcLoomData.Job(UUID.randomUUID(),"minecraft:overworld",9L,"yarn","cloth",65));
    }
}
