package ru.projectkingdom.administration;

import java.util.ArrayList;
import java.util.List;

public final class AdministrativeMenuLayout {
    public static final int WIDTH=340,HEIGHT=220;
    public enum Tab {INFO,CITIZENS,OFFICES,SOCIAL,CIVIC,FINANCE,STORAGE,TRADE,LAND,CASES,CELLS,GUARDS,LOG,SETTINGS}
    public record Rect(int x,int y,int width,int height){public int right(){return x+width;}public int bottom(){return y+height;}}
    private AdministrativeMenuLayout(){}
    public static List<Tab> tabs(AdministrativeKind kind){
        return switch(kind){
            case TOWN_HALL->List.of(Tab.INFO,Tab.CITIZENS,Tab.OFFICES,Tab.SOCIAL,Tab.CIVIC,Tab.LOG,Tab.SETTINGS);
            case TREASURY->List.of(Tab.INFO,Tab.FINANCE,Tab.LOG,Tab.SETTINGS);
            case BANK->List.of(Tab.INFO,Tab.FINANCE,Tab.LOG);
            case CENTRAL_WAREHOUSE->List.of(Tab.INFO,Tab.STORAGE,Tab.LOG,Tab.SETTINGS);
            case CITY_MARKET->List.of(Tab.INFO,Tab.TRADE,Tab.FINANCE,Tab.LOG,Tab.SETTINGS);
            case LAND_OFFICE->List.of(Tab.INFO,Tab.LAND,Tab.LOG,Tab.SETTINGS);
            case COURT->List.of(Tab.INFO,Tab.CASES,Tab.LOG,Tab.SETTINGS);
            case PRISON->List.of(Tab.INFO,Tab.CELLS,Tab.LOG,Tab.SETTINGS);
            case GUARD_ADMINISTRATION->List.of(Tab.INFO,Tab.GUARDS,Tab.CASES,Tab.LOG,Tab.SETTINGS);
        };
    }
    public static List<Rect> tabRects(AdministrativeKind kind){
        ArrayList<Rect> result=new ArrayList<>();for(int i=0;i<tabs(kind).size();i++)result.add(new Rect(4,16+i*29,27,24));return List.copyOf(result);
    }
    public static Rect content(){return new Rect(42,12,288,198);}
}
