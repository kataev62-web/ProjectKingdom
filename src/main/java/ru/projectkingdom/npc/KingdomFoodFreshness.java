package ru.projectkingdom.npc;

import java.lang.reflect.Method;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * Real TFC food expiry metadata. Never rewrites creation dates or preserves
 * spoiled ingredients by inventing a new expiry. Read-only reflection keeps
 * this Forge mod compatible with a single JAR.
 */
public final class KingdomFoodFreshness {
    private KingdomFoodFreshness(){}
    private static boolean initialized,compatible;
    private static Method getFood,rotAt,calendarTick;
    private static Object calendar;

    private static boolean init(){
        if(initialized)return compatible;
        initialized=true;
        if(!ModList.get().isLoaded("tfc"))return false;
        try {
            Class<?> food=Class.forName("net.dries007.tfc.common.capabilities.food.FoodCapability");
            Class<?> detail=Class.forName("net.dries007.tfc.common.capabilities.food.IFood");
            Class<?> calendars=Class.forName("net.dries007.tfc.util.calendar.Calendars");
            Class<?> clock=Class.forName("net.dries007.tfc.util.calendar.ICalendar");
            getFood=food.getMethod("get",ItemStack.class);
            rotAt=detail.getMethod("getRottenDate");
            calendarTick=clock.getMethod("getTicks");
            calendar=calendars.getField("SERVER").get(null);
            compatible=true;
        }catch(ReflectiveOperationException|LinkageError ex){
            System.err.println("[Kingdom] TFC expiry dates unavailable: "+ex);
        }
        return compatible;
    }
    /** Long.MAX_VALUE means non-food, never-expiring, or unavailable metadata. */
    public static long expiry(ItemStack stack){
        if(stack==null||stack.isEmpty()||!init())return Long.MAX_VALUE;
        try {
            Object current=getFood.invoke(null,stack);
            return current==null?Long.MAX_VALUE:((Number)rotAt.invoke(current)).longValue();
        }catch(ReflectiveOperationException|RuntimeException ex){return Long.MAX_VALUE;}
    }
    public static long now(){
        if(!init())return 0;
        try{return ((Number)calendarTick.invoke(calendar)).longValue();}
        catch(ReflectiveOperationException|RuntimeException ex){return 0;}
    }

    /**
     * Short-lived food receives a bounded bonus, never enough to force
     * an unsuitable meal when a better nutritional group is deficient.
     * TFC time is ~24000 ticks per calendar day; no world/gameTime assumptions.
     */
    public static int urgency(long expiry,long now){
        if(expiry==Long.MAX_VALUE || expiry<=now)return 0;
        long remaining=expiry-now;
        if(remaining<=24000L)return 24;
        if(remaining<=72000L)return 16;
        if(remaining<=168000L)return 8;
        return 0;
    }
    public static int mealScore(KingdomNpcData.Worker w,
                                KingdomNpcNutrition.Meal meal,long expiry,long now){
        if(w==null||meal==null)return Integer.MIN_VALUE;
        int value=meal.hunger()*2;
        int[] nutrients=meal.nutrients();
        for(int i=0;i<KingdomNpcNutrition.COUNT;i++)
            value+=nutrients[i]*Math.max(0,100-KingdomNpcNutrition.nutrient(w,i))/40;
        return value+urgency(expiry,now);
    }
    /** FEFO tie-breaker for equal nutritional priority; never expire => last. */
    public static boolean prefers(long nextExpiry,long currentExpiry){
        return nextExpiry<currentExpiry;
    }
}
