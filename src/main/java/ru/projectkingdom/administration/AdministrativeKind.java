package ru.projectkingdom.administration;

import java.util.Arrays;
import java.util.Optional;

public enum AdministrativeKind {
    TOWN_HALL("town_hall","Ратуша"),
    TREASURY("treasury","Казначейство"),
    BANK("bank","Банк"),
    CENTRAL_WAREHOUSE("central_warehouse","Центральный склад"),
    CITY_MARKET("city_market","Городской рынок"),
    LAND_OFFICE("land_office","Земельное управление"),
    COURT("court","Суд"),
    PRISON("prison","Тюрьма"),
    GUARD_ADMINISTRATION("guard_administration","Управление стражи");

    private final String id;
    private final String russianName;
    AdministrativeKind(String id,String russianName){this.id=id;this.russianName=russianName;}
    public String id(){return id;}
    public String blockId(){return id+"_core";}
    public String russianName(){return russianName;}
    public static Optional<AdministrativeKind> byId(String id){
        return Arrays.stream(values()).filter(value->value.id.equals(id)).findFirst();
    }
}
