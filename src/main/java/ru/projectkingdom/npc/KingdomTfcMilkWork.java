package ru.projectkingdom.npc;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.ModList;

/**
 * Real TFC milking: milk is a FLUID, never an invented milk ItemStack.
 * The worker carries an actual empty Forge/TFC fluid container, TFC's
 * AnimalProductEvent can veto/modify the yield, and native TFC cooldown
 * and use counters are changed exactly once after accepted milking.
 */
public final class KingdomTfcMilkWork {
    private KingdomTfcMilkWork(){}
    private static boolean loaded,compatible;
    private static Class<?> dairy,animalProps;
    private static Constructor<?> productCtor;
    private static Method getMilkFluid,ready,cooldown,uses,eventUses,eventFluid;
    private static boolean init(){
        if(loaded)return compatible;
        loaded=true;
        if(!ModList.get().isLoaded("tfc"))return false;
        try{
            dairy=Class.forName("net.dries007.tfc.common.entities.livestock.DairyAnimal");
            animalProps=Class.forName("net.dries007.tfc.common.entities.livestock.TFCAnimalProperties");
            var product=Class.forName("net.dries007.tfc.util.events.AnimalProductEvent");
            productCtor=product.getConstructor(net.minecraft.world.level.Level.class,BlockPos.class,
                net.minecraft.world.entity.player.Player.class,animalProps,
                FluidStack.class,ItemStack.class,int.class);
            getMilkFluid=dairy.getMethod("getMilkFluid");
            ready=dairy.getMethod("isReadyForAnimalProduct");
            cooldown=dairy.getMethod("setProductsCooldown");
            uses=animalProps.getMethod("addUses",int.class);
            eventUses=product.getMethod("getUses");
            eventFluid=product.getMethod("getFluidProduct");
            compatible=true;
        }catch(ReflectiveOperationException|LinkageError ex){
            System.err.println("[Kingdom] TFC milking adapter unavailable: "+ex);
        }
        return compatible;
    }
    private static IFluidHandlerItem handler(ItemStack stack){
        return stack.isEmpty()?null:stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
            .orElse(null);
    }
    private static FluidStack milk(Animal animal) {
        if(!init()||!dairy.isInstance(animal))return FluidStack.EMPTY;
        try{
            var fluid=(net.minecraft.world.level.material.Fluid)getMilkFluid.invoke(animal);
            return fluid==null?FluidStack.EMPTY:new FluidStack(fluid,1000);
        }catch(ReflectiveOperationException|RuntimeException ex){return FluidStack.EMPTY;}
    }
    private static boolean ready(Animal animal){
        if(!init()||!dairy.isInstance(animal))return false;
        try{return Boolean.TRUE.equals(ready.invoke(animal));}
        catch(ReflectiveOperationException|RuntimeException ex){return false;}
    }
    private static boolean emptyVessel(ItemStack stack,FluidStack milk){
        if(milk.isEmpty()||stack.getCount()!=1)return false;
        IFluidHandlerItem cap=handler(stack);
        return cap!=null
            && cap.drain(Integer.MAX_VALUE,IFluidHandler.FluidAction.SIMULATE).isEmpty()
            && cap.fill(milk,IFluidHandler.FluidAction.SIMULATE)==milk.getAmount();
    }
    /** Do not touch the raw supply chest unless the NPC is physically there. */
    private static boolean fetch(ServerLevel level,Villager npc,KingdomNpcData.Worker w,
                                 Container raw,BlockPos supply,FluidStack milk){
        if(npc.distanceToSqr(supply.getX()+.5,supply.getY()+.5,supply.getZ()+.5)>6.25){
            npc.getNavigation().moveTo(supply.getX()+.5,supply.getY(),supply.getZ()+.5,.75);
            return false;
        }
        for(int i=0;i<raw.getContainerSize();i++){
            ItemStack in=raw.getItem(i);
            if(in.isEmpty()||!raw.canTakeItem(raw,i,in))continue;
            ItemStack one=in.copyWithCount(1);
            if(!emptyVessel(one,milk)
                ||!NpcFieldWork.canFit(w,java.util.List.of(one)))continue;
            ItemStack taken=raw.removeItem(i,1);
            if(taken.isEmpty())return false;
            NpcFieldWork.put(w,taken);raw.setChanged();
            KingdomNpcData.get(level.getServer()).setDirty();return true;
        }
        return false;
    }
    public static boolean hasMilk(ItemStack stack){
        IFluidHandlerItem cap=handler(stack);
        if(cap==null)return false;
        FluidStack inside=cap.drain(Integer.MAX_VALUE,IFluidHandler.FluidAction.SIMULATE);
        // Keep existing player-owned nonmilk fluid vessels out of finished production.
        return !inside.isEmpty() && inside.getFluid()==net.minecraftforge.common.ForgeMod.MILK.get();
    }
    public static boolean tick(ServerLevel level,Villager npc,KingdomNpcData.Worker w,
                               Animal animal,Container raw,BlockPos source,Container finished){
        if(!ready(animal))return false;
        FluidStack expected=milk(animal);
        if(expected.isEmpty())return false;
        int position=-1;
        for(int i=0;i<w.fieldInventory.size();i++)
            if(emptyVessel(w.fieldInventory.get(i),expected)){position=i;break;}
        if(position<0)return fetch(level,npc,w,raw,source,expected);
        ItemStack empty=w.fieldInventory.get(position);
        IFluidHandlerItem cap=handler(empty.copy());
        if(cap==null || cap.fill(expected,IFluidHandler.FluidAction.SIMULATE)!=1000)return false;
        // Check real destination capacity BEFORE milking the animal.
        ItemStack predictedCopy=empty.copy();
        IFluidHandlerItem predicted=handler(predictedCopy);
        if(predicted==null||predicted.fill(expected,IFluidHandler.FluidAction.EXECUTE)!=1000
            ||!KingdomLivestockStorage.canInsert(finished,predicted.getContainer()))return false;
        if(npc.distanceToSqr(animal)>9){
            npc.getNavigation().moveTo(animal,.75);return false;
        }
        try{
            Event event=(Event)productCtor.newInstance(level,animal.blockPosition(),null,animal,
                expected.copy(),empty.copy(),1);
            if(MinecraftForge.EVENT_BUS.post(event))return false;
            FluidStack actual=(FluidStack)eventFluid.invoke(event);
            if(actual==null||actual.isEmpty()||actual.getAmount()!=1000
                ||actual.getFluid()!=expected.getFluid())return false;
            IFluidHandlerItem output=handler(empty.copy());
            if(output==null||output.fill(actual,IFluidHandler.FluidAction.SIMULATE)!=1000)return false;
            ItemStack nextPreview=empty.copy();
            IFluidHandlerItem preview=handler(nextPreview);
            if(preview==null||preview.fill(actual,IFluidHandler.FluidAction.EXECUTE)!=1000
                ||!KingdomLivestockStorage.canInsert(finished,preview.getContainer()))return false;
            // Consume the native TFC animal cooldown before materializing fluid in the carried vessel.
            cooldown.invoke(animal);
            int wear=Math.max(0,Math.min(10,((Number)eventUses.invoke(event)).intValue()));
            uses.invoke(animal,wear);
            w.fieldInventory.set(position,preview.getContainer().copy());
            w.workUnits++;w.fieldHarvested++;
            w.energy=Math.max(0,w.energy-1);w.skillXp++;
            KingdomNpcData.get(level.getServer()).setDirty();
            return true;
        }catch(ReflectiveOperationException|RuntimeException ex){
            System.err.println("[Kingdom] TFC milk transfer failed closed: "+ex);
            return false;
        }
    }
}
