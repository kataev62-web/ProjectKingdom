package ru.projectkingdom.npc;

import java.lang.reflect.Method;
import java.util.Locale;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * NPC's own dietary history, informed by actual TFC FoodCapability on ItemStacks.
 * The five values are independent of TFC soil nutrients (N/P/K).
 * Does NOT modify any player's TFC food capability.
 */
public final class KingdomNpcNutrition {
    private KingdomNpcNutrition() {}
    public static final int GRAIN=0, FRUIT=1, VEGETABLES=2, PROTEIN=3, DAIRY=4, COUNT=5;
    public static final String[] LABELS={"зерновые","фрукты","овощи","белки","молочные"};
    public record Meal(int hunger,int[] nutrients) {
        public Meal { nutrients=nutrients.clone(); }
        @Override public int[] nutrients(){return nutrients.clone();}
        public boolean nutritious(){for(int value:nutrients)if(value>0)return true;return false;}
    }

    private static boolean initialized, supported;
    private static Method foodOf, foodData, isRotten, getHunger;
    private static final Method[] nutrients=new Method[COUNT];
    private static boolean init(){
        if(initialized)return supported;
        initialized=true;
        if(ModList.get()==null||!ModList.get().isLoaded("tfc"))return false;
        try{
            Class<?> capability=Class.forName("net.dries007.tfc.common.capabilities.food.FoodCapability");
            Class<?> food=Class.forName("net.dries007.tfc.common.capabilities.food.IFood");
            Class<?> data=Class.forName("net.dries007.tfc.common.capabilities.food.FoodData");
            foodOf=capability.getMethod("get",ItemStack.class);
            foodData=food.getMethod("getData");
            isRotten=food.getMethod("isRotten");
            getHunger=data.getMethod("hunger");
            String[] names={"grain","fruit","vegetables","protein","dairy"};
            for(int i=0;i<COUNT;i++)nutrients[i]=data.getMethod(names[i]);
            supported=true;
        }catch(ReflectiveOperationException|LinkageError ex){
            System.err.println("[Kingdom] TFC nutrition unavailable: "+ex);
        }
        return supported;
    }

    /** True only when actual TFC food capability methods have been resolved. */
    public static boolean available(){return init();}

    /** Original TFC expiration state; spoiled produce is NOT a viable food reserve. */
    public static boolean spoiled(ItemStack stack) {
        if(stack==null||stack.isEmpty()||!init())return false;
        try {
            Object food=foodOf.invoke(null,stack);
            return food!=null && Boolean.TRUE.equals(isRotten.invoke(food));
        }catch(ReflectiveOperationException|RuntimeException ex){return false;}
    }

    /** Returns null for rotten food, tools, false food, or an unsupported TFC API. */
    public static Meal inspect(ItemStack stack){
        if(stack==null||stack.isEmpty()||!init())return null;
        try{
            Object food=foodOf.invoke(null,stack);
            if(food==null||Boolean.TRUE.equals(isRotten.invoke(food)))return null;
            Object stats=foodData.invoke(food);
            int hunger=Math.max(0,Math.min(20,((Number)getHunger.invoke(stats)).intValue()));
            int[] values=new int[COUNT];boolean any=false;
            for(int i=0;i<COUNT;i++){
                float amount=((Number)nutrients[i].invoke(stats)).floatValue();
                values[i]=(int)Math.max(0,Math.min(40,Math.round(amount*12)));
                any|=values[i]>0;
            }
            // No nutritional value and no hunger: TFC dough or compost, not a meal.
            return hunger>0||any?new Meal(hunger,values):null;
        }catch(ReflectiveOperationException|RuntimeException ex){
            return null;
        }
    }

    public static int quality(KingdomNpcData.Worker w){
        if(w==null)return 0;
        return (w.dietGrain+w.dietFruit+w.dietVegetables+w.dietProtein+w.dietDairy)/COUNT;
    }
    public static int nutrient(KingdomNpcData.Worker w,int group){
        return switch(group){
            case GRAIN -> w.dietGrain;
            case FRUIT -> w.dietFruit;
            case VEGETABLES -> w.dietVegetables;
            case PROTEIN -> w.dietProtein;
            case DAIRY -> w.dietDairy;
            default -> 0;
        };
    }
    private static int clamp(int n){return Math.max(0,Math.min(100,n));}
    public static void eat(KingdomNpcData.Worker w,Meal meal){
        int[] values=meal.nutrients();
        w.hunger=NpcLife.clamp(w.hunger+meal.hunger()*5);
        w.dietGrain=clamp(w.dietGrain+values[GRAIN]);
        w.dietFruit=clamp(w.dietFruit+values[FRUIT]);
        w.dietVegetables=clamp(w.dietVegetables+values[VEGETABLES]);
        w.dietProtein=clamp(w.dietProtein+values[PROTEIN]);
        w.dietDairy=clamp(w.dietDairy+values[DAIRY]);
    }
    public static void age(KingdomNpcData.Worker w,long days) {
        int decrease=(int)Math.min(30,Math.max(0,days))*3;
        w.dietGrain=clamp(w.dietGrain-decrease);
        w.dietFruit=clamp(w.dietFruit-decrease);
        w.dietVegetables=clamp(w.dietVegetables-decrease);
        w.dietProtein=clamp(w.dietProtein-decrease);
        w.dietDairy=clamp(w.dietDairy-decrease);
        int average=quality(w);
        if(average<30){
            w.energy=clamp(w.energy-2);
            w.morale=clamp(w.morale-3);
        }else if(average>=70){
            w.energy=clamp(w.energy+1);
            w.morale=clamp(w.morale+1);
        }
    }

    /** Poor diet reduces the number of physical work actions; never increases item yields. */
    public static boolean permittedWorkTick(KingdomNpcData.Worker w,long tick) {
        int score=quality(w);
        return score>=65 || (score>=35 ? tick%80==0 : tick%120==0);
    }
    public static String summary(KingdomNpcData.Worker w){
        return String.format(Locale.ROOT,
            "Питание %d%% | зерно %d%%, фрукты %d%%, овощи %d%%, белки %d%%, молочное %d%%",
            quality(w),w.dietGrain,w.dietFruit,w.dietVegetables,w.dietProtein,w.dietDairy);
    }
}
