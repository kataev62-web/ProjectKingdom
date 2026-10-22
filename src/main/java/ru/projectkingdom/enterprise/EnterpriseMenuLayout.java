package ru.projectkingdom.enterprise;

import java.util.ArrayList;
import java.util.List;

public final class EnterpriseMenuLayout {
    public static final int WIDTH=320,HEIGHT=210;
    public enum Tab { INFO,WORKERS,PRODUCTION,STORAGE,CASH,ORDERS,SETTINGS }
    public record Rect(int x,int y,int width,int height){public int right(){return x+width;}public int bottom(){return y+height;}}
    private EnterpriseMenuLayout(){}
    public static List<Rect> tabs(){
        ArrayList<Rect> result=new ArrayList<>();for(int i=0;i<Tab.values().length;i++)result.add(new Rect(4,14+i*27,25,23));return List.copyOf(result);
    }
    public static Rect content(){return new Rect(38,12,272,188);}
}
