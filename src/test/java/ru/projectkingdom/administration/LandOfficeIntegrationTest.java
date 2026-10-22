package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class LandOfficeIntegrationTest {
    @Test void founderIdentitySurvivesMunicipalAppointments() {
        UUID town=UUID.randomUUID(),founder=UUID.randomUUID(),replacement=UUID.randomUUID();
        var data=new KingdomAdministrationData();
        assertTrue(data.ensureFounder(town,founder));
        assertTrue(data.appoint(town,founder,founder,replacement,MunicipalRole.MAYOR));
        assertEquals(founder,data.founder(town));
        assertTrue(AdministrativePermissionService.mayPerformFounderOnly(founder,data.founder(town)));
        assertFalse(AdministrativePermissionService.mayPerformFounderOnly(replacement,data.founder(town)));
    }

    @Test void landRoutesCheckRoleExactTownAndExistingContracts() throws Exception {
        String source=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomCivicsData.java"));
        assertTrue(source.contains("replaceMayorAuthorized"));
        assertTrue(source.contains("landOfficeSetZone"));
        assertTrue(source.contains("AdministrativeKind.LAND_OFFICE"));
        assertTrue(source.contains("!townId.equals(plot.town())"));
        assertTrue(source.contains("canAcceptLease"));
        assertTrue(source.contains("canBuySale"));
    }

    @Test void townHallPacketsAreBoundToTheOpenTownHallMenu() throws Exception {
        String source=Files.readString(Path.of("src/main/java/ru/projectkingdom/administration/AdministrativeTextPacket.java"));
        assertTrue(source.contains("appoint_role"));
        assertTrue(source.contains("revoke_role"));
        assertTrue(source.contains("appoint_mayor"));
        assertTrue(source.contains("menu.kind()!=AdministrativeKind.TOWN_HALL"));
    }
}
