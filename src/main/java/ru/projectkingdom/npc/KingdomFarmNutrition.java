package ru.projectkingdom.npc;

import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

/**
 * Uses registered TFC fertilizer data and real IFarmland nutrients.
 * No world editing, fabricated compost, or direct crop growth manipulation.
 */
public final class KingdomFarmNutrition {
    private KingdomFarmNutrition(){}
    private static boolean loaded,compatible;
    private static Class<?> fertilizerType,nutrientType;
    private static Method findFertilizer,getNutrientOfFertilizer;

    private static boolean init() {
        if(loaded)return compatible;
        loaded=true;
        if(!ModList.get().isLoaded("tfc"))return false;
        try {
            fertilizerType=Class.forName("net.dries007.tfc.util.Fertilizer");
            nutrientType=Class.forName("net.dries007.tfc.common.blockentities.FarmlandBlockEntity$NutrientType");
            findFertilizer=fertilizerType.getMethod("get",ItemStack.class);
            getNutrientOfFertilizer=fertilizerType.getMethod("getNutrient",nutrientType);
            compatible=true;
        }catch(ReflectiveOperationException | LinkageError ex){
            System.err.println("[Kingdom] TFC fertilizers unavailable: "+ex);
        }
        return compatible;
    }
    private static Object fertilizer(ItemStack stack) {
        if(!init()||stack.isEmpty())return null;
        try {return findFertilizer.invoke(null,stack);}
        catch(ReflectiveOperationException|RuntimeException ex){return null;}
    }
    public static boolean recognized(ItemStack stack){return fertilizer(stack)!=null;}

    /** Add ONE TFC fertilizer only when the primary nutrient is below 35%. */
    public static boolean apply(ServerLevel level,BlockPos cropPos,BlockState crop,
                                ItemStack fertilizerStack) {
        if(!init()||fertilizerStack.isEmpty()||!level.hasChunkAt(cropPos.below()))return false;
        Object fert=fertilizer(fertilizerStack);
        if(fert==null)return false;
        Object farmland=level.getBlockEntity(cropPos.below());
        if(farmland==null)return false;
        try{
            Object primary=crop.getBlock().getClass().getMethod("getPrimaryNutrient").invoke(crop.getBlock());
            if(!nutrientType.isInstance(primary))return false;
            Method read=farmland.getClass().getMethod("getNutrient",nutrientType);
            float value=((Number)read.invoke(farmland,primary)).floatValue();
            float change=((Number)getNutrientOfFertilizer.invoke(fert,primary)).floatValue();
            if(value>=0.35f||change<=0)return false;
            // TFC's IFarmland implementation clamps and synchronizes real N/P/K.
            farmland.getClass().getMethod("addNutrients",fertilizerType,float.class)
                .invoke(farmland,fert,1f);
            fertilizerStack.shrink(1);
            return true;
        }catch(ReflectiveOperationException|RuntimeException ex){
            return false;
        }
    }

    /** Withdraw a real suitable fertilizer into the worker's existing saved inventory. */
    public static boolean withdraw(ServerLevel level,BlockPos cropPos,BlockState crop,
                                   Container raw,KingdomNpcData.Worker worker) {
        if(!init()||!level.hasChunkAt(cropPos.below()))return false;
        Object farmland=level.getBlockEntity(cropPos.below());if(farmland==null)return false;
        try{
            Object primary=crop.getBlock().getClass().getMethod("getPrimaryNutrient").invoke(crop.getBlock());
            if(!nutrientType.isInstance(primary))return false;
            float n=((Number)farmland.getClass().getMethod("getNutrient",nutrientType)
                .invoke(farmland,primary)).floatValue();
            if(n>=0.35f)return false;
            for(int i=0;i<raw.getContainerSize();i++) {
                ItemStack source=raw.getItem(i);
                Object fert=fertilizer(source);
                if(fert==null||source.isEmpty()||!raw.canTakeItem(raw,i,source))continue;
                float gain=((Number)getNutrientOfFertilizer.invoke(fert,primary)).floatValue();
                if(gain<=0)continue;
                ItemStack taken=raw.removeItem(i,1);
                if(taken.isEmpty())return false;
                raw.setChanged();NpcFieldWork.put(worker,taken);
                KingdomNpcData.get(level.getServer()).setDirty();return true;
            }
        }catch(ReflectiveOperationException|RuntimeException ex){
            return false;
        }
        return false;
    }
}
