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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandlerModifiable;
import com.mojang.authlib.GameProfile;
import net.minecraftforge.common.util.FakePlayerFactory;
import ru.projectkingdom.enterprise.EnterpriseBuildingRegistration;
import ru.projectkingdom.enterprise.EnterpriseKind;

/**
 * Native TFC smithing driver. Never creates items directly: fuel, heat, molds,
 * and anvil actions are performed against the TFC BlockEntity inventories.
 * Reflection isolates TFC 1.20.x as an optional runtime mod dependency.
 */
public final class KingdomTfcSmithWork {
    private static final String FORGE="tfc:charcoal_forge";
    private static final String ANVIL="net.dries007.tfc.common.blockentities.AnvilBlockEntity";
    private static final String FORGE_BE="net.dries007.tfc.common.blockentities.CharcoalForgeBlockEntity";
    private static final String FORGE_FUEL="tfc:forge_fuel";
    private static final String TFC_FORGING="net.dries007.tfc.common.capabilities.forge.ForgingCapability";
    private static final String TFC_HEAT="net.dries007.tfc.common.capabilities.heat.HeatCapability";
    private static final int[] FORGE_STEPS={-3,-6,-9,-15,2,7,13,16};
    private KingdomTfcSmithWork(){}

    public static boolean inside(EnterpriseBuildingRegistration.Room room,BlockPos pos){
        return room!=null&&room.contains(pos);
    }
    /** Native TFC forge is a real workshop assignment, not a crafting-table recipe. */
    public static boolean isForge(ServerLevel level,KingdomNpcData.Worker w){
        if(level==null||w==null||!"smith".equals(w.job)||!w.hasWorkstation
                ||!level.dimension().location().toString().equals(w.dimension))return false;
        var pos=new BlockPos(w.workX,w.workY,w.workZ);
        return level.hasChunkAt(pos)&&FORGE.equals(id(level,pos));
    }
    private static String id(ServerLevel level,BlockPos p){
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).toString();
    }
    private static boolean near(Villager npc,BlockPos pos){
        return npc.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=9;
    }
    private static void walk(Villager npc,BlockPos pos){
        npc.getNavigation().moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,.7);
    }
    static boolean forgeFuel(ItemStack item){
        return !item.isEmpty()&&(item.is(net.minecraft.world.item.Items.CHARCOAL)
                ||item.is(net.minecraft.world.item.Items.COAL)
                ||item.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                        new ResourceLocation(FORGE_FUEL))));
    }
    /** TFC's inventory is protected in InventoryBlockEntity and may not expose automation. */
    private static IItemHandlerModifiable inventory(BlockEntity entity){
        if(entity==null||!ModList.get().isLoaded("tfc"))return null;
        for(Class<?> c=entity.getClass();c!=null;c=c.getSuperclass()){
            try{
                Field f=c.getDeclaredField("inventory");
                f.setAccessible(true);
                Object value=f.get(entity);
                return value instanceof IItemHandlerModifiable h?h:null;
            }catch(NoSuchFieldException ignored){}catch(ReflectiveOperationException|RuntimeException ex){return null;}
        }
        return null;
    }
    private static boolean nativeValid(BlockEntity machine,int slot,ItemStack item){
        try{
            return Boolean.TRUE.equals(machine.getClass().getMethod("isItemValid",int.class,ItemStack.class)
                    .invoke(machine,slot,item));
        }catch(ReflectiveOperationException|RuntimeException e){return false;}
    }
    private static int first(Container chest,java.util.function.Predicate<ItemStack> predicate){
        if(chest==null)return -1;
        for(int i=0;i<chest.getContainerSize();i++){
            ItemStack stack=chest.getItem(i);
            if(!stack.isEmpty()&&predicate.test(stack)&&chest.canTakeItem(chest,i,stack))return i;
        }
        return -1;
    }
    /** The physical written slot must match item, NBT, and exact count. */
    private static boolean exact(ItemStack actual,ItemStack expected){
        return actual.getCount()==expected.getCount()
            &&ItemStack.isSameItemSameTags(actual,expected);
    }
    /**
     * Read/verify machine destination before extracting raw inventory.
     * Container.getItem may return a COPY; shrinking that copy is not an extraction.
     * Rejected native setters must leave the raw stack untouched.
     */
    private static boolean fromChest(Container chest,BlockEntity machine,IItemHandlerModifiable dest,
                                     int slot,java.util.function.Predicate<ItemStack> acceptable){
        if(chest==null||machine==null||dest==null||slot<0||slot>=dest.getSlots()
                ||!dest.getStackInSlot(slot).isEmpty())return false;
        int source=first(chest,stack->acceptable.test(stack)&&nativeValid(machine,slot,stack));
        if(source<0)return false;
        ItemStack stock=chest.getItem(source).copy();
        if(stock.isEmpty())return false;
        ItemStack unit=stock.copy();unit.setCount(1);
        if(!nativeValid(machine,slot,unit))return false;
        // Stage destination first and verify it really contains precisely one item.
        dest.setStackInSlot(slot,unit.copy());
        if(!exact(dest.getStackInSlot(slot),unit)){
            if(!dest.getStackInSlot(slot).isEmpty())
                dest.setStackInSlot(slot,ItemStack.EMPTY);
            return false;
        }
        // Remove from the REAL chest inventory, not a copy returned by getItem.
        ItemStack removed=chest.removeItem(source,1);
        ItemStack remainder=stock.copy();remainder.shrink(1);
        if(!exact(removed,unit)||!exact(chest.getItem(source),remainder)){
            chest.setItem(source,stock.copy());
            dest.setStackInSlot(slot,ItemStack.EMPTY);
            chest.setChanged();machine.setChanged();
            return false;
        }
        chest.setChanged();machine.setChanged();
        return true;
    }

    /**
     * Native inventory transfer, verified on both physical endpoints.
     * Stacks and capability-backed NBT are never re-created from item IDs.
     */
    private static boolean moveNative(BlockEntity source,IItemHandlerModifiable from,int fromSlot,
                                      BlockEntity target,IItemHandlerModifiable to,int toSlot){
        if(source==null||target==null||from==null||to==null
                ||fromSlot<0||toSlot<0||fromSlot>=from.getSlots()||toSlot>=to.getSlots()
                ||!to.getStackInSlot(toSlot).isEmpty())return false;
        ItemStack moving=from.getStackInSlot(fromSlot).copy();
        if(moving.isEmpty()||!nativeValid(target,toSlot,moving))return false;
        to.setStackInSlot(toSlot,moving.copy());
        if(!exact(to.getStackInSlot(toSlot),moving)){
            if(!to.getStackInSlot(toSlot).isEmpty())
                to.setStackInSlot(toSlot,ItemStack.EMPTY);
            return false;
        }
        from.setStackInSlot(fromSlot,ItemStack.EMPTY);
        if(!from.getStackInSlot(fromSlot).isEmpty()){
            to.setStackInSlot(toSlot,ItemStack.EMPTY);
            return false;
        }
        source.setChanged();target.setChanged();
        return true;
    }

    /**
     * Keep PR #74's item/container stack-size guards. A caller may clear
     * an anvil only after the destination really contains the whole stack.
     */
    private static boolean intoChest(Container chest,ItemStack carried){
        if(chest==null||carried.isEmpty())return false;
        for(int i=0;i<chest.getContainerSize();i++){
            ItemStack existing=chest.getItem(i).copy();
            int limit=Math.min(chest.getMaxStackSize(),carried.getMaxStackSize());
            ItemStack desired;
            if(existing.isEmpty()&&chest.canPlaceItem(i,carried)
                    &&carried.getCount()<=limit){
                desired=carried.copy();
            }else if(!existing.isEmpty()&&ItemStack.isSameItemSameTags(existing,carried)
                    &&chest.canPlaceItem(i,carried)
                    &&existing.getCount()<=Math.min(chest.getMaxStackSize(),
                        existing.getMaxStackSize())-carried.getCount()){
                desired=existing.copy();
                desired.grow(carried.getCount());
            }else continue;
            // setItem is not guaranteed to accept the requested count. Its
            // implementation can ignore the write or clamp it.
            chest.setItem(i,desired.copy());
            if(exact(chest.getItem(i),desired)){
                chest.setChanged();
                return true;
            }
            // A partially accepted write must not count as a delivery.
            if(!exact(chest.getItem(i),existing))
                chest.setItem(i,existing.copy());
            return false;
        }
        return false;
    }
    /**
     * Verified anvil -> warehouse transfer. If the native anvil refuses
     * to clear, restore the warehouse slot before ever crediting delivery.
     */
    private static boolean deliverNative(Container warehouse,BlockEntity anvil,
                                         IItemHandlerModifiable anvilSlots){
        if(warehouse==null||anvil==null||anvilSlots==null||anvilSlots.getSlots()<1)return false;
        ItemStack ready=anvilSlots.getStackInSlot(0).copy();
        if(ready.isEmpty())return false;
        for(int slot=0;slot<warehouse.getContainerSize();slot++){
            ItemStack previous=warehouse.getItem(slot).copy();
            // This helper only changes one target slot when it succeeds.
            if(previous.isEmpty()){
                if(!warehouse.canPlaceItem(slot,ready)
                    ||ready.getCount()>Math.min(warehouse.getMaxStackSize(),ready.getMaxStackSize()))
                    continue;
            }else if(!warehouse.canPlaceItem(slot,ready)
                    ||!ItemStack.isSameItemSameTags(previous,ready)
                    ||ready.getCount()>Math.min(warehouse.getMaxStackSize(),
                        previous.getMaxStackSize())-previous.getCount())continue;
            ItemStack desired=previous.isEmpty()?ready.copy():previous.copy();
            if(!previous.isEmpty())desired.grow(ready.getCount());
            warehouse.setItem(slot,desired.copy());
            if(!exact(warehouse.getItem(slot),desired)){
                if(!exact(warehouse.getItem(slot),previous))
                    warehouse.setItem(slot,previous.copy());
                return false;
            }
            anvilSlots.setStackInSlot(0,ItemStack.EMPTY);
            if(!anvilSlots.getStackInSlot(0).isEmpty()){
                warehouse.setItem(slot,previous.copy());
                warehouse.setChanged();
                return false;
            }
            warehouse.setChanged();anvil.setChanged();
            return true;
        }
        return false;
    }
    /** A filled TFC mold is removed only after the ACTUAL fluid has cooled.
     * Empty molds never count as output, and no casting recipe is fabricated.
     */
    private static boolean cooledFilledMold(ItemStack stack){
        if(stack.isEmpty())return false;
        var handler=stack.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER_ITEM)
                .orElse(null);
        if(handler==null)return false;
        boolean metal=false;
        for(int tank=0;tank<handler.getTanks();tank++)
            if(!handler.getFluidInTank(tank).isEmpty())metal=true;
        if(!metal)return false;
        try{
            Object value=Class.forName(TFC_HEAT).getMethod("getTemperature",ItemStack.class)
                    .invoke(null,stack);
            return value instanceof Number t&&t.floatValue()<150.0f;
        }catch(ReflectiveOperationException|LinkageError|RuntimeException ex){return false;}
    }
    private static boolean hotEnough(ItemStack stack){
        try{
            Class<?> type=Class.forName(TFC_HEAT);
            Object heat=type.getMethod("get",ItemStack.class).invoke(null,stack);
            return heat!=null&&Boolean.TRUE.equals(heat.getClass().getMethod("canWork").invoke(heat));
        }catch(ReflectiveOperationException|LinkageError|RuntimeException e){return false;}
    }
    private static Object forgeCapability(ItemStack stack){
        try{return Class.forName(TFC_FORGING).getMethod("get",ItemStack.class).invoke(null,stack);}
        catch(ReflectiveOperationException|LinkageError|RuntimeException e){return null;}
    }
    private static boolean isSmithMachine(BlockEntity entity,String type){
        return entity!=null&&entity.getClass().getName().equals(type);
    }
    private static BlockPos findAnvil(ServerLevel level,EnterpriseBuildingRegistration.Room room,BlockPos forge){
        if(room==null)return null;
        for(int dx=-8;dx<=8;dx++)for(int dy=-3;dy<=3;dy++)for(int dz=-8;dz<=8;dz++){
            BlockPos at=forge.offset(dx,dy,dz);
            if(!inside(room,at)||!level.hasChunkAt(at))continue;
            if(isSmithMachine(level.getBlockEntity(at),ANVIL))return at.immutable();
        }
        return null;
    }
    /** A recipe with more than one valid output needs an explicit player-selected anvil plan. */
    private static boolean selectUniqueAnvilRecipe(ServerLevel level,BlockEntity anvil,
                                                     ItemStack input,KingdomNpcData.Worker w){
        try{
            if(forgeCapability(input)==null)return false;
            Class<?> recipe=Class.forName("net.dries007.tfc.common.recipes.AnvilRecipe");
            int tier=(int)anvil.getClass().getMethod("getTier").invoke(anvil);
            Object collection=recipe.getMethod("getAll",net.minecraft.world.level.Level.class,
                    ItemStack.class,int.class).invoke(null,level,input,tier);
            if(!(collection instanceof List<?> candidates))return false;
            Object selected=selectedRecipe(candidates,w.smithRecipeId);
            if(selected==null)return false;
            anvil.getClass().getMethod("chooseRecipe",recipe).invoke(anvil,selected);
            return true;
        }catch(ReflectiveOperationException|LinkageError|RuntimeException e){return false;}
    }

    /**
     * TFC AnvilRecipe implements vanilla Recipe via ISimpleRecipe.
     * Use Minecraft's mapped interface invocation instead of reflection on
     * AnvilRecipe#getId: in production Forge/TFC 3.2.26 it is named m_6423_.
     */
    private static String recipeId(Object candidate){
        return candidate instanceof net.minecraft.world.item.crafting.Recipe<?> recipe
            ?recipe.getId().toString():null;
    }

    private static Object selectedRecipe(List<?> options,String id){
        if(options==null||options.isEmpty())return null;
        if(id==null||id.isBlank())return options.size()==1?options.get(0):null;
        for(Object option:options)
            if(id.equals(recipeId(option)))return option;
        return null;
    }

    /** Real native recipes visible for this worker's current registered anvil and raw stock. */
    public record RecipeOption(String recipeId,String inputId) {}
    public static List<RecipeOption> availableRecipes(ServerLevel level,KingdomNpcData.Worker w){
        if(!ModList.get().isLoaded("tfc")||!KingdomSmithySite.inspect(level,w).ready()
                ||!w.hasSource||!w.hasDestination)return List.of();
        BlockPos input=new BlockPos(w.sourceX,w.sourceY,w.sourceZ);
        BlockPos forgePos=new BlockPos(w.workX,w.workY,w.workZ);
        if(!level.hasChunkAt(input)||!KingdomTerritories.get(level.getServer())
                .usable(level,input,w.employer,"raw"))return List.of();
        if(!(level.getBlockEntity(input) instanceof Container raw))return List.of();
        UUID firm;
        try{firm=UUID.fromString(w.workplaceId);}catch(IllegalArgumentException ex){return List.of();}
        var room=EnterpriseBuildingRegistration.get(level.getServer()).room(firm);
        BlockPos anvilPos=findAnvil(level,room,forgePos);
        if(anvilPos==null||!KingdomTerritories.get(level.getServer()).canUse(level,anvilPos,w.employer))
            return List.of();
        var anvil=level.getBlockEntity(anvilPos);
        if(inventory(anvil)==null)return List.of();
        try{
            Class<?> type=Class.forName("net.dries007.tfc.common.recipes.AnvilRecipe");
            int tier=((Number)anvil.getClass().getMethod("getTier").invoke(anvil)).intValue();
            Method all=type.getMethod("getAll",net.minecraft.world.level.Level.class,ItemStack.class,int.class);
            var unique=new TreeMap<String,RecipeOption>();
            for(int i=0;i<raw.getContainerSize()&&unique.size()<128;i++){
                ItemStack stack=raw.getItem(i);
                if(stack.isEmpty()||!raw.canTakeItem(raw,i,stack)
                        ||forgeCapability(stack)==null
                        ||!nativeValid(level.getBlockEntity(forgePos),5,stack))continue;
                Object rows=all.invoke(null,level,stack,tier);
                if(!(rows instanceof List<?> candidates))continue;
                String inputId=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                for(Object candidate:candidates){
                    String recipeId=recipeId(candidate);
                    if(recipeId==null||!recipeId.startsWith("tfc:"))continue; // real TFC recipes only
                    unique.putIfAbsent(recipeId+"|"+inputId,new RecipeOption(recipeId,inputId));
                }
            }
            return List.copyOf(unique.values());
        }catch(ReflectiveOperationException|LinkageError|RuntimeException ex){
            return List.of(); // TFC unavailable or changed API: never synthesize a recipe
        }
    }

    /** A free native anvil input slot is required before accepting a new order. */
    public static boolean idleAnvil(ServerLevel level,KingdomNpcData.Worker w){
        if(!KingdomSmithySite.inspect(level,w).ready())return false;
        UUID company;
        try{company=UUID.fromString(w.workplaceId);}catch(IllegalArgumentException ex){return false;}
        var room=EnterpriseBuildingRegistration.get(level.getServer()).room(company);
        BlockPos anvil=findAnvil(level,room,new BlockPos(w.workX,w.workY,w.workZ));
        if(anvil==null||!KingdomTerritories.get(level.getServer()).canUse(level,anvil,w.employer))
            return false;
        var slots=inventory(level.getBlockEntity(anvil));
        return slots!=null&&slots.getSlots()>=4&&slots.getStackInSlot(0).isEmpty()
                &&!w.smithOutputReady;
    }

    /** Left click Recipe in the NPC menu cycles valid native TFC anvil outputs. */
    public static String cycleRecipe(net.minecraft.server.level.ServerPlayer owner,KingdomNpcData.Worker w){
        if(owner==null||w==null||!"smith".equals(w.job)||!owner.getUUID().equals(w.employer))
            return "Сначала найми кузнеца и выбери его предприятие.";
        UUID assigned;
        try{assigned=UUID.fromString(w.workplaceId);}catch(IllegalArgumentException ex){
            return "Не привязана кузница.";
        }
        var activeOrder=KingdomSmithyOrdersData.get(owner.getServer()).order(assigned);
        if(activeOrder!=null&&!activeOrder.finished())
            return "Кузнец: рецепт закреплён действующим заказом; меняй его через блок кузницы после завершения.";
        var site=KingdomSmithySite.inspect(owner.serverLevel(),w);
        if(!site.ready())return site.message();
        if(!w.hasSource)return "Кузнец: привяжи склад сырья.";
        var server=owner.getServer();var firms=KingdomEnterpriseData.get(server);
        UUID firm;
        try{firm=UUID.fromString(w.workplaceId);}catch(IllegalArgumentException ex){return "Не привязана кузница.";}
        if(!firms.mayManage(owner.getUUID(),firm)||firms.kind(firm)!=EnterpriseKind.SMITHY)
            return "Нет прав на эту кузницу.";
        var room=EnterpriseBuildingRegistration.get(server).room(firm);
        var level=owner.serverLevel();var pos=new BlockPos(w.workX,w.workY,w.workZ);
        BlockPos anvilPos=findAnvil(level,room,pos);
        if(anvilPos==null)return "Кузнец: в зарегистрированном здании не найдена наковальня TFC.";
        if(!KingdomTerritories.get(server).canUse(level,anvilPos,w.employer))
            return "Кузнец: нет прав на участок с наковальней.";
        BlockPos rawPos=new BlockPos(w.sourceX,w.sourceY,w.sourceZ);
        if(!level.hasChunkAt(rawPos)
                ||!KingdomTerritories.get(server).usable(level,rawPos,w.employer,"raw")
                ||!(level.getBlockEntity(rawPos) instanceof Container raw))
            return "Кузнец: склад сырья не загружен, не привязан или недоступен.";
        BlockEntity anvil=level.getBlockEntity(anvilPos);
        var inv=inventory(anvil);
        if(inv==null)return "Инвентарь наковальни TFC недоступен.";
        ItemStack sample=inv.getStackInSlot(0);
        try{
            Class<?> recipe=Class.forName("net.dries007.tfc.common.recipes.AnvilRecipe");
            int tier=(int)anvil.getClass().getMethod("getTier").invoke(anvil);
            Method getAll=recipe.getMethod("getAll",net.minecraft.world.level.Level.class,ItemStack.class,int.class);
            if(sample.isEmpty()){
                for(int i=0;i<raw.getContainerSize();i++){
                    ItemStack stack=raw.getItem(i);
                    if(stack.isEmpty())continue;
                    Object choices=getAll.invoke(null,level,stack,tier);
                    if(choices instanceof List<?> list&&!list.isEmpty()){sample=stack;break;}
                }
            }
            if(sample.isEmpty())return "В складе нет пригодного для ковки металла.";
            Object choices=getAll.invoke(null,level,sample,tier);
            if(!(choices instanceof List<?> list)||list.isEmpty())return "Нет подходящего TFC-рецепта.";
            var ids=new ArrayList<String>();
            for(Object candidate:list)
                ids.add(recipeId(candidate));
            Collections.sort(ids);
            int old=ids.indexOf(w.smithRecipeId);
            w.smithRecipeId=ids.get((old+1)%ids.size());
            KingdomNpcData.get(server).setDirty();
            return "Выбран настоящий рецепт: "+w.smithRecipeId
                    +" ("+(ids.indexOf(w.smithRecipeId)+1)+"/"+ids.size()+").";
        }catch(ReflectiveOperationException|LinkageError|RuntimeException ex){
            return "Не удалось прочитать рецепты TFC.";
        }
    }
    /** Returns the valid next native step (0..7), or no step for unsafe/unplanned work. */
    public static int nextStep(int target,int work,int last,int second,int third,String[] rules){
        if(work<0||work>150||target<0||target>150||rules==null||rules.length==0)return -1;
        record State(int value,int a,int b,int c){}
        record Previous(State state,int step){}
        State start=new State(work,last,second,third);
        var open=new ArrayDeque<State>();
        var seen=new HashMap<State,Previous>();
        open.add(start);seen.put(start,null);
        State goal=null;
        while(!open.isEmpty()&&seen.size()<85000){
            State cur=open.removeFirst();
            if(cur.value==target&&rulesMatch(rules,cur.a,cur.b,cur.c)){goal=cur;break;}
            for(int step=0;step<FORGE_STEPS.length;step++){
                int v=cur.value+FORGE_STEPS[step];
                if(v<0||v>150)continue;
                var next=new State(v,step,cur.a,cur.b);
                if(seen.containsKey(next))continue;
                seen.put(next,new Previous(cur,step));
                open.add(next);
            }
        }
        if(goal==null||goal.equals(start))return -1;
        int result=-1;
        for(State node=goal;!node.equals(start);){
            Previous prior=seen.get(node);
            if(prior==null)return -1;
            result=prior.step();
            node=prior.state();
        }
        return result;
    }
    private static boolean rulesMatch(String[] rules,int last,int second,int third){
        for(String value:rules){
            if(value==null)return false;
            String rule=value.toUpperCase(java.util.Locale.ROOT);
            int marker=rule.indexOf('_');
            if(marker<1)return false;
            String action=rule.substring(0,marker);
            String order=rule.substring(marker+1);
            boolean l=stepMatches(action,last),s=stepMatches(action,second),t=stepMatches(action,third);
            boolean matches=switch(order){
                case "LAST"->l;case "SECOND_LAST"->s;case "THIRD_LAST"->t;
                case "NOT_LAST"->s||t;case "ANY"->l||s||t;default->false;
            };
            if(!matches)return false;
        }
        return true;
    }
    private static boolean stepMatches(String type,int step){
        if(step<0||step>=FORGE_STEPS.length)return false;
        if("HIT".equals(type))return step<=2;
        return switch(type){
            case "DRAW"->step==3;case "PUNCH"->step==4;case "BEND"->step==5;
            case "UPSET"->step==6;case "SHRINK"->step==7;default->false;
        };
    }

    private static boolean anvilWork(ServerLevel level,Villager npc,BlockEntity anvil,
                                     IItemHandlerModifiable slots,KingdomNpcData.Worker w,
                                     KingdomSmithyOrdersData.Order order){
        ItemStack metal=slots.getStackInSlot(0);
        if(metal.isEmpty()||slots.getSlots()<4||!order.input().equals(
            BuiltInRegistries.ITEM.getKey(metal.getItem()).toString()))return false;
        var hammer=slots.getStackInSlot(2);
        if(hammer.isEmpty()||!hammer.is(net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ITEM,new ResourceLocation("tfc","hammers"))))return false;
        Object forging=forgeCapability(metal);
        if(forging==null||!hotEnough(metal))return false;
        try {
            Class<?> forgeStep=Class.forName("net.dries007.tfc.common.capabilities.forge.ForgeStep");
            Object recipe=forging.getClass().getMethod("getRecipe",
                    net.minecraft.world.level.Level.class).invoke(forging,level);
            if(recipe==null&&!selectUniqueAnvilRecipe(level,anvil,metal,w))return false;
            recipe=forging.getClass().getMethod("getRecipe",net.minecraft.world.level.Level.class)
                    .invoke(forging,level);
            if(recipe==null||!order.recipe().equals(recipeId(recipe)))return false;
            int target=(int)forging.getClass().getMethod("getWorkTarget").invoke(forging);
            int work=(int)forging.getClass().getMethod("getWork").invoke(forging);
            Object stages=forging.getClass().getMethod("getSteps").invoke(forging);
            Class<?> stagesType=stages.getClass();
            int[] recent=new int[3];int i=0;
            for(String name:new String[]{"last","secondLast","thirdLast"}){
                Object step=stagesType.getMethod(name).invoke(stages);
                recent[i++]=step==null?-1:((Enum<?>)step).ordinal();
            }
            Object[] rules=(Object[])recipe.getClass().getMethod("getRules").invoke(recipe);
            String[] ruleNames=Arrays.stream(rules).map(Object::toString).toArray(String[]::new);
            int next=nextStep(target,work,recent[0],recent[1],recent[2],ruleNames);
            if(next<0)return false; // Never overwork an item without a valid solution.
            Object step=forgeStep.getEnumConstants()[next];
            var fake=FakePlayerFactory.get(level,new GameProfile(
                    UUID.nameUUIDFromBytes("kingdom-tfc-smith".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    "[KingdomSmith]"));
            ItemStack before=slots.getStackInSlot(0).copy();
            Object outcome=anvil.getClass().getMethod("work",net.minecraft.server.level.ServerPlayer.class,
                    forgeStep).invoke(anvil,fake,step);
            ItemStack after=slots.getStackInSlot(0);
            if(!after.isEmpty()&&!ItemStack.isSameItem(before,after)){
                String outputId=BuiltInRegistries.ITEM.getKey(after.getItem()).toString();
                boolean recorded=KingdomSmithyOrdersData.get(level.getServer()).ready(
                    order.enterprise(),w.entityId,outputId,after.getCount());
                if(!recorded){
                    KingdomSmithyOrdersData.get(level.getServer()).note(order.enterprise(),w.entityId,
                        "TFC создал изделие, но запись заказа заблокирована; предмет остаётся в наковальне.");
                    return false; // Never fabricate a second copy or count a non-delivered item.
                }
                w.smithOutputReady=true;KingdomNpcData.get(level.getServer()).setDirty();
            }
            anvil.setChanged();
            return outcome==net.minecraft.world.InteractionResult.SUCCESS
                    ||outcome==net.minecraft.world.InteractionResult.CONSUME;
        }catch(ReflectiveOperationException|LinkageError|RuntimeException ex){
            return false;
        }
    }
    public static boolean tick(ServerLevel level,Villager npc,KingdomNpcData.Worker w){
        if(w==null||!"smith".equals(w.job)||w.employer==null
                ||!ModList.get().isLoaded("tfc"))return false;
        var server=level.getServer();
        var enterprise=KingdomEnterpriseData.get(server);
        UUID company;
        try{company=UUID.fromString(w.workplaceId);}catch(IllegalArgumentException e){return false;}
        var orders=KingdomSmithyOrdersData.get(server);
        var order=orders.order(company);
        if(order==null||!order.actionable()||!order.worker().equals(w.entityId))return false;
        if(w.contractPaused||!NpcLife.ableToWork(w.ageYears,w.hunger,w.energy,w.contractPaused)){
            orders.note(company,w.entityId,w.contractPaused
                ?"Работа остановлена: задолженность по зарплате."
                :"Работа остановлена: нужен отдых, питание или безопасность NPC.");
            return false;
        }
        if(!KingdomNpcDuty.autonomousOrder(w.order)
                ||!"CONTINUE_ASSIGNED_JOB".equals(w.autonomousIntent)){
            orders.note(company,w.entityId,"Ожидание рабочего дня или приказа NPC «Работать».");
            return false;
        }
        if(!w.hasWorkstation||!w.hasSource||!w.hasDestination){
            orders.note(company,w.entityId,"Требуется рабочий горн и оба привязанных склада.");
            return false;
        }
        // The enterprise order, not a stale NPC menu selection, is authoritative.
        if(!order.recipe().equals(w.smithRecipeId)){
            w.smithRecipeId=order.recipe();KingdomNpcData.get(server).setDirty();
        }
        var firm=enterprise.find(company);
        var binding=enterprise.core(company);
        var contract=KingdomEnterprisePayrollData.get(server).contract(company,w.entityId);
        if(firm==null||!firm.owner().equals(w.employer)||enterprise.kind(company)!=EnterpriseKind.SMITHY
                ||binding==null||!binding.active()
                ||contract==null||!contract.active()
                ||!w.dimension.equals(level.dimension().location().toString())){
            orders.note(company,w.entityId,"Ожидание: трудовой договор, предприятие или размерность недействительны.");
            return false;
        }
        var site=KingdomSmithySite.inspect(level,w);
        if(!site.ready()){
            orders.note(company,w.entityId,site.message());return false;
        }
        var building=EnterpriseBuildingRegistration.get(server).room(company);
        BlockPos forgePos=new BlockPos(w.workX,w.workY,w.workZ);
        BlockEntity forge=level.getBlockEntity(forgePos);
        BlockPos fromPos=new BlockPos(w.sourceX,w.sourceY,w.sourceZ);
        BlockPos toPos=new BlockPos(w.destX,w.destY,w.destZ);
        var land=KingdomTerritories.get(server);
        if(!level.hasChunkAt(fromPos)||!level.hasChunkAt(toPos)
                ||!land.usable(level,fromPos,w.employer,"raw")
                ||!land.usable(level,toPos,w.employer,"finished")
                ||!(level.getBlockEntity(fromPos) instanceof Container raw)
                ||!(level.getBlockEntity(toPos) instanceof Container finished)){
            orders.note(company,w.entityId,"Нет доступа к сырьевому/готовому складу или чанк выгружен.");
            return false;
        }
        BlockPos anvilPos=findAnvil(level,building,forgePos);
        if(anvilPos==null||!land.canUse(level,anvilPos,w.employer)){
            orders.note(company,w.entityId,"Нужна настоящая наковальня TFC на своей земле.");
            return false;
        }
        BlockEntity anvil=level.getBlockEntity(anvilPos);
        IItemHandlerModifiable anvilInv=inventory(anvil);
        if(anvilInv==null||anvilInv.getSlots()<4){
            orders.note(company,w.entityId,"Недоступен инвентарь наковальни TFC.");
            return false;
        }
        // 5: a REAL TFC hammer is placed in the anvil's own hammer slot by player;
        // it is not demanded from a warehouse chest.
        if(!anvilInv.getStackInSlot(0).isEmpty()){
            if(!near(npc,anvilPos)){
                orders.note(company,w.entityId,"Кузнец идёт к настоящей наковальне.");
                walk(npc,anvilPos);return false;
            }
            ItemStack inAnvil=anvilInv.getStackInSlot(0);
            if(order.phase()==KingdomSmithyOrdersData.Phase.READY){
                String actual=BuiltInRegistries.ITEM.getKey(inAnvil.getItem()).toString();
                int count=inAnvil.getCount();
                if(!actual.equals(order.pendingItem())||count!=order.pendingCount()){
                    orders.note(company,w.entityId,
                        "Готовое изделие в наковальне изменено: нужна ручная проверка, перенос остановлен.");
                    return false;
                }
                // Both destination and source must confirm the physical transfer
                // BEFORE the order receives credit. A refused warehouse setter
                // or refused anvil extraction is never a successful delivery.
                if(!deliverNative(finished,anvil,anvilInv)){
                    orders.note(company,w.entityId,
                        "Доставка не подтверждена: проверь свободный склад и наковальню TFC.");
                    return false;
                }
                if(!orders.delivered(company,w.entityId,actual,count)){
                    orders.note(company,w.entityId,
                        "Передача состоялась, но учёт заказа отклонён: остановить до сверки.");
                    return false;
                }
                w.smithOutputReady=false;KingdomNpcData.get(server).setDirty();
                return true;
            }
            if(w.smithOutputReady){
                orders.note(company,w.entityId,"Изделие без подтверждённого заказа: требуется проверка владельца.");
                return false;
            }
            if(!order.input().equals(BuiltInRegistries.ITEM.getKey(inAnvil.getItem()).toString())){
                orders.note(company,w.entityId,"Наковальня занята другим материалом; освободите её вручную.");
                return false;
            }
            // TFC metal cools on the anvil. Move the SAME partially worked
            // stack back to a free native forge input slot; its forging NBT is
            // preserved, and no new raw material is consumed while reheating.
            if(!hotEnough(inAnvil)){
                var heatingSlots=inventory(forge);
                if(heatingSlots==null||heatingSlots.getSlots()<14){
                    orders.note(company,w.entityId,"Нагрев невозможен: горн TFC недоступен.");
                    return false;
                }
                for(int slot=5;slot<=9;slot++){
                    if(!heatingSlots.getStackInSlot(slot).isEmpty()
                            ||!nativeValid(forge,slot,inAnvil))continue;
                    if(!moveNative(anvil,anvilInv,0,forge,heatingSlots,slot)){
                        orders.note(company,w.entityId,
                            "Нативная передача на повторный нагрев не подтверждена.");
                        return false;
                    }
                    orders.working(company,w.entityId);
                    orders.note(company,w.entityId,
                        "Остывший металл возвращён в настоящий горн TFC для повторного нагрева.");
                    return true;
                }
                orders.note(company,w.entityId,
                    "Металл остыл, но в горне нет свободного слота для нагрева.");
                return false;
            }
            if(anvilWork(level,npc,anvil,anvilInv,w,order)){
                orders.working(company,w.entityId);return true;
            }
            orders.note(company,w.entityId,"Ожидание молота TFC, выбора рецепта или выполнения правил ковки.");
            return false;
        }
        if(order.phase()==KingdomSmithyOrdersData.Phase.READY||w.smithOutputReady){
            orders.note(company,w.entityId,"В наковальне нет ожидаемого изделия: задание остановлено.");
            return false;
        }
        if(!near(npc,forgePos)){walk(npc,forgePos);return false;}
        IItemHandlerModifiable forgeInv=inventory(forge);
        if(forgeInv==null||forgeInv.getSlots()<14){
            orders.note(company,w.entityId,"Горн TFC недоступен.");
            return false;
        }
        // Casting is a separate, unsupported production mode. This anvil order
        // must never count cooled molds or unrelated objects as its output.
        // 1: fuel is consumed from the actual raw warehouse, by native TFC forge.
        if(fromChest(raw,forge,forgeInv,0,KingdomTfcSmithWork::forgeFuel)){
            orders.note(company,w.entityId,"Топливо TFC доставлено в горн.");
            KingdomNpcData.get(server).setDirty();return true;
        }
        // 2: warm native forge, using its real light/consumeFuel method.
        try{
            if(!Boolean.TRUE.equals(forge.getClass().getMethod("light",
                    net.minecraft.world.level.block.state.BlockState.class)
                    .invoke(forge,level.getBlockState(forgePos)))){
                orders.note(company,w.entityId,"Горн TFC не разожжён: нужен настоящий топливный ресурс.");
                return false;
            }
        }catch(ReflectiveOperationException|RuntimeException ex){
            orders.note(company,w.entityId,"Горн не разожжён: нет действительного топлива или условия TFC.");
            return false;
        }
        // Anvil orders DO NOT auto-handle molds, ingots or smelting vessels.
        // The only raw input is an already real, TFC-forge-compatible metal
        // for the explicitly selected native anvil recipe.
        // Keep at most one matching workpiece in flight. Prevents a one-piece
        // order from consuming several expensive ingots while the first heats.
        boolean alreadyHeating=false;
        for(int slot=5;slot<=9;slot++){
            ItemStack heating=forgeInv.getStackInSlot(slot);
            if(!heating.isEmpty()&&order.input().equals(
                    BuiltInRegistries.ITEM.getKey(heating.getItem()).toString()))
                alreadyHeating=true;
        }
        if(!alreadyHeating){
            for(int slot=5;slot<=9;slot++)if(fromChest(raw,forge,forgeInv,slot,
                    stack->order.input().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                        &&forgeCapability(stack)!=null
                        &&selectUniqueAnvilRecipePreflight(level,anvil,stack,w))){
                orders.working(company,w.entityId);
                orders.note(company,w.entityId,"Настоящая TFC-заготовка помещена в горн для нагрева.");
                return true;
            }
        }
        if(anvilInv.getStackInSlot(2).isEmpty()){
            orders.note(company,w.entityId,"Положите настоящий молот TFC в слот наковальни.");
            return false;
        }
        for(int slot=5;slot<=9;slot++){
            ItemStack heated=forgeInv.getStackInSlot(slot);
            if(heated.isEmpty()||!hotEnough(heated)||forgeCapability(heated)==null
                ||!order.input().equals(BuiltInRegistries.ITEM.getKey(heated.getItem()).toString()))continue;
            if(!nativeValid(anvil,0,heated)
                ||!selectUniqueAnvilRecipePreflight(level,anvil,heated,w))continue;
            // Move the SAME real hot stack to the anvil, with no copy left in forge.
            if(!moveNative(forge,forgeInv,slot,anvil,anvilInv,0)){
                orders.note(company,w.entityId,
                    "Нативная передача горячего металла в наковальню не подтверждена.");
                return false;
            }
            orders.working(company,w.entityId);
            orders.note(company,w.entityId,"Нагретый металл передан на настоящую TFC-наковальню.");
            return true;
        }
        orders.note(company,w.entityId,
            "Ожидание: подходящее сырьё и топливо TFC, нагрев или доступная наковальня.");
        return false;
    }
    private static boolean selectUniqueAnvilRecipePreflight(ServerLevel level,BlockEntity anvil,
                                                               ItemStack stack,KingdomNpcData.Worker w){
        try{
            Class<?> recipe=Class.forName("net.dries007.tfc.common.recipes.AnvilRecipe");
            int tier=(int)anvil.getClass().getMethod("getTier").invoke(anvil);
            Object rows=recipe.getMethod("getAll",net.minecraft.world.level.Level.class,
                    ItemStack.class,int.class).invoke(null,level,stack,tier);
            return rows instanceof List<?> list&&selectedRecipe(list,w.smithRecipeId)!=null;
        }catch(ReflectiveOperationException|LinkageError|RuntimeException ex){return false;}
    }
}
