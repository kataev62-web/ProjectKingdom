package ru.projectkingdom.npc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandlerModifiable;
import ru.projectkingdom.enterprise.EnterpriseKind;

/**
 * True leather production through TFC's sealed barrel, NEVER instant-crafting a
 * hide into leather. The TFC barrel remains solely responsible for elapsed TFC
 * time, fluid consumption, and native recipe assembly.
 *
 * Handles the three physical sealed-barrel steps for small/medium/large hides:
 * raw -> soaked, scraped -> prepared, prepared -> leather. Native hide scraping
 * between first and second steps is a separate physical operation.
 *
 * This first adapter requires a barrel prefilled with the required real fluid.
 * A mismatched/empty fluid or a busy/player-owned barrel fails closed.
 */
public final class KingdomTfcTanneryWork {
    private KingdomTfcTanneryWork(){}
    private static final Set<String> RECIPES=Set.of(
        "tfc:barrel/small_soaked_hide","tfc:barrel/medium_soaked_hide",
        "tfc:barrel/large_soaked_hide","tfc:barrel/small_prepared_hide",
        "tfc:barrel/medium_prepared_hide","tfc:barrel/large_prepared_hide",
        "tfc:barrel/small_leather","tfc:barrel/medium_leather",
        "tfc:barrel/large_leather");
    static boolean supportedRecipe(String id){return id!=null&&RECIPES.contains(id);}
    private static boolean attempted,available;
    private static Class<?> barrelClass;
    private static Field inventoryField;
    private static Method nativeRecipe,seal,ingredientItem,ingredientFluid,
        outputItem,singleStack,recipeDuration;
    private static RecipeType<?> sealedType;

    public static boolean isBarrel(ServerLevel level,KingdomNpcData.Worker w){
        if(w==null||!"tanner".equals(w.job)||!w.hasWorkstation
            ||!ModList.get().isLoaded("tfc"))return false;
        BlockPos pos=new BlockPos(w.workX,w.workY,w.workZ);
        return level.hasChunkAt(pos)&&"tfc:barrel".equals(BuiltInRegistries.BLOCK.getKey(
            level.getBlockState(pos).getBlock()).toString());
    }
    private static boolean init(){
        if(attempted)return available;
        attempted=true;
        if(!ModList.get().isLoaded("tfc"))return false;
        try{
            barrelClass=Class.forName("net.dries007.tfc.common.blockentities.BarrelBlockEntity");
            Class<?> inventory=Class.forName("net.dries007.tfc.common.blockentities.InventoryBlockEntity");
            Class<?> recipe=Class.forName("net.dries007.tfc.common.recipes.BarrelRecipe");
            Class<?> sealedRecipe=Class.forName("net.dries007.tfc.common.recipes.SealedBarrelRecipe");
            Class<?> block=Class.forName("net.dries007.tfc.common.blocks.devices.BarrelBlock");
            Class<?> recipeTypes=Class.forName("net.dries007.tfc.common.recipes.TFCRecipeTypes");
            Object registry=recipeTypes.getField("BARREL_SEALED").get(null);
            sealedType=(RecipeType<?>)registry.getClass().getMethod("get").invoke(registry);
            inventoryField=inventory.getDeclaredField("inventory");
            inventoryField.setAccessible(true);
            nativeRecipe=barrelClass.getMethod("getRecipe");
            seal=block.getMethod("toggleSeal",Level.class,BlockPos.class,BlockState.class);
            ingredientItem=recipe.getMethod("getInputItem");
            ingredientFluid=recipe.getMethod("getInputFluid");
            outputItem=recipe.getMethod("getOutputItem");
            recipeDuration=sealedRecipe.getMethod("getDuration");
            Class<?> provider=Class.forName("net.dries007.tfc.common.recipes.outputs.ItemStackProvider");
            singleStack=provider.getMethod("getSingleStack",ItemStack.class);
            available=true;
        }catch(ReflectiveOperationException|LinkageError ex){
            System.err.println("[Kingdom] Native TFC tannery disabled: "+ex);
        }
        return available;
    }
    private static String id(ItemStack stack){
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
    private static boolean sealed(BlockState state){
        for(var property:state.getProperties())
            if("sealed".equals(property.getName())
                &&property instanceof net.minecraft.world.level.block.state.properties.BooleanProperty flag)
                return state.getValue(flag);
        return false;
    }
    private static boolean toggle(ServerLevel level,BlockPos station,BlockState state){
        if(!init())return false;
        try{
            seal.invoke(null,level,station,state);
            return sealed(level.getBlockState(station))!=sealed(state);
        }catch(ReflectiveOperationException|RuntimeException ex){
            available=false;return false;
        }
    }
    private record Plan(String id,String input,String output,ItemStack product){}

    /** Native TFC matching: real item ingredient, fluid ingredient and output. */
    private static Plan plan(ServerLevel level,ItemStack one,FluidStack fluid){
        if(one.isEmpty()||fluid.isEmpty()||!init()
            ||KingdomCurrency.value(one)>0||one.is(KingdomCurrency.WALLET.get()))return null;
        try{
            for(var nativeEntry:level.getRecipeManager().getAllRecipesFor((RecipeType)sealedType)){
                if(!(nativeEntry instanceof Recipe<?> recipe)
                    || !supportedRecipe(recipe.getId().toString()))continue;
                Object itemRule=ingredientItem.invoke(nativeEntry);
                Object fluidRule=ingredientFluid.invoke(nativeEntry);
                if(!Boolean.TRUE.equals(itemRule.getClass().getMethod("test",ItemStack.class)
                        .invoke(itemRule,one))
                    ||!Boolean.TRUE.equals(fluidRule.getClass().getMethod("test",FluidStack.class)
                        .invoke(fluidRule,fluid)))continue;
                int duration=((Number)recipeDuration.invoke(nativeEntry)).intValue();
                if(duration<=0)continue;
                ItemStack product=(ItemStack)singleStack.invoke(outputItem.invoke(nativeEntry),one.copy());
                if(product==null||product.isEmpty()||product.getCount()<1
                    ||KingdomCurrency.value(product)>0
                    ||product.is(KingdomCurrency.WALLET.get()))continue;
                return new Plan(recipe.getId().toString(),id(one),id(product),product);
            }
        }catch(ReflectiveOperationException|RuntimeException ex){
            available=false;
            System.err.println("[Kingdom] Native hide recipe mismatch, stopped: "+ex);
        }
        return null;
    }

    /** Real liquid reagents ONLY: never spawn water, limewater or tannin.
     * One vessel at a time. Barrels receive exactly the fluid actually drained
     * from a single physical Forge item container in the authorized RAW store. */
    @SuppressWarnings({"rawtypes","unchecked"})
    private static boolean supplyReagent(ServerLevel level,BlockPos station,Container raw,
                                         IFluidHandler barrel){
        FluidStack inside=barrel.getFluidInTank(0);
        try{
            for(int rawSlot=0;rawSlot<raw.getContainerSize();rawSlot++){
                ItemStack candidate=raw.getItem(rawSlot);
                if(candidate.isEmpty()||!raw.canTakeItem(raw,rawSlot,candidate)
                    ||KingdomCurrency.value(candidate)>0)continue;
                ItemStack one=candidate.copyWithCount(1);
                for(var entry:level.getRecipeManager().getAllRecipesFor((RecipeType)sealedType)){
                    if(!(entry instanceof Recipe<?> recipe)
                        ||!supportedRecipe(recipe.getId().toString()))continue;
                    Object itemRule=ingredientItem.invoke(entry);
                    if(!Boolean.TRUE.equals(itemRule.getClass().getMethod("test",ItemStack.class)
                        .invoke(itemRule,one)))continue;
                    Object reagent=ingredientFluid.invoke(entry);
                    int amount=((Number)reagent.getClass().getMethod("amount").invoke(reagent))
                        .intValue();
                    if(amount<=inside.getAmount())continue;
                    Object fluidType=reagent.getClass().getMethod("ingredient").invoke(reagent);
                    if(!(fluidType instanceof java.util.function.Predicate predicate))continue;
                    for(int slot=0;slot<raw.getContainerSize();slot++){
                        ItemStack vessel=raw.getItem(slot);
                        if(vessel.isEmpty()||vessel.getCount()!=1||slot==rawSlot
                            ||!raw.canTakeItem(raw,slot,vessel)
                            ||KingdomCurrency.value(vessel)>0)continue;
                        ItemStack copy=vessel.copy();
                        var container=copy.getCapability(
                            net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER_ITEM)
                            .orElse(null);
                        if(container==null)continue;
                        FluidStack held=container.drain(Integer.MAX_VALUE,
                            IFluidHandler.FluidAction.SIMULATE);
                        if(held.isEmpty()||!predicate.test(held.getFluid()))continue;
                        if(!inside.isEmpty()&&!inside.isFluidEqual(held))continue;
                        // Atomic whole-container transfer only. Partial nonstandard
                        // drainage can destroy stackable vessels and is refused.
                        int accepted=barrel.fill(held,IFluidHandler.FluidAction.SIMULATE);
                        if(accepted!=held.getAmount())continue;
                        FluidStack withdrawn=container.drain(held.getAmount(),
                            IFluidHandler.FluidAction.EXECUTE);
                        if(withdrawn.getAmount()!=held.getAmount()
                            ||!withdrawn.isFluidEqual(held))continue;
                        int delivered=barrel.fill(withdrawn,IFluidHandler.FluidAction.EXECUTE);
                        if(delivered!=withdrawn.getAmount()){
                            FluidStack leftover=withdrawn.copy();
                            leftover.setAmount(Math.max(0,withdrawn.getAmount()-delivered));
                            if(!leftover.isEmpty())
                                container.fill(leftover,IFluidHandler.FluidAction.EXECUTE);
                        }
                        raw.setItem(slot,container.getContainer());
                        raw.setChanged();
                        return delivered>0;
                    }
                }
            }
        }catch(ReflectiveOperationException|RuntimeException ex){
            available=false;
            System.err.println("[Kingdom] Real TFC hide reagent transfer unavailable: "+ex);
        }
        return false;
    }

    private static ItemStack[] storagePlan(Container storage,ItemStack product){
        var proposed=new ItemStack[storage.getContainerSize()];
        for(int i=0;i<proposed.length;i++)proposed[i]=storage.getItem(i).copy();
        ItemStack remaining=product.copy();
        for(int i=0;i<proposed.length&&!remaining.isEmpty();i++){
            var cell=proposed[i];
            if(cell.isEmpty()||!ItemStack.isSameItemSameTags(cell,remaining)
                ||!storage.canPlaceItem(i,remaining))continue;
            int room=Math.max(0,Math.min(cell.getMaxStackSize(),
                storage.getMaxStackSize())-cell.getCount());
            int n=Math.min(room,remaining.getCount());
            if(n>0){cell.grow(n);remaining.shrink(n);}
        }
        for(int i=0;i<proposed.length&&!remaining.isEmpty();i++){
            if(!proposed[i].isEmpty()||!storage.canPlaceItem(i,remaining))continue;
            int n=Math.min(remaining.getCount(),Math.min(remaining.getMaxStackSize(),
                storage.getMaxStackSize()));
            if(n<=0)continue;
            proposed[i]=remaining.copyWithCount(n);remaining.shrink(n);
        }
        return remaining.isEmpty()?proposed:null;
    }
    private static void restore(ServerLevel level,BlockPos station,Container raw,
                                int slot,ItemStack stack){
        if(stack.isEmpty())return;
        ItemStack target=raw.getItem(slot);
        if(target.isEmpty()&&raw.canPlaceItem(slot,stack))raw.setItem(slot,stack);
        else if(ItemStack.isSameItemSameTags(target,stack)
            &&target.getCount()+stack.getCount()<=Math.min(target.getMaxStackSize(),
                raw.getMaxStackSize())){target.grow(stack.getCount());raw.setChanged();}
        else Block.popResource(level,station,stack.copy());
    }
    private static boolean deliver(ServerLevel level,KingdomNpcData.Worker w,
           BlockPos station,IItemHandlerModifiable handler,Container output,
           KingdomTfcTanneryData.Batch batch){
        ItemStack leather=handler.getStackInSlot(2).copy();
        if(leather.isEmpty()||!batch.outputId().equals(id(leather)))return false;
        ItemStack[] plan=storagePlan(output,leather);
        if(plan==null)return false;
        if(!ItemStack.matches(handler.extractItem(2,leather.getCount(),true),leather))
            return false;
        ItemStack extracted=handler.extractItem(2,leather.getCount(),false);
        if(!ItemStack.matches(extracted,leather)){
            if(!extracted.isEmpty())Block.popResource(level,station,extracted);
            return false;
        }
        for(int i=0;i<plan.length;i++)output.setItem(i,plan[i]);
        output.setChanged();
        KingdomTfcTanneryData.get(level.getServer()).release(w.entityId);
        w.produced+=leather.getCount();w.workUnits++;w.skillXp++;
        w.energy=Math.max(0,w.energy-1);
        KingdomNpcData.get(level.getServer()).setDirty();
        return true;
    }
    public static boolean tick(ServerLevel level,Villager npc,KingdomNpcData.Worker w){
        if(!isBarrel(level,w)||!init()||w.employer==null
            ||!w.hasSource||!w.hasDestination
            ||!KingdomNpcDuty.autonomousOrder(w.order)
            ||!"CONTINUE_ASSIGNED_JOB".equals(w.autonomousIntent)
            ||!NpcLife.ableToWork(w.ageYears,w.hunger,w.energy,w.contractPaused)
            ||!KingdomNpcWorkday.employed(level.getServer(),w)
            ||!w.dimension.equals(level.dimension().location().toString()))return false;
        var firms=KingdomEnterpriseData.get(level.getServer());
        boolean assigned=false;
        for(var payroll:KingdomEnterprisePayrollData.get(level.getServer()).all()){
            if(!payroll.active()||!payroll.worker().equals(w.entityId)
                ||(!w.workplaceId.isBlank()
                    &&!w.workplaceId.equals(payroll.enterprise().toString()))
                ||firms.kind(payroll.enterprise())!=EnterpriseKind.TANNERY)continue;
            var company=firms.find(payroll.enterprise());
            var core=firms.core(payroll.enterprise());
            if(company!=null&&company.owner().equals(w.employer)
                &&core!=null&&core.active()&&core.kind()==EnterpriseKind.TANNERY){
                assigned=true;break;
            }
        }
        if(!assigned)return false;
        BlockPos pos=new BlockPos(w.workX,w.workY,w.workZ);
        BlockPos source=new BlockPos(w.sourceX,w.sourceY,w.sourceZ);
        BlockPos target=new BlockPos(w.destX,w.destY,w.destZ);
        if(pos.distSqr(source)>36||pos.distSqr(target)>36
            ||!level.hasChunkAt(pos)||!level.hasChunkAt(source)
            ||!level.hasChunkAt(target))return false;
        var territory=KingdomTerritories.get(level.getServer());
        if(!territory.usable(level,pos,w.employer,"workstation")
            ||!territory.usable(level,source,w.employer,"raw")
            ||!territory.usable(level,target,w.employer,"finished"))return false;
        if(!(level.getBlockEntity(source) instanceof Container raw)
            ||!(level.getBlockEntity(target) instanceof Container finished)
            ||raw==finished)return false;
        BlockEntity block=level.getBlockEntity(pos);
        if(block==null||!barrelClass.isInstance(block))return false;
        if(npc.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>6.25){
            npc.getNavigation().moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,.8);
            return false;
        }
        try{
            Object internal=inventoryField.get(block);
            if(!(internal instanceof IItemHandlerModifiable handler)
                ||!(internal instanceof IFluidHandler fluids)||handler.getSlots()<3
                ||fluids.getTanks()<1)return false;
            var data=KingdomTfcTanneryData.get(level.getServer());
            if(data.occupied(w.entityId,w.dimension,pos.asLong()))return false;
            var job=data.get(w.entityId);
            if(job!=null&&(!job.dimension().equals(w.dimension)
                ||job.station()!=pos.asLong()))return false;
            BlockState state=level.getBlockState(pos);
            boolean closed=sealed(state);
            ItemStack in=handler.getStackInSlot(2).copy();
            if(job!=null){
                if(!in.isEmpty()&&job.outputId().equals(id(in))){
                    // The native TFC tick already consumed the real liquid and time.
                    if(closed)return toggle(level,pos,state);
                    return deliver(level,w,pos,handler,finished,job);
                }
                // Unseal only after a real finished output. Do not force-progress
                // native calendar or change somebody else's partially cooked batch.
                return false;
            }
            if(closed||!in.isEmpty())return false;
            FluidStack liquid=fluids.getFluidInTank(0);
            if(liquid.isEmpty())return supplyReagent(level,pos,raw,fluids);
            for(int slot=0;slot<raw.getContainerSize();slot++){
                ItemStack available=raw.getItem(slot);
                if(available.isEmpty()||!raw.canTakeItem(raw,slot,available))continue;
                ItemStack one=available.copyWithCount(1);
                Plan recipe=plan(level,one,liquid);
                if(recipe==null||storagePlan(finished,recipe.product())==null
                    ||!handler.insertItem(2,one.copy(),true).isEmpty())continue;
                ItemStack spent=raw.removeItem(slot,1);
                if(!ItemStack.matches(spent,one)){
                    restore(level,pos,raw,slot,spent);return false;
                }
                ItemStack remainder=handler.insertItem(2,spent,false);
                if(!remainder.isEmpty()){
                    restore(level,pos,raw,slot,remainder);return false;
                }
                raw.setChanged();
                var stored=new KingdomTfcTanneryData.Batch(w.entityId,w.dimension,
                    pos.asLong(),recipe.input(),recipe.output(),recipe.id());
                if(!data.claim(stored))return false; // Material remains physically in barrel.
                if(!toggle(level,pos,level.getBlockState(pos)))return false;
                block.setChanged();
                return true;
            }
            // A TFC hide recipe can require more liquid than one jug carries.
            // Replenish only with an appropriate real fluid vessel, then retry.
            return supplyReagent(level,pos,raw,fluids);
        }catch(ReflectiveOperationException|RuntimeException ex){
            available=false;
            System.err.println("[Kingdom] Native TFC hide barrel stopped: "+ex);
            return false;
        }
    }
}
