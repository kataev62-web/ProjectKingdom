package ru.projectkingdom.administration;

public enum AdministrativeBindingRole {
    TREASURY_VAULT(AdministrativeKind.TREASURY,false),
    BANK_VAULT(AdministrativeKind.BANK,false),
    WAREHOUSE_STORAGE(AdministrativeKind.CENTRAL_WAREHOUSE,true),
    MARKET_STORAGE(AdministrativeKind.CITY_MARKET,true),
    PRISON_CELL(AdministrativeKind.PRISON,true);
    private final AdministrativeKind kind;private final boolean multiple;
    AdministrativeBindingRole(AdministrativeKind kind,boolean multiple){this.kind=kind;this.multiple=multiple;}
    public AdministrativeKind kind(){return kind;}public boolean multiple(){return multiple;}
}
