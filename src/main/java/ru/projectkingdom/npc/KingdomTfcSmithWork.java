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
        return room!=null&&pos!=null&&pos.getX()>room.minX()&&pos.getX()<room.maxX()
                &&pos.getY()>=room.minY()&&pos.getY()<=room.maxY()
                &&pos.getZ()>room.minZ()&&pos.getZ()<room.maxZ();
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
    /**
     * One genuine item is moved only after native TFC validity and slot checks.
     * No recipe products, metal or money can be fabricated through this method.
     */
    private static boolean fromChest(Container chest,BlockEntity machine,IItemHandlerModifiable dest,
                                     int slot,java.util.function.Predicate<ItemStack> acceptable){
        if(dest==null||slot>=dest.getSlots()||!dest.getStackInSlot(slot).isEmpty())return false;
        int source=first(chest,stack->acceptable.test(stack)&&nativeValid(machine,slot,stack));
        if(source<0)return false;
        ItemStack stock=chest.getItem(source);
        ItemStack unit=stock.copy();unit.setCount(1);
        if(!nativeValid(machine,slot,unit))return false;
        dest.setStackInSlot(slot,unit);
        stock.shrink(1);chest.setChanged();machine.setChanged();
        return true;
    }
    private static boolean intoChest(Container chest,ItemStack carried){
        if(chest==null||carried.isEmpty())return false;
        for(int i=0;i<chest.getContainerSize();i++){
            ItemStack existing=chest.getItem(i);
            if(existing.isEmpty()&&chest.canPlaceItem(i,carried)){
                chest.setItem(i,carried.copy());chest.setChanged();return true;
            }
            if(!existing.isEmpty()&&ItemStack.isSameItemSameTags(existing,carried)
                    &&chest.canPlaceItem(i,carried)
                    &&existing.getCount()<=Math.min(chest.getMaxStackSize(),existing.getMaxStackSize())-carried.getCount()){
                existing.grow(carried.getCount());chest.setChanged();return true;
            }
        }
        return false;
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

    private static Object selectedRecipe(List<?> options,String id){
        if(options==null||options.isEmpty())return null;
        if(id==null||id.isBlank())return options.size()==1?options.get(0):null;
        for(Object option:options)try{
            if(id.equals(option.getClass().getMethod("getId").invoke(option).toString()))
                return option;
        }catch(ReflectiveOperationException ignored){}
        return null;
    }

    /** Left click Recipe in the NPC menu cycles valid native TFC anvil outputs. */
    public static String cycleRecipe(net.minecraft.server.level.ServerPlayer owner,KingdomNpcData.Worker w){
        if(owner==null||w==null||!"smith".equals(w.job)||!owner.getUUID().equals(w.employer)
                ||!w.hasWorkstation||!w.hasSource)return "Сначала найми кузнеца и настрой его рабочее место.";
        var server=owner.getServer();var firms=KingdomEnterpriseData.get(server);
        UUID firm;
        try{firm=UUID.fromString(w.workplaceId);}catch(IllegalArgumentException ex){return "Не привязана кузница.";}
        if(!firms.mayManage(owner.getUUID(),firm)||firms.kind(firm)!=EnterpriseKind.SMITHY)
            return "Нет прав на эту кузницу.";
        var room=EnterpriseBuildingRegistration.get(server).room(firm);
        var level=owner.serverLevel();var pos=new BlockPos(w.workX,w.workY,w.workZ);
        if(room==null||!room.dimension().equals(w.dimension)||!inside(room,pos))
            return "Зарегистрируй здание кузницы.";
        BlockPos anvilPos=findAnvil(level,room,pos);
        BlockPos rawPos=new BlockPos(w.sourceX,w.sourceY,w.sourceZ);
        if(anvilPos==null||!level.hasChunkAt(rawPos)
                ||!KingdomTerritories.get(server).usable(level,rawPos,w.employer,"raw")
                ||!(level.getBlockEntity(rawPos) instanceof Container raw))
            return "Нужны наковальня TFC внутри здания и привязанный склад сырья.";
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
                ids.add(candidate.getClass().getMethod("getId").invoke(candidate).toString());
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
                                     IItemHandlerModifiable slots,KingdomNpcData.Worker w){
        ItemStack metal=slots.getStackInSlot(0);
        if(metal.isEmpty()||slots.getSlots()<4)return false;
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
            if(recipe==null)return false;
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
        if(w==null||!"smith".equals(w.job)||w.employer==null||!w.hasWorkstation
                ||!w.hasSource||!w.hasDestination||w.contractPaused
                ||!NpcLife.ableToWork(w.ageYears,w.hunger,w.energy,w.contractPaused)
                ||!KingdomNpcDuty.autonomousOrder(w.order)
                ||!"CONTINUE_ASSIGNED_JOB".equals(w.autonomousIntent)
                ||!ModList.get().isLoaded("tfc"))return false;
        var server=level.getServer();
        var enterprise=KingdomEnterpriseData.get(server);
        UUID company;
        try{company=UUID.fromString(w.workplaceId);}catch(IllegalArgumentException e){return false;}
        var firm=enterprise.find(company);
        var binding=enterprise.core(company);
        var contract=KingdomEnterprisePayrollData.get(server).contract(company,w.entityId);
        if(firm==null||!firm.owner().equals(w.employer)||enterprise.kind(company)!=EnterpriseKind.SMITHY
                ||binding==null||!binding.active()
                ||contract==null||!contract.active()
                ||!w.dimension.equals(level.dimension().location().toString()))return false;
        var building=EnterpriseBuildingRegistration.get(server).room(company);
        BlockPos forgePos=new BlockPos(w.workX,w.workY,w.workZ);
        if(building==null||!building.dimension().equals(w.dimension)
                ||!inside(building,forgePos)||!level.hasChunkAt(forgePos)
                ||!FORGE.equals(id(level,forgePos)))return false;
        BlockEntity forge=level.getBlockEntity(forgePos);
        if(!isSmithMachine(forge,FORGE_BE))return false;
        BlockPos fromPos=new BlockPos(w.sourceX,w.sourceY,w.sourceZ);
        BlockPos toPos=new BlockPos(w.destX,w.destY,w.destZ);
        var land=KingdomTerritories.get(server);
        if(!level.hasChunkAt(fromPos)||!level.hasChunkAt(toPos)
                ||!land.usable(level,fromPos,w.employer,"raw")
                ||!land.usable(level,toPos,w.employer,"finished")
                ||!(level.getBlockEntity(fromPos) instanceof Container raw)
                ||!(level.getBlockEntity(toPos) instanceof Container finished))return false;
        if(!near(npc,forgePos)){walk(npc,forgePos);return false;}
        IItemHandlerModifiable forgeInv=inventory(forge);
        if(forgeInv==null||forgeInv.getSlots()<14)return false;
        // 1: fuel is consumed from the actual raw warehouse, by native TFC forge.
        if(fromChest(raw,forge,forgeInv,0,KingdomTfcSmithWork::forgeFuel)){
            KingdomNpcData.get(server).setDirty();return true;
        }
        // 2: warm native forge, using its real light/consumeFuel method.
        try{
            if(!Boolean.TRUE.equals(forge.getClass().getMethod("light",
                    net.minecraft.world.level.block.state.BlockState.class)
                    .invoke(forge,level.getBlockState(forgePos))))return false;
        }catch(ReflectiveOperationException|RuntimeException ex){return false;}
        // 3: actual molten metal molds/casting vessels occupy native extra slots.
        for(int slot=10;slot<=13;slot++)if(fromChest(raw,forge,forgeInv,slot,
                stack->!forgeFuel(stack)))return true;
        // 4: input ores/metal are heated by TFC, never generated by Kingdom.
        for(int slot=5;slot<=9;slot++)if(fromChest(raw,forge,forgeInv,slot,
                stack->!forgeFuel(stack)&&!nativeValid(forge,10,stack)))return true;
        BlockPos anvilPos=findAnvil(level,building,forgePos);
        if(anvilPos==null)return false;
        BlockEntity anvil=level.getBlockEntity(anvilPos);
        IItemHandlerModifiable anvilInv=inventory(anvil);
        if(anvilInv==null||anvilInv.getSlots()<4)return false;
        // 5: a REAL TFC hammer is placed in the anvil's own hammer slot by player;
        // it is not demanded from a warehouse chest.
        if(!anvilInv.getStackInSlot(0).isEmpty()){
            if(w.smithOutputReady){
                ItemStack ready=anvilInv.getStackInSlot(0);
                if(intoChest(finished,ready)){
                    anvilInv.setStackInSlot(0,ItemStack.EMPTY);anvil.setChanged();
                    w.smithOutputReady=false;KingdomNpcData.get(server).setDirty();return true;
                }
                return false; // Real output waits until the finished chest has space.
            }
            if(anvilWork(level,npc,anvil,anvilInv,w))return true;
            return false;
        }
        if(w.smithOutputReady){w.smithOutputReady=false;KingdomNpcData.get(server).setDirty();}
        if(anvilInv.getStackInSlot(2).isEmpty())return false;
        for(int slot=5;slot<=9;slot++){
            ItemStack heated=forgeInv.getStackInSlot(slot);
            if(heated.isEmpty()||!hotEnough(heated)||forgeCapability(heated)==null)continue;
            if(!selectUniqueAnvilRecipePreflight(level,anvil,heated,w))continue;
            // Move the SAME real hot stack to the anvil, with no copy left in forge.
            anvilInv.setStackInSlot(0,heated.copy());
            forgeInv.setStackInSlot(slot,ItemStack.EMPTY);
            forge.setChanged();anvil.setChanged();return true;
        }
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
