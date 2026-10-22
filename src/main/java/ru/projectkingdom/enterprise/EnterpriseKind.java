package ru.projectkingdom.enterprise;

import java.util.Arrays;
import java.util.List;

/** Private enterprise catalog. Administrative buildings deliberately do not belong here. */
public enum EnterpriseKind {
    FARM("farm", "Ферма", "Ферма", true),
    MINE("mine", "Шахта", "Шахта", true),
    LUMBER("lumber", "Лесозаготовительное предприятие", "Лесное дело", true),
    SMITHY("smithy", "Кузница", "Кузница", true),
    BAKERY("bakery", "Пекарня", "Пекарня", true),
    POTTERY("pottery", "Гончарная мастерская", "Гончарная", true),
    CONSTRUCTION("construction", "Строительная контора", "Стройконтора", true),
    COURIER("courier", "Курьерская служба", "Курьерская служба", true),
    LEGACY("legacy", "Старое предприятие", "Предприятие", false);

    private final String id;
    private final String title;
    private final String defaultPrefix;
    private final boolean placeable;

    EnterpriseKind(String id, String title, String defaultPrefix, boolean placeable) {
        this.id=id; this.title=title; this.defaultPrefix=defaultPrefix; this.placeable=placeable;
    }

    public String id(){return id;}
    public String title(){return title;}
    public String defaultPrefix(){return defaultPrefix;}
    public boolean placeable(){return placeable;}
    public String blockId(){return id+"_core";}
    public String translationKey(){return "block.kingdomnpc."+blockId();}

    public static List<EnterpriseKind> placeableKinds(){
        return Arrays.stream(values()).filter(EnterpriseKind::placeable).toList();
    }

    public static EnterpriseKind byId(String id){
        if(id==null)return LEGACY;
        return Arrays.stream(values()).filter(kind->kind.id.equals(id)).findFirst().orElse(LEGACY);
    }
}
