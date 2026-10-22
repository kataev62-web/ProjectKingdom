import java.util.Set;
import java.util.UUID;
import ru.projectkingdom.administration.AdministrativeKind;
import ru.projectkingdom.administration.AdministrativePermissionService;
import ru.projectkingdom.administration.MunicipalRole;

public final class AdministrativeDomainContractTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        check(AdministrativeKind.values().length==9,"expected nine administrative kinds");
        check(Set.copyOf(java.util.Arrays.stream(AdministrativeKind.values()).map(AdministrativeKind::id).toList()).equals(
                Set.of("town_hall","treasury","bank","central_warehouse","city_market","land_office","court","prison","guard_administration")),"stable ids");
        UUID actor=UUID.randomUUID(),founder=UUID.randomUUID(),mayor=UUID.randomUUID();
        check(AdministrativePermissionService.mayManage(founder,founder,mayor,Set.of(),AdministrativeKind.COURT),"founder rights");
        check(!AdministrativePermissionService.mayPerformFounderOnly(mayor,founder),"mayor is not founder");
        check(AdministrativePermissionService.mayManage(actor,founder,mayor,Set.of(MunicipalRole.LAND_MANAGER),AdministrativeKind.LAND_OFFICE),"land manager");
        check(!AdministrativePermissionService.mayManage(actor,founder,mayor,Set.of(MunicipalRole.LAND_MANAGER),AdministrativeKind.BANK),"role isolation");
        System.out.println("PASS administrative domain contract");
    }
}
