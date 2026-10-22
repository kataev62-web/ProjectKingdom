package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class JusticeFoundationTest {
    @Test void recordsEnforceBoundedTextTimeAndImmutableIds() {
        UUID id=UUID.randomUUID(),town=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
        JusticeCase item=new JusticeCase(id,town,a,b,null,JusticeCase.Status.OPEN,"Спор о земле","",0,10);
        assertEquals(id,item.id());assertEquals(id,item.assignJudge(UUID.randomUUID()).id());
        assertThrows(IllegalArgumentException.class,()->new JusticeCase(id,town,a,b,null,JusticeCase.Status.OPEN,"x".repeat(97),"",0,10));
        assertThrows(IllegalArgumentException.class,()->new PrisonRecord(id,town,a,null,"minecraft:overworld",1,20,19,"Причина",true));
    }

    @Test void judgeAndGuardChiefWriteOnlyTheirRegistriesAndPersistenceRoundTrips() {
        UUID town=UUID.randomUUID(),founder=UUID.randomUUID(),mayor=UUID.randomUUID(),judge=UUID.randomUUID(),chief=UUID.randomUUID();
        var data=new KingdomAdministrationData();data.ensureFounder(town,founder);
        assertNotNull(data.createCase(town,judge,mayor,Set.of(MunicipalRole.JUDGE),UUID.randomUUID(),UUID.randomUUID(),"Дело",1));
        assertNull(data.createCase(town,chief,mayor,Set.of(MunicipalRole.GUARD_CHIEF),UUID.randomUUID(),UUID.randomUUID(),"Чужое дело",1));
        assertTrue(data.issueWarrant(town,chief,mayor,Set.of(MunicipalRole.GUARD_CHIEF),UUID.randomUUID(),UUID.randomUUID(),"Розыск",2));
        var loaded=KingdomAdministrationData.fromTag(data.save(new CompoundTag()));
        assertEquals(1,loaded.justiceCases(town,0,20).size());assertEquals(1,loaded.wantedEntries(town,0,20).size());
    }

    @Test void duplicateActiveWarrantDeniedAndNoAutomationExists() throws Exception {
        UUID town=UUID.randomUUID(),founder=UUID.randomUUID(),chief=UUID.randomUUID(),target=UUID.randomUUID(),caseId=UUID.randomUUID();
        var data=new KingdomAdministrationData();data.ensureFounder(town,founder);
        assertTrue(data.issueWarrant(town,chief,founder,Set.of(MunicipalRole.GUARD_CHIEF),target,caseId,"Розыск",1));
        assertFalse(data.issueWarrant(town,chief,founder,Set.of(MunicipalRole.GUARD_CHIEF),target,caseId,"Повтор",2));
        String source=Files.readString(Path.of("src/main/java/ru/projectkingdom/administration/KingdomAdministrationData.java"));
        assertFalse(source.contains("teleportTo("));assertFalse(source.contains("setDeltaMovement("));assertFalse(source.contains("hurt("));
    }
}
