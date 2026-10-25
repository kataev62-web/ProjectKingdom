package ru.projectkingdom.npc;

import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Production planning from PHYSICAL fresh food and real TFC nutritional data.
 * Numbers here only rank opportunities; only native recipes create real products.
 */
public final class KingdomFoodBalance {
    private KingdomFoodBalance(){}

    /** A mixed meal contributes to every nutrient group it genuinely contains. */
    public static int[] freshNutritionStock(Container pantry){
        int[] stock=new int[KingdomNpcNutrition.COUNT];
        if(pantry==null)return stock;
        for(int slot=0;slot<pantry.getContainerSize();slot++){
            ItemStack item=pantry.getItem(slot);
            if(item.isEmpty()||KingdomNpcNutrition.spoiled(item))continue;
            var meal=KingdomNpcNutrition.inspect(item);
            if(meal==null)continue;
            int[] nutrients=meal.nutrients();
            for(int g=0;g<stock.length;g++)
                if(nutrients[g]>0)
                    stock[g]=(int)Math.min(Integer.MAX_VALUE,(long)stock[g]+item.getCount());
        }
        return stock;
    }
    /**
     * Missing diet groups outweigh surplus grain. Deterministic and bounded;
     * never creates food or bypasses actual TFC ingredient checks.
     */
    public static int score(int[] pantryStock,int[] foodNutrients){
        if(pantryStock.length!=KingdomNpcNutrition.COUNT
            ||foodNutrients.length!=KingdomNpcNutrition.COUNT)
            throw new IllegalArgumentException("five nutrient groups required");
        long value=0;
        for(int group=0;group<KingdomNpcNutrition.COUNT;group++){
            int deficiency=Math.max(0,48-Math.max(0,pantryStock[group]));
            value+=(long)deficiency*Math.max(0,foodNutrients[group]);
        }
        return (int)Math.min(Integer.MAX_VALUE,value);
    }
    public static int scoreOutputs(int[] pantryStock,List<ItemStack> products){
        int[] values=new int[KingdomNpcNutrition.COUNT];
        for(ItemStack item:products){
            if(item.isEmpty()||KingdomNpcNutrition.spoiled(item))continue;
            var meal=KingdomNpcNutrition.inspect(item);
            if(meal==null)continue;
            int[] nutrients=meal.nutrients();
            for(int g=0;g<values.length;g++)
                values[g]=(int)Math.min(Integer.MAX_VALUE,(long)values[g]+nutrients[g]);
        }
        return score(pantryStock,values);
    }
}
