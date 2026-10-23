package ru.projectkingdom.npc;

import java.util.*;

/** Reproducible building plans: the same plan is regenerated after every restart. */
public final class KingdomBuildingTypes {
    private KingdomBuildingTypes() {}
    public enum Type {
        TOWN_HALL("Ратуша", 13, 15, 2, "minecraft:bell"),
        HOUSE("Жилой дом", 9, 11, 1, "minecraft:crafting_table"),
        WAREHOUSE("Склад", 13, 15, 1, "minecraft:barrel"),
        SMITHY("Кузница", 11, 13, 1, "minecraft:smithing_table"),
        TAVERN("Таверна", 13, 15, 2, "minecraft:brewing_stand");
        public final String title, station;
        public final int width, length, floors;
        Type(String title,int width,int length,int floors,String station){this.title=title;this.width=width;this.length=length;this.floors=floors;this.station=station;}
    }
    public static KingdomDesignEngine.Design design(Type type) {
        var base=KingdomDesignEngine.medievalHouse(type.name(),type.width,type.length,type.floors);
        var cells=new ArrayList<>(base.blocks());
        // Functional landmark in the center, with an unobstructed doorway at the front.
        cells.add(new KingdomDesignEngine.Cell(type.width/2,1,type.length/2,type.station));
        if(type==Type.WAREHOUSE) {
            cells.add(new KingdomDesignEngine.Cell(2,1,2,"minecraft:barrel"));
            cells.add(new KingdomDesignEngine.Cell(type.width-3,1,2,"minecraft:barrel"));
        }
        // Anvil is a FallingBlock and is deliberately not placeable by the safe
        // one-item construction worker. Use a stable workstation in the shell;
        // players may install their own anvil after construction completes.
        if(type==Type.SMITHY)cells.add(new KingdomDesignEngine.Cell(2,1,type.length/2,"minecraft:stonecutter"));
        return new KingdomDesignEngine.Design(type.name(),type.width,type.length,base.height(),cells);
    }
    public static Type type(String name) {
        try { return Type.valueOf(name); } catch(IllegalArgumentException e){ return null; }
    }
}
