package ru.projectkingdom.npc;

import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.fluids.FluidStack;

/**
 * Native TFC simple POT recipes (not soup/jam). The pot itself consumes actual
 * water and fuel and executes heating/food-decay rules. Only bounded, prefilled
 * machines, physical stock, and deterministic food outputs may be automated.
 * TFC classes are resolved at runtime to preserve the Forge-only one-JAR build.
 */
public final class KingdomTfcPotWork {
    private KingdomTfcPotWork(){}
    private static final int FUEL=3,FIRST=4,LAST=8;
    private static boolean initialized,compatible;
    private static Class<?> simpleRecipe;
    private static Method inventory,getOutput,hasStarted,light,fluidIngredient,itemIngredients,
        outputProviders,displayFluid,singleResult,fluidInTank;
    private static RecipeType<?> potType;

    public static boolean isPot(ServerLevel level,KingdomNpcData.Worker w){
        if(w==null||!w.hasWorkstation || !KingdomProfessionWorkRules.pot(w.job,
                ru.projectkingdom.enterprise.EnterpriseKind.BAKERY)
                ||!ModList.get().isLoaded("tfc"))return false;
        BlockPos p=new BlockPos(w.workX,w.workY,w.workZ);
        return level.hasChunkAt(p) && "tfc:pot".equals(BuiltInRegistries.BLOCK.getKey(
            level.getBlockState(p).getBlock()).toString());
    }
    private static boolean init(){
        if(initialized)return compatible;
        initialized=true;
        if(!ModList.get().isLoaded("tfc"))return false;
        try{
            Class<?> pot=Class.forName("net.dries007.tfc.common.blockentities.PotBlockEntity");
            Class<?> pit=Class.forName("net.dries007.tfc.common.blockentities.AbstractFirepitBlockEntity");
            Class<?> recipe=Class.forName("net.dries007.tfc.common.recipes.PotRecipe");
            simpleRecipe=Class.forName("net.dries007.tfc.common.recipes.SimplePotRecipe");
            Class<?> ingredient=Class.forName("net.dries007.tfc.common.recipes.ingredients.FluidStackIngredient");
            Class<?> provider=Class.forName("net.dries007.tfc.common.recipes.outputs.ItemStackProvider");
            Class<?> types=Class.forName("net.dries007.tfc.common.recipes.TFCRecipeTypes");
            Object registry=types.getField("POT").get(null);
            potType=(RecipeType<?>)registry.getClass().getMethod("get").invoke(registry);
            inventory=pot.getMethod("getInventory");
            getOutput=pot.getMethod("getOutput");
            hasStarted=pot.getMethod("hasRecipeStarted");
            light=pit.getMethod("light",net.minecraft.world.level.block.state.BlockState.class);
            fluidIngredient=recipe.getMethod("getFluidIngredient");
            itemIngredients=recipe.getMethod("getItemIngredients");
            outputProviders=simpleRecipe.getMethod("getOutputProviders");
            displayFluid=simpleRecipe.getMethod("getDisplayFluid");
            singleResult=provider.getMethod("getSingleStack",ItemStack.class);
            Class<?> inv=Class.forName("net.dries007.tfc.common.blockentities.PotBlockEntity$PotInventory");
            fluidInTank=inv.getMethod("getFluidInTank",int.class);
            compatible=true;
        }catch(ReflectiveOperationException|LinkageError ex){
            System.err.println("[Kingdom] Native TFC pot unavailable: "+ex);
        }
        return compatible;
    }
    private static String id(ItemStack stack){
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
    private static boolean cookable(ItemStack stack){
        return !stack.isEmpty()&&!KingdomNpcNutrition.spoiled(stack)
            && id(stack).startsWith("tfc:food/")
            && KingdomCurrency.value(stack)==0 && !stack.is(KingdomCurrency.WALLET.get());
    }
    private record Plan(List<Integer> slots,List<ItemStack> inputs,List<ItemStack> outputs,
                        Recipe<?> recipe,String specialMode){
        private Plan(List<Integer> slots,List<ItemStack> inputs,List<ItemStack> outputs,Recipe<?> recipe){
            this(slots,inputs,outputs,recipe,"");
        }
    }
    /** Soup and jam create special TFC native outputs, not items in pot slots. */
    private static Plan findSpecialRecipe(ServerLevel level,Object potInventory,
                                          Container raw,Container finished){
        try{
            Object fluid=fluidInTank.invoke(potInventory,0);
            if(!(fluid instanceof FluidStack water)||water.isEmpty())return null;
            // Reserve at least one free destination slot; final NBT and recipe yield
            // are validated after native cooking and before removing any portion.
            boolean space=false;
            for(int i=0;i<finished.getContainerSize();i++)
                if(finished.getItem(i).isEmpty()){space=true;break;}
            if(!space)return null;
            List<? extends Recipe<?>> recipes=level.getRecipeManager()
                .getAllRecipesFor((RecipeType)potType).stream()
                .sorted(Comparator.comparing(r->((Recipe<?>)r).getId().toString())).toList();
            for(Object candidate:recipes){
                if(!(candidate instanceof Recipe<?> recipe))continue;
                String mode=KingdomTfcSpecialPotWork.recipeMode(candidate);
                if(mode.isBlank()||!KingdomTfcSpecialPotWork.hasServingContainer(raw,mode))
                    continue;
                Object fluidRequirement=fluidIngredient.invoke(candidate);
                if(!Boolean.TRUE.equals(fluidRequirement.getClass()
                    .getMethod("test",FluidStack.class).invoke(fluidRequirement,water)))continue;
                List<?> ingredients=(List<?>)itemIngredients.invoke(candidate);
                if(ingredients.isEmpty()||ingredients.size()>5)continue;
                List<Integer> slots=new ArrayList<>();
                List<ItemStack> inputs=new ArrayList<>();
                Map<Integer,Integer> reserved=new HashMap<>();
                boolean matched=true;
                for(Object targetObject:ingredients){
                    if(!(targetObject instanceof Ingredient target)){matched=false;break;}
                    int picked=-1;
                    for(int slot=0;slot<raw.getContainerSize();slot++){
                        ItemStack available=raw.getItem(slot);
                        if(!cookable(available)||!raw.canTakeItem(raw,slot,available)
                            ||available.getCount()<=reserved.getOrDefault(slot,0))continue;
                        ItemStack one=available.copyWithCount(1);
                        if(target.test(one)){picked=slot;inputs.add(one);break;}
                    }
                    if(picked<0){matched=false;break;}
                    slots.add(picked);reserved.merge(picked,1,Integer::sum);
                }
                if(matched)return new Plan(slots,inputs,List.of(),recipe,mode);
            }
        }catch(ReflectiveOperationException|RuntimeException ex){
            System.err.println("[Kingdom] TFC special recipe matching stopped: "+ex);
        }
        return null;
    }
    /** Bounded simple pot recipe matching against real existing water and stock. */
    private static Plan findRecipe(ServerLevel level,Object potInventory,Container raw,Container finished){
        try{
            Object fluid=fluidInTank.invoke(potInventory,0);
            if(!(fluid instanceof FluidStack water)||water.isEmpty())return null;
            int[] balancedStock=KingdomFoodBalance.freshNutritionStock(finished);
            Plan bestPlan=null;
            int bestScore=Integer.MIN_VALUE;
            for(Object recipeObject:level.getRecipeManager().getAllRecipesFor((RecipeType)potType)){
                if(!simpleRecipe.isInstance(recipeObject) || !(recipeObject instanceof Recipe<?> recipe))continue;
                FluidStack outputFluid=(FluidStack)displayFluid.invoke(recipeObject);
                if(outputFluid==null||!outputFluid.isEmpty())continue;
                Object req=fluidIngredient.invoke(recipeObject);
                if(!Boolean.TRUE.equals(req.getClass().getMethod("test",FluidStack.class).invoke(req,water)))
                    continue;
                List<?> ingredients=(List<?>)itemIngredients.invoke(recipeObject);
                List<?> providers=(List<?>)outputProviders.invoke(recipeObject);
                if(ingredients.isEmpty()||ingredients.size()>5
                    ||providers.size()!=ingredients.size())continue;
                List<Integer> slots=new ArrayList<>();
                List<ItemStack> inputs=new ArrayList<>(),outputs=new ArrayList<>();
                Set<String> consumedIds=new HashSet<>(),madeIds=new HashSet<>();
                boolean valid=true;
                Map<Integer,Integer> reserved=new HashMap<>();
                for(int ingredientIndex=0;ingredientIndex<ingredients.size();ingredientIndex++){
                    if(!(ingredients.get(ingredientIndex) instanceof Ingredient target)){valid=false;break;}
                    int best=-1;ItemStack chosen=ItemStack.EMPTY,produced=ItemStack.EMPTY;
                    for(int slot=0;slot<raw.getContainerSize();slot++){
                        ItemStack stock=raw.getItem(slot);
                        if(!cookable(stock)||!raw.canTakeItem(raw,slot,stock)
                            ||stock.getCount()<=reserved.getOrDefault(slot,0))continue;
                        ItemStack one=stock.copy();one.setCount(1);
                        if(!target.test(one))continue; // TFC not_rotten and NBT ingredient rules
                        Object result=singleResult.invoke(providers.get(ingredientIndex),one.copy());
                        if(!(result instanceof ItemStack output)||!cookable(output)
                                ||output.getCount()!=1)continue;
                        best=slot;chosen=one;produced=output;break;
                    }
                    if(best<0){valid=false;break;}
                    reserved.merge(best,1,Integer::sum);
                    slots.add(best);inputs.add(chosen);outputs.add(produced);
                    consumedIds.add(id(chosen));madeIds.add(id(produced));
                }
                // Cannot distinguish TFC progress from player ingredients when IDs coincide.
                if(!valid || madeIds.stream().anyMatch(consumedIds::contains)
                    || !canAcceptAll(finished,outputs))continue;
                // Hysteresis: avoid flooding a real pantry with one type of cooked food.
                boolean overstocked=false;
                for(ItemStack produced:outputs)
                    if(freshCount(finished,id(produced))>=32){overstocked=true;break;}
                if(overstocked)continue;
                int score=KingdomFoodBalance.scoreOutputs(balancedStock,outputs);
                if(bestPlan==null||score>bestScore
                    ||(score==bestScore
                        &&recipe.getId().toString().compareTo(bestPlan.recipe().getId().toString())<0)){
                    bestPlan=new Plan(slots,inputs,outputs,recipe);
                    bestScore=score;
                }
            }
            return bestPlan!=null?bestPlan:findSpecialRecipe(level,potInventory,raw,finished);
        }catch(ReflectiveOperationException | RuntimeException ex){
            // Incompatible TFC API disables rather than inventing outputs.
            compatible=false;
            System.err.println("[Kingdom] Pot recipe selection disabled: "+ex);
        }
        return null;
    }
    static int freshCount(Container store,String itemId){
        int total=0;
        for(int i=0;i<store.getContainerSize();i++){
            ItemStack stack=store.getItem(i);
            if(!stack.isEmpty()&&itemId.equals(id(stack))&&!KingdomNpcNutrition.spoiled(stack))
                total+=stack.getCount();
        }
        return total;
    }
    /** Simulate a whole multi-item deposit; never partially overwrite a pantry. */
    private static ItemStack[] planDeposit(Container dest,List<ItemStack> output){
        ItemStack[] snapshot=new ItemStack[dest.getContainerSize()];
        for(int i=0;i<snapshot.length;i++)snapshot[i]=dest.getItem(i).copy();
        for(ItemStack item:output){
            if(item.isEmpty())return null;
            ItemStack remainder=item.copy();
            for(int i=0;i<snapshot.length&&!remainder.isEmpty();i++){
                var target=snapshot[i];
                if(target.isEmpty()||!ItemStack.isSameItemSameTags(target,remainder)
                    ||!dest.canPlaceItem(i,remainder))continue;
                int room=Math.max(0,Math.min(target.getMaxStackSize(),dest.getMaxStackSize())-target.getCount());
                int amount=Math.min(room,remainder.getCount());
                if(amount>0){target.grow(amount);remainder.shrink(amount);}
            }
            for(int i=0;i<snapshot.length&&!remainder.isEmpty();i++){
                if(!snapshot[i].isEmpty()||!dest.canPlaceItem(i,remainder))continue;
                int amount=Math.min(remainder.getCount(),
                    Math.min(remainder.getMaxStackSize(),dest.getMaxStackSize()));
                if(amount<=0)continue;
                ItemStack taken=remainder.copy();taken.setCount(amount);
                snapshot[i]=taken;remainder.shrink(amount);
            }
            if(!remainder.isEmpty())return null;
        }
        return snapshot;
    }
    private static boolean canAcceptAll(Container dest,List<ItemStack> outputs){
        return planDeposit(dest,outputs)!=null;
    }
    private static void fuel(Container raw,IItemHandler machine){
        if(!machine.getStackInSlot(FUEL).isEmpty())return;
        for(int i=0;i<raw.getContainerSize();i++){
            ItemStack item=raw.getItem(i);
            if(item.isEmpty()||!raw.canTakeItem(raw,i,item)||KingdomCurrency.value(item)>0)continue;
            ItemStack one=item.copy();one.setCount(1);
            if(!machine.insertItem(FUEL,one,true).isEmpty())continue;
            ItemStack extracted=raw.removeItem(i,1);
            if(!ItemStack.matches(one,extracted))return;
            ItemStack remainder=machine.insertItem(FUEL,extracted,false);
            if(!remainder.isEmpty()){
                ItemStack left=raw.getItem(i);
                if(left.isEmpty())raw.setItem(i,remainder);
                else if(ItemStack.isSameItemSameTags(left,remainder)
                    && left.getCount()+remainder.getCount()<=left.getMaxStackSize())
                    left.grow(remainder.getCount());
            }
            raw.setChanged();
            return;
        }
    }
    private static void lightPot(BlockEntity pot,ServerLevel level,BlockPos at){
        try{
            if(!Boolean.TRUE.equals(hasStarted.invoke(pot)))
                light.invoke(pot,level.getBlockState(at));
        }catch(ReflectiveOperationException|RuntimeException ex){compatible=false;}
    }
    /** Native pot output remains in TFC slots until the worker has warehouse space. */
    private static boolean collect(ServerLevel level,KingdomNpcData.Worker worker,
                                   KingdomTfcPotData.Batch batch,IItemHandler machine,
                                   Container output){
        List<ItemStack> made=new ArrayList<>();
        boolean finished=true,untouched=true;
        for(int i=0;i<batch.inputs().size();i++){
            ItemStack current=machine.getStackInSlot(FIRST+i).copy();
            if(current.isEmpty()){finished=false;untouched=false;break;}
            if(!id(current).equals(batch.outputs().get(i)))finished=false;
            if(!id(current).equals(batch.inputs().get(i)))untouched=false;
            made.add(current);
        }
        if(!finished){
            // A manually changed machine is not property the NPC may seize.
            if(!untouched)KingdomTfcPotData.get(level.getServer()).remove(worker.entityId);
            return false;
        }
        ItemStack[] after=planDeposit(output,made);
        if(after==null)return false;
        for(int i=0;i<made.size();i++)
            if(!ItemStack.matches(machine.extractItem(FIRST+i,made.get(i).getCount(),true),made.get(i)))
                return false;
        // All output slots and the whole receiving inventory were prevalidated.
        for(int i=0;i<made.size();i++){
            ItemStack real=machine.extractItem(FIRST+i,made.get(i).getCount(),false);
            if(!ItemStack.matches(real,made.get(i)))return false;
        }
        for(int i=0;i<after.length;i++)output.setItem(i,after[i]);
        output.setChanged();
        KingdomTfcPotData.get(level.getServer()).remove(worker.entityId);
        worker.produced+=made.stream().mapToInt(ItemStack::getCount).sum();
        worker.skillXp++;worker.workUnits++;worker.energy=Math.max(0,worker.energy-1);
        KingdomNpcData.get(level.getServer()).setDirty();
        return true;
    }
    /**
     * Roll back only the real items NOT accepted by the TFC machine.
     * If a source slot unexpectedly refuses an item, materialize it as a
     * physical world drop instead of silently deleting it.
     */
    private static void returnToSource(ServerLevel level,Container raw,int slot,
                                       ItemStack left,BlockPos machinePos){
        if(left.isEmpty())return;
        ItemStack saved=raw.getItem(slot);
        if(saved.isEmpty()&&raw.canPlaceItem(slot,left)){
            raw.setItem(slot,left.copy());return;
        }
        if(!saved.isEmpty()&&ItemStack.isSameItemSameTags(saved,left)
                &&saved.getCount()<=Math.min(saved.getMaxStackSize(),
                    raw.getMaxStackSize())-left.getCount()){
            saved.grow(left.getCount());raw.setChanged();return;
        }
        // Last-resort recovery: the item stays real and recoverable by the player.
        net.minecraft.world.level.block.Block.popResource(level,machinePos,left.copy());
    }

    private static boolean fill(ServerLevel level,KingdomNpcData.Worker worker,
                                Plan plan,Container raw,IItemHandler machine,BlockPos pos){
        int n=plan.inputs().size();
        Map<Integer,Integer> requirements=new HashMap<>();
        for(int i=0;i<n;i++){
            ItemStack one=plan.inputs().get(i);
            int sourceSlot=plan.slots().get(i);
            requirements.merge(sourceSlot,1,Integer::sum);
            ItemStack available=raw.getItem(sourceSlot);
            if(available.getCount()<requirements.get(sourceSlot)
                ||!ItemStack.isSameItemSameTags(available,one)
                ||!raw.canTakeItem(raw,sourceSlot,available)
                ||!machine.getStackInSlot(FIRST+i).isEmpty()
                ||!machine.insertItem(FIRST+i,one.copy(),true).isEmpty())return false;
        }
        List<ItemStack> extracted=new ArrayList<>();
        for(int i=0;i<n;i++){
            ItemStack taken=raw.removeItem(plan.slots().get(i),1);
            if(!ItemStack.matches(taken,plan.inputs().get(i))){
                returnToSource(level,raw,plan.slots().get(i),taken,pos);
                for(int j=0;j<extracted.size();j++)
                    returnToSource(level,raw,plan.slots().get(j),extracted.get(j),pos);
                raw.setChanged();
                return false;
            }
            extracted.add(taken);
        }
        for(int i=0;i<n;i++){
            ItemStack remainder=machine.insertItem(FIRST+i,extracted.get(i),false);
            if(!remainder.isEmpty()){
                // Accepted items stay IN the physical pot. Return only those NOT accepted.
                returnToSource(level,raw,plan.slots().get(i),remainder,pos);
                for(int j=i+1;j<n;j++)
                    returnToSource(level,raw,plan.slots().get(j),extracted.get(j),pos);
                raw.setChanged();
                return false; // No batch credit, no invented output.
            }
        }
        raw.setChanged();
        List<String> inputs=plan.inputs().stream().map(KingdomTfcPotWork::id).toList();
        List<String> outputs=plan.specialMode().isBlank()
            ? plan.outputs().stream().map(KingdomTfcPotWork::id).toList()
            : Collections.nCopies(plan.inputs().size(),plan.specialMode());
        KingdomTfcPotData.get(level.getServer()).put(new KingdomTfcPotData.Batch(
            worker.entityId,worker.dimension,pos.asLong(),inputs,outputs));
        return true;
    }
    /** Operates only with a real contracted food worker at a real TFC pot. */
    public static boolean tick(ServerLevel level,Villager npc,KingdomNpcData.Worker w){
        if(!isPot(level,w)||!init()||w.employer==null||!w.hasSource||!w.hasDestination
            ||!KingdomNpcDuty.autonomousOrder(w.order)
            ||!"CONTINUE_ASSIGNED_JOB".equals(w.autonomousIntent)
            ||!NpcLife.ableToWork(w.ageYears,w.hunger,w.energy,w.contractPaused)
            ||!w.dimension.equals(level.dimension().location().toString())
            ||!KingdomNpcWorkday.employed(level.getServer(),w))return false;
        var firms=KingdomEnterpriseData.get(level.getServer());
        boolean permitted=false;
        for(var contract:KingdomEnterprisePayrollData.get(level.getServer()).all()){
            if(!contract.active()||!contract.worker().equals(w.entityId)
                ||!KingdomProfessionWorkRules.pot(w.job,firms.kind(contract.enterprise()))
                ||(!w.workplaceId.isBlank()
                    &&!w.workplaceId.equals(contract.enterprise().toString())))continue;
            var firm=firms.find(contract.enterprise());var core=firms.core(contract.enterprise());
            if(firm!=null&&firm.owner().equals(w.employer)
                &&core!=null&&core.active()
                &&core.kind()==ru.projectkingdom.enterprise.EnterpriseKind.BAKERY){
                permitted=true;break;
            }
        }
        if(!permitted)return false;
        BlockPos potPos=new BlockPos(w.workX,w.workY,w.workZ);
        BlockPos source=new BlockPos(w.sourceX,w.sourceY,w.sourceZ);
        BlockPos finished=new BlockPos(w.destX,w.destY,w.destZ);
        if(!level.hasChunkAt(potPos)||!level.hasChunkAt(source)||!level.hasChunkAt(finished)
            ||potPos.distSqr(source)>36||potPos.distSqr(finished)>36)return false;
        var land=KingdomTerritories.get(level.getServer());
        if(!land.usable(level,potPos,w.employer,"workstation")
            ||!land.usable(level,source,w.employer,"raw")
            ||!land.usable(level,finished,w.employer,"finished"))return false;
        if(!(level.getBlockEntity(source) instanceof Container raw)
            ||!(level.getBlockEntity(finished) instanceof Container dest)
            ||raw==dest)return false;
        BlockEntity pot=level.getBlockEntity(potPos);
        if(pot==null)return false;
        if(npc.distanceToSqr(potPos.getX()+.5,potPos.getY(),potPos.getZ()+.5)>6.25){
            npc.getNavigation().moveTo(potPos.getX()+.5,potPos.getY(),potPos.getZ()+.5,.75);
            return false;
        }
        try{
            Object tfcInventory=inventory.invoke(pot);
            if(!(tfcInventory instanceof IItemHandler machine)||machine.getSlots()<9)return false;
            KingdomTfcPotData data=KingdomTfcPotData.get(level.getServer());
            if(data.reserved(w.entityId,w.dimension,potPos.asLong()))return false;
            var batch=data.get(w.entityId);
            if(batch!=null){
                if(!batch.dimension().equals(w.dimension)||batch.position()!=potPos.asLong()){
                    data.remove(w.entityId);return false;
                }
                boolean collected=batch.outputs().contains(KingdomTfcSpecialPotWork.SOUP)
                    ||batch.outputs().contains(KingdomTfcSpecialPotWork.JAM)
                    ?KingdomTfcSpecialPotWork.collect(level,potPos,pot,w,batch,raw,dest)
                    :collect(level,w,batch,machine,dest);
                if(!collected){fuel(raw,machine);lightPot(pot,level,potPos);}
                return collected;
            }
            if(getOutput.invoke(pot)!=null || Boolean.TRUE.equals(hasStarted.invoke(pot)))
                return false; // Never take a player's special output or ongoing batch.
            for(int i=FIRST;i<=LAST;i++)if(!machine.getStackInSlot(i).isEmpty())
                return false; // A player or another worker already put items in this pot.
            // The pot must already be attached to real fluid capabilities.
            // A finite, one-item vessel from RAW may fill it; Kingdom never spawns water.
            if(((FluidStack)fluidInTank.invoke(tfcInventory,0)).isEmpty()
                    && KingdomTfcPotWater.fill(level,potPos,raw,pot)){
                return true; // TFC fluid changed; check recipes next worker tick.
            }
            Plan selected=findRecipe(level,tfcInventory,raw,dest);
            if(selected==null)return false;
            if(!fill(level,w,selected,raw,machine,potPos))return false;
            fuel(raw,machine);lightPot(pot,level,potPos);
            return true;
        }catch(ReflectiveOperationException|RuntimeException ex){
            compatible=false;
            System.err.println("[Kingdom] Native TFC pot adapter disabled: "+ex);
            return false;
        }
    }
}
