package ru.projectkingdom.npc;

import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Real TFC soup/jam portion extraction from a claimed native cooking pot.
 * Uses the native PotRecipe.Output stack, native soup bowl food capability,
 * the registered TFC bowl/jar tags and pot's own output cleanup/sync method.
 *
 * Only invoked after Kingdom has a SAVED batch custody entry. Unclaimed or
 * player-cooked pot output remains completely untouched.
 */
public final class KingdomTfcSpecialPotWork {
    private KingdomTfcSpecialPotWork(){}
    public static final String SOUP="kingdom:tfc_pot_soup";
    public static final String JAM="kingdom:tfc_pot_jam";
    private static boolean attempted,valid;
    private static TagKey<Item> soupBowls,emptyJars;
    private static Method getFood,setBowl,potOutput,outputStack,cleanOutput;

    /** Explicit allowlist; custom add-on recipes require their own trusted adapter. */
    public static String recipeMode(Object recipe){
        if(recipe==null)return "";
        return switch(recipe.getClass().getName()){
            case "net.dries007.tfc.common.recipes.SoupPotRecipe" -> SOUP;
            case "net.dries007.tfc.common.recipes.JamPotRecipe" -> JAM;
            default -> "";
        };
    }
    private static boolean init(){
        if(attempted)return valid;
        attempted=true;
        try {
            Class<?> tags=Class.forName("net.dries007.tfc.common.TFCTags$Items");
            @SuppressWarnings("unchecked")
            TagKey<Item> bowls=(TagKey<Item>)tags.getField("SOUP_BOWLS").get(null);
            @SuppressWarnings("unchecked")
            TagKey<Item> jars=(TagKey<Item>)tags.getField("EMPTY_JAR_WITH_LID").get(null);
            soupBowls=bowls;emptyJars=jars;
            Class<?> food=Class.forName("net.dries007.tfc.common.capabilities.food.FoodCapability");
            Class<?> dynamic=Class.forName("net.dries007.tfc.common.capabilities.food.DynamicBowlHandler");
            Class<?> pot=Class.forName("net.dries007.tfc.common.blockentities.PotBlockEntity");
            getFood=food.getMethod("get",ItemStack.class);
            setBowl=dynamic.getMethod("setBowl",ItemStack.class);
            potOutput=pot.getMethod("getOutput");
            cleanOutput=pot.getMethod("interactWithOutput",Player.class,ItemStack.class);
            valid=true;
        }catch(ReflectiveOperationException|LinkageError ex){
            System.err.println("[Kingdom] TFC soup and jam extraction unavailable: "+ex);
        }
        return valid;
    }
    private static boolean serving(ItemStack stack,String mode){
        return !stack.isEmpty() && (SOUP.equals(mode)?stack.is(soupBowls)
             : JAM.equals(mode) && stack.is(emptyJars));
    }
    public static boolean hasServingContainer(Container raw,String mode){
        if(!init()||(!SOUP.equals(mode)&&!JAM.equals(mode)))return false;
        for(int i=0;i<raw.getContainerSize();i++){
            ItemStack item=raw.getItem(i);
            if(serving(item,mode)&&raw.canTakeItem(raw,i,item))return true;
        }
        return false;
    }
    private static ItemStack[] simulateDeposit(Container dest,ItemStack made){
        ItemStack[] contents=new ItemStack[dest.getContainerSize()];
        for(int i=0;i<contents.length;i++)contents[i]=dest.getItem(i).copy();
        ItemStack rest=made.copy();
        for(int i=0;i<contents.length&&!rest.isEmpty();i++){
            ItemStack stack=contents[i];
            if(stack.isEmpty()||!ItemStack.isSameItemSameTags(stack,rest)
                ||!dest.canPlaceItem(i,rest))continue;
            int room=Math.max(0,Math.min(stack.getMaxStackSize(),
                dest.getMaxStackSize())-stack.getCount());
            int move=Math.min(room,rest.getCount());
            if(move>0){stack.grow(move);rest.shrink(move);}
        }
        for(int i=0;i<contents.length&&!rest.isEmpty();i++){
            if(!contents[i].isEmpty()||!dest.canPlaceItem(i,rest))continue;
            int move=Math.min(rest.getCount(),Math.min(rest.getMaxStackSize(),
                dest.getMaxStackSize()));
            if(move<=0)continue;
            ItemStack placed=rest.copy();placed.setCount(move);
            contents[i]=placed;rest.shrink(move);
        }
        return rest.isEmpty()?contents:null;
    }
    private static ItemStack portion(ItemStack nativeProduct,ItemStack vessel,String mode){
        ItemStack prepared=nativeProduct.copyWithCount(1);
        if(SOUP.equals(mode)){
            try {
                Object capability=getFood.invoke(null,prepared);
                if(capability==null||!setBowl.getDeclaringClass().isInstance(capability))
                    return ItemStack.EMPTY;
                setBowl.invoke(capability,vessel.copyWithCount(1));
            }catch(ReflectiveOperationException|RuntimeException ex){
                return ItemStack.EMPTY;
            }
        }
        return prepared;
    }
    /** Extract at most ONE portion per worker tick; always consumes a real bowl/jar. */
    public static boolean collect(ServerLevel level,BlockPos station,BlockEntity pot,
                                  KingdomNpcData.Worker worker,KingdomTfcPotData.Batch batch,
                                  Container raw,Container finished){
        if(!init()||batch==null||pot==null||!level.hasChunkAt(station)
            ||!batch.dimension().equals(level.dimension().location().toString())
            ||batch.position()!=station.asLong()||batch.outputs().isEmpty())return false;
        String mode=batch.outputs().get(0);
        if(!SOUP.equals(mode)&&!JAM.equals(mode))return false;
        if(batch.outputs().stream().anyMatch(m->!mode.equals(m)))return false;
        try {
            Object nativeOutput=potOutput.invoke(pot);
            if(nativeOutput==null)return false;
            String expected=SOUP.equals(mode)
                ? "net.dries007.tfc.common.recipes.SoupPotRecipe$SoupOutput"
                : "net.dries007.tfc.common.recipes.JamPotRecipe$JamOutput";
            if(!nativeOutput.getClass().getName().equals(expected))return false;
            Method getStack=nativeOutput.getClass().getMethod("stack");
            Object value=getStack.invoke(nativeOutput);
            if(!(value instanceof ItemStack nativeStack)||nativeStack.isEmpty())return false;
            if(KingdomCurrency.value(nativeStack)>0
                ||nativeStack.is(KingdomCurrency.WALLET.get()))return false;
            for(int sourceSlot=0;sourceSlot<raw.getContainerSize();sourceSlot++){
                ItemStack supply=raw.getItem(sourceSlot);
                if(!serving(supply,mode)||!raw.canTakeItem(raw,sourceSlot,supply))continue;
                ItemStack vessel=supply.copyWithCount(1);
                ItemStack made=portion(nativeStack,vessel,mode);
                if(made.isEmpty()||KingdomNpcNutrition.spoiled(made))continue;
                ItemStack[] finalStorage=simulateDeposit(finished,made);
                if(finalStorage==null)continue;
                // All checks complete: ONE real jar/bowl is now committed.
                ItemStack used=raw.removeItem(sourceSlot,1);
                if(!ItemStack.matches(used,vessel)){
                    if(!used.isEmpty()){
                        ItemStack existing=raw.getItem(sourceSlot);
                        if(existing.isEmpty())raw.setItem(sourceSlot,used);
                        else if(ItemStack.isSameItemSameTags(existing,used)
                            &&existing.getCount()<existing.getMaxStackSize())existing.grow(1);
                        else net.minecraft.world.level.block.Block.popResource(level,station,used);
                    }
                    return false;
                }
                ItemStack physicallyMade=nativeStack.split(1);
                if(physicallyMade.isEmpty()){
                    // A concurrent mod interrupted native output; restore container.
                    ItemStack existing=raw.getItem(sourceSlot);
                    if(existing.isEmpty())raw.setItem(sourceSlot,used);
                    else if(ItemStack.isSameItemSameTags(existing,used))existing.grow(1);
                    else net.minecraft.world.level.block.Block.popResource(level,station,used);
                    return false;
                }
                for(int i=0;i<finalStorage.length;i++)
                    finished.setItem(i,finalStorage[i]);
                finished.setChanged();raw.setChanged();
                // Empty-hand native interaction triggers PotBlockEntity's saved-output
                // cleanup + sync but does NOT give a duplicate item to a player.
                cleanOutput.invoke(pot,null,ItemStack.EMPTY);
                if(nativeStack.isEmpty())KingdomTfcPotData.get(level.getServer()).remove(worker.entityId);
                worker.produced++;worker.workUnits++;worker.skillXp++;
                worker.energy=Math.max(0,worker.energy-1);
                KingdomNpcData.get(level.getServer()).setDirty();
                return true;
            }
        }catch(ReflectiveOperationException|RuntimeException ex){
            System.err.println("[Kingdom] TFC soup/jam collection stopped: "+ex);
        }
        return false;
    }
}
