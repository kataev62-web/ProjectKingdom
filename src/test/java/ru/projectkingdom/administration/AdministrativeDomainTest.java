package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class AdministrativeDomainTest {
    @Test void catalogHasExactlyNineStableKinds() {
        assertEquals(9, AdministrativeKind.values().length);
        assertEquals(Set.of("town_hall", "treasury", "bank", "central_warehouse", "city_market",
                "land_office", "court", "prison", "guard_administration"),
                Set.copyOf(java.util.Arrays.stream(AdministrativeKind.values()).map(AdministrativeKind::id).toList()));
    }

    @Test void founderAndMayorHaveApprovedManagementRights() {
        UUID founder=UUID.randomUUID(), mayor=UUID.randomUUID();
        for(var kind:AdministrativeKind.values()) {
            assertTrue(AdministrativePermissionService.mayManage(founder,founder,mayor,Set.of(),kind));
            assertTrue(AdministrativePermissionService.mayManage(mayor,founder,mayor,Set.of(),kind));
        }
        assertTrue(AdministrativePermissionService.mayPerformFounderOnly(founder,founder));
        assertFalse(AdministrativePermissionService.mayPerformFounderOnly(mayor,founder));
    }

    @Test void specialistRolesManageOnlyApprovedServices() {
        UUID actor=UUID.randomUUID(), founder=UUID.randomUUID(), mayor=UUID.randomUUID();
        assertTrue(AdministrativePermissionService.mayManage(actor,founder,mayor,
                Set.of(MunicipalRole.TREASURER),AdministrativeKind.BANK));
        assertTrue(AdministrativePermissionService.mayManage(actor,founder,mayor,
                Set.of(MunicipalRole.TREASURER),AdministrativeKind.TREASURY));
        assertTrue(AdministrativePermissionService.mayManage(actor,founder,mayor,
                Set.of(MunicipalRole.TREASURER),AdministrativeKind.CITY_MARKET));
        assertFalse(AdministrativePermissionService.mayManage(actor,founder,mayor,
                Set.of(MunicipalRole.TREASURER),AdministrativeKind.COURT));
        assertTrue(AdministrativePermissionService.mayManage(actor,founder,mayor,
                Set.of(MunicipalRole.GUARD_CHIEF),AdministrativeKind.PRISON));
        assertTrue(AdministrativePermissionService.mayManage(actor,founder,mayor,
                Set.of(MunicipalRole.GUARD_CHIEF),AdministrativeKind.GUARD_ADMINISTRATION));
    }

    @Test void ordinaryCitizenCannotManageAnyService() {
        UUID actor=UUID.randomUUID();
        for(var kind:AdministrativeKind.values())
            assertFalse(AdministrativePermissionService.mayManage(actor,UUID.randomUUID(),UUID.randomUUID(),Set.of(),kind));
    }
}
