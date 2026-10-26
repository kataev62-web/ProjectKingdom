package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Admin status is a persisted work mode, not a new source of OP permissions. */
class KingdomServerAdminDataTest {
    @Test void toggleAndAuditSurviveSaveWithoutGrantingOtherPlayers(){
        UUID operator=UUID.randomUUID(),outsider=UUID.randomUUID();
        var data=new KingdomServerAdminData();
        assertFalse(data.enabled(operator));
        assertTrue(data.set(operator,true));
        assertTrue(data.enabled(operator));
        assertFalse(data.enabled(outsider));
        data.record(operator,"CREATE_CITY test "+UUID.randomUUID());
        var restored=KingdomServerAdminData.load(data.save(new CompoundTag()));
        assertTrue(restored.enabled(operator));
        assertFalse(restored.enabled(outsider));
        assertEquals(1,restored.audit(15).size());
        assertTrue(restored.set(operator,false));
        assertFalse(restored.enabled(operator));
    }
    @Test void auditCannotGrowWithoutBound(){
        var data=new KingdomServerAdminData();
        UUID actor=UUID.randomUUID();
        for(int i=0;i<400;i++)data.record(actor,"action "+i);
        assertEquals(50,data.audit(500).size());
        var reloaded=KingdomServerAdminData.load(data.save(new CompoundTag()));
        assertEquals(50,reloaded.audit(50).size());
        assertTrue(reloaded.audit(1).get(0).endsWith("action 399"));
    }
}
