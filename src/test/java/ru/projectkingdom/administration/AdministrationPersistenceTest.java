package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class AdministrationPersistenceTest {
    @Test void founderRolesCoresAndLogsRoundTrip() {
        UUID town=UUID.randomUUID(), founder=UUID.randomUUID(), mayor=UUID.randomUUID(), officer=UUID.randomUUID();
        var data=new KingdomAdministrationData();
        assertTrue(data.ensureFounder(town,founder));
        assertTrue(data.appoint(town,founder,mayor,officer,MunicipalRole.TREASURER));
        var binding=new AdministrativeCoreBinding(town,AdministrativeKind.BANK,"minecraft:overworld",123L);
        assertTrue(data.activateCore(officer,mayor,binding));
        data.record(town,officer,MunicipalRole.TREASURER,"bank.activate",10L);
        var loaded=KingdomAdministrationData.fromTag(data.save(new CompoundTag()));
        assertEquals(founder,loaded.founder(town));
        assertEquals(Set.of(MunicipalRole.TREASURER),loaded.roles(town,officer));
        assertEquals(binding,loaded.core(town,AdministrativeKind.BANK));
        assertEquals(1,loaded.entries(town,10).size());
    }

    @Test void duplicateCoreIsRejectedAndSuspensionKeepsTownData() {
        UUID town=UUID.randomUUID(), founder=UUID.randomUUID();
        var data=new KingdomAdministrationData();data.ensureFounder(town,founder);
        var first=new AdministrativeCoreBinding(town,AdministrativeKind.TOWN_HALL,"minecraft:overworld",1L);
        var second=new AdministrativeCoreBinding(town,AdministrativeKind.TOWN_HALL,"minecraft:overworld",2L);
        assertTrue(data.activateCore(founder,founder,first));
        assertFalse(data.activateCore(founder,founder,second));
        assertFalse(data.suspendCore(founder,founder,second));
        assertTrue(data.suspendCore(founder,founder,first));
        assertNull(data.core(town,AdministrativeKind.TOWN_HALL));
        assertEquals(founder,data.founder(town));
    }

    @Test void migrationAndLogLimitAreIdempotent() {
        UUID town=UUID.randomUUID(), mayor=UUID.randomUUID();
        var data=new KingdomAdministrationData();
        assertEquals(1,data.migrateFounder(town,mayor));
        assertEquals(0,data.migrateFounder(town,UUID.randomUUID()));
        for(int i=0;i<520;i++)data.record(town,mayor,null,"event."+i,i);
        assertEquals(500,data.entries(town,1000).size());
        var loaded=KingdomAdministrationData.fromTag(data.save(new CompoundTag()));
        assertEquals(mayor,loaded.founder(town));
        assertEquals(500,loaded.entries(town,1000).size());
        assertEquals(KingdomAdministrationData.DATA_VERSION,loaded.dataVersion());
    }
}
