package ru.projectkingdom.administration;

import java.util.Set;
import java.util.UUID;

public final class AdministrativePermissionService {
    private AdministrativePermissionService(){}
    public static boolean mayPerformFounderOnly(UUID actor,UUID founder){
        return actor!=null&&actor.equals(founder);
    }
    public static boolean mayManage(UUID actor,UUID founder,UUID mayor,Set<MunicipalRole> roles,AdministrativeKind kind){
        if(actor==null||kind==null)return false;
        if(actor.equals(founder)||actor.equals(mayor))return true;
        Set<MunicipalRole> safe=roles==null?Set.of():roles;
        return switch(kind){
            case TOWN_HALL -> safe.contains(MunicipalRole.MAYOR);
            case TREASURY, BANK -> safe.contains(MunicipalRole.TREASURER);
            case CITY_MARKET -> safe.contains(MunicipalRole.TREASURER);
            case CENTRAL_WAREHOUSE -> safe.contains(MunicipalRole.WAREHOUSE_MANAGER);
            case LAND_OFFICE -> safe.contains(MunicipalRole.LAND_MANAGER);
            case COURT -> safe.contains(MunicipalRole.JUDGE);
            case PRISON, GUARD_ADMINISTRATION -> safe.contains(MunicipalRole.GUARD_CHIEF);
        };
    }
    public static boolean mayUsePublic(AdministrativeKind kind){
        return kind==AdministrativeKind.TOWN_HALL || kind==AdministrativeKind.BANK || kind==AdministrativeKind.CITY_MARKET;
    }
}
