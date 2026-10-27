package ru.projectkingdom.npc;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandlerModifiable;
import ru.projectkingdom.enterprise.EnterpriseKind;

/**
 * Physical weaver working a real TFC loom. Native TFC recipe, number of
 * threads, weave steps and completion are used; no recipe shortcut or output
 * minting. One Java JAR, with runtime-only TFC references.
 *
 * The trusted worker may touch ONLY an empty loom, or its own persisted batch.
 * A loom already containing a player's fabric is not claimed.
 */
public final class KingdomTfcLoomWork {
    private KingdomTfcLoomWork(){}
    private static boolean attempted,compatible;
    private static Class<?> loomClass;
    private static Method lookup,press,getRecipe,clearRecipe;
    private static Field inventoryField;

    public static boolean isLoom(ServerLevel level,KingdomNpcData.Worker w) {
        if(w==null||!"weaver".equals(w.job)||!w.hasWorkstation
                ||!ModList.get().isLoaded("tfc"))return false;
        BlockPos pos=new BlockPos(w.workX,w.workY,w.workZ);
        return level.hasChunkAt(pos) && "tfc:loom".equals(
            BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString());
    }
    private static boolean init(){
        if(attempted)return compatible;
        attempted=true;
        if(!ModList.get().isLoaded("tfc"))return false;
        try {
            loomClass=Class.forName("net.dries007.tfc.common.blockentities.LoomBlockEntity");
            Class<?> recipe=Class.forName("net.dries007.tfc.common.recipes.LoomRecipe");
            Class<?> storage=Class.forName("net.dries007.tfc.common.blockentities.InventoryBlockEntity");
            inventoryField=storage.getDeclaredField("inventory");
            inventoryField.setAccessible(true);
            lookup=recipe.getMethod("getRecipe",Level.class,ItemStack.class);
            press=loomClass.getMethod("onRightClick",Player.class);
            getRecipe=loomClass.getMethod("getRecipe");
            clearRecipe=loomClass.getDeclaredMethod("clearRecipe");
            clearRecipe.setAccessible(true);
            compatible=true;
        }catch(ReflectiveOperationException|LinkageError ex){
            System.err.println("[Kingdom] TFC loom adapter unavailable: "+ex);
        }
        return compatible;
    }
    private static String id(ItemStack s){
        return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }
    private record Pattern(int count,ItemStack product){}
    @SuppressWarnings("unchecked")
    private static Pattern match(ServerLevel level,ItemStack sample){
        if(!init()||sample.isEmpty()||KingdomNpcNutrition.spoiled(sample)
            ||KingdomCurrency.value(sample)>0
            ||sample.is(KingdomCurrency.WALLET.get()))return null;
        try {
            Object found=lookup.invoke(null,level,sample.copyWithCount(1));
            if(!(found instanceof Recipe<?> source))return null;
            int count=((Number)found.getClass().getMethod("getInputCount").invoke(found)).intValue();
            if(count<1||count>64)return null;
            Class<?> inv=Class.forName("net.dries007.tfc.common.recipes.inventory.ItemStackInventory");
            Object wrapper=inv.getConstructor(ItemStack.class).newInstance(sample.copyWithCount(1));
            ItemStack result=((Recipe<Container>)source).assemble((Container)wrapper,level.registryAccess());
            if(result.isEmpty()||KingdomCurrency.value(result)>0
                ||result.is(KingdomCurrency.WALLET.get()))return null;
            return new Pattern(count,result);
        }catch(ReflectiveOperationException|RuntimeException ex){
            compatible=false;
            System.err.println("[Kingdom] TFC loom recipes unavailable: "+ex);
            return null;
        }
    }

    private static ItemStack[] depositPlan(Container storage,ItemStack product){
        ItemStack[] result=new ItemStack[storage.getContainerSize()];
        for(int i=0;i<result.length;i++)result[i]=storage.getItem(i).copy();
        ItemStack remaining=product.copy();
        for(int i=0;i<result.length&&!remaining.isEmpty();i++){
            ItemStack stack=result[i];
            if(stack.isEmpty()||!ItemStack.isSameItemSameTags(stack,remaining)
                    ||!storage.canPlaceItem(i,remaining))continue;
            int room=Math.max(0,Math.min(stack.getMaxStackSize(),
                storage.getMaxStackSize())-stack.getCount());
            int taken=Math.min(room,remaining.getCount());
            if(taken>0){stack.grow(taken);remaining.shrink(taken);}
        }
        for(int i=0;i<result.length&&!remaining.isEmpty();i++){
            if(!result[i].isEmpty()||!storage.canPlaceItem(i,remaining))continue;
            int count=Math.min(remaining.getCount(),
                Math.min(remaining.getMaxStackSize(),storage.getMaxStackSize()));
            if(count<=0)continue;
            ItemStack stack=remaining.copyWithCount(count);
            result[i]=stack;remaining.shrink(count);
        }
        return remaining.isEmpty()?result:null;
    }

    /** Return rejected material to its source chest or leave it as a real drop. */
    private static void restore(ServerLevel level,BlockPos pos,Container raw,int slot,ItemStack stack){
        if(stack.isEmpty())return;
        ItemStack left=raw.getItem(slot);
        if(left.isEmpty()&&raw.canPlaceItem(slot,stack))raw.setItem(slot,stack);
        else if(ItemStack.isSameItemSameTags(left,stack)
                &&left.getCount()+stack.getCount()<=Math.min(left.getMaxStackSize(),
                    raw.getMaxStackSize())){left.grow(stack.getCount());raw.setChanged();}
        else Block.popResource(level,pos,stack.copy());
    }
    private static boolean deliver(ServerLevel level,KingdomNpcData.Worker w,
            BlockEntity loom,IItemHandlerModifiable inv,Container dest,
            KingdomTfcLoomData.Job job){
        ItemStack cloth=inv.getStackInSlot(1).copy();
        if(cloth.isEmpty()||!job.output().equals(id(cloth)))return false;
        var plan=depositPlan(dest,cloth);
        if(plan==null)return false;
        ItemStack removed=inv.extractItem(1,cloth.getCount(),true);
        if(!ItemStack.matches(removed,cloth))return false;
        removed=inv.extractItem(1,cloth.getCount(),false);
        if(!ItemStack.matches(removed,cloth)){
            if(!removed.isEmpty())Block.popResource(level,loom.getBlockPos(),removed);
            return false;
        }
        for(int i=0;i<plan.length;i++)dest.setItem(i,plan[i]);
        dest.setChanged();
        try{clearRecipe.invoke(loom);}
        catch(ReflectiveOperationException ex){compatible=false;loom.setChanged();}
        KingdomTfcLoomData.get(level.getServer()).release(w.entityId);
        w.produced+=cloth.getCount();w.workUnits++;w.skillXp++;
        w.energy=Math.max(0,w.energy-1);
        KingdomNpcData.get(level.getServer()).setDirty();
        return true;
    }

    public static boolean tick(ServerLevel level,Villager npc,KingdomNpcData.Worker w){
        if(!isLoom(level,w)||!init()||!KingdomNpcDuty.autonomousOrder(w.order)
                ||!"CONTINUE_ASSIGNED_JOB".equals(w.autonomousIntent)
                ||!NpcLife.ableToWork(w.ageYears,w.hunger,w.energy,w.contractPaused)
                ||w.employer==null||!w.hasSource||!w.hasDestination
                ||!KingdomNpcWorkday.employed(level.getServer(),w)
                ||!w.dimension.equals(level.dimension().location().toString()))return false;
        var firms=KingdomEnterpriseData.get(level.getServer());
        boolean employed=false;
        for(var contract:KingdomEnterprisePayrollData.get(level.getServer()).all()){
            if(!contract.active()||!contract.worker().equals(w.entityId)
                    ||(!w.workplaceId.isBlank()
                        &&!w.workplaceId.equals(contract.enterprise().toString()))
                    ||firms.kind(contract.enterprise())!=EnterpriseKind.TEXTILE)continue;
            var firm=firms.find(contract.enterprise());
            var core=firms.core(contract.enterprise());
            if(firm!=null&&firm.owner().equals(w.employer)
                    &&core!=null&&core.active()&&core.kind()==EnterpriseKind.TEXTILE){
                employed=true;break;
            }
        }
        if(!employed)return false;
        BlockPos station=new BlockPos(w.workX,w.workY,w.workZ);
        BlockPos rawPos=new BlockPos(w.sourceX,w.sourceY,w.sourceZ);
        BlockPos finishedPos=new BlockPos(w.destX,w.destY,w.destZ);
        if(station.distSqr(rawPos)>36||station.distSqr(finishedPos)>36
                ||!level.hasChunkAt(station)||!level.hasChunkAt(rawPos)
                ||!level.hasChunkAt(finishedPos))return false;
        var land=KingdomTerritories.get(level.getServer());
        if(!land.usable(level,station,w.employer,"workstation")
                ||!land.usable(level,rawPos,w.employer,"raw")
                ||!land.usable(level,finishedPos,w.employer,"finished"))return false;
        if(!(level.getBlockEntity(rawPos) instanceof Container raw)
                ||!(level.getBlockEntity(finishedPos) instanceof Container finished)
                ||raw==finished)return false;
        BlockEntity loom=level.getBlockEntity(station);
        if(loom==null||!loomClass.isInstance(loom))return false;
        if(npc.distanceToSqr(station.getX()+.5,station.getY()+.5,station.getZ()+.5)>6.25){
            npc.getNavigation().moveTo(station.getX()+.5,station.getY(),
                station.getZ()+.5,.8);
            return false;
        }
        try{
            Object value=inventoryField.get(loom);
            if(!(value instanceof IItemHandlerModifiable inv)||inv.getSlots()<2)return false;
            var custody=KingdomTfcLoomData.get(level.getServer());
            if(custody.claimedByOther(w.entityId,w.dimension,station.asLong()))return false;
            var job=custody.job(w.entityId);
            if(job!=null&&(!job.dimension().equals(w.dimension)
                    ||job.station()!=station.asLong()))return false;
            ItemStack output=inv.getStackInSlot(1);
            if(!output.isEmpty()){
                // Never take preexisting output that was not made by this worker.
                return job!=null && deliver(level,w,loom,inv,finished,job);
            }
            ItemStack in=inv.getStackInSlot(0);
            if(job!=null && !in.isEmpty() && !job.input().equals(id(in)))return false;
            if(job!=null && in.isEmpty()){
                // Loom was cleared manually; do not re-create inputs or product.
                custody.release(w.entityId);
                job=null;
            }
            if(in.isEmpty()){
                if(job!=null)return false;
                for(int slot=0;slot<raw.getContainerSize();slot++){
                    ItemStack stock=raw.getItem(slot);
                    if(stock.isEmpty()||!raw.canTakeItem(raw,slot,stock))continue;
                    var pattern=match(level,stock);
                    if(pattern==null||depositPlan(finished,pattern.product())==null)continue;
                    ItemStack one=stock.copyWithCount(1);
                    if(!inv.insertItem(0,one,true).isEmpty())continue;
                    ItemStack taken=raw.removeItem(slot,1);
                    if(!ItemStack.matches(taken,one)){
                        restore(level,station,raw,slot,taken);return false;
                    }
                    ItemStack remainder=inv.insertItem(0,taken,false);
                    if(!remainder.isEmpty()){
                        restore(level,station,raw,slot,remainder);
                        return false;
                    }
                    raw.setChanged();
                    custody.claim(new KingdomTfcLoomData.Job(w.entityId,w.dimension,
                        station.asLong(),id(one),id(pattern.product()),pattern.count()));
                    loom.setChanged();
                    return true;
                }
                return false;
            }
            if(job==null)return false;
            if(in.getCount()<job.needed()){
                for(int slot=0;slot<raw.getContainerSize();slot++){
                    ItemStack stock=raw.getItem(slot);
                    if(stock.isEmpty()||!raw.canTakeItem(raw,slot,stock)
                            ||!job.input().equals(id(stock))
                            ||!ItemStack.isSameItemSameTags(stock,in)
                            ||KingdomNpcNutrition.spoiled(stock))continue;
                    ItemStack one=stock.copyWithCount(1);
                    if(!inv.insertItem(0,one,true).isEmpty())continue;
                    ItemStack taken=raw.removeItem(slot,1);
                    if(!ItemStack.matches(taken,one)){
                        restore(level,station,raw,slot,taken);return false;
                    }
                    ItemStack leftover=inv.insertItem(0,taken,false);
                    if(!leftover.isEmpty()){
                        restore(level,station,raw,slot,leftover);return false;
                    }
                    raw.setChanged();loom.setChanged();return true;
                }
                return false;
            }
            if(in.getCount()!=job.needed())return false;
            Object recipe=getRecipe.invoke(loom);
            if(recipe==null)return false; // Native recipe resets on its own tick.
            // Simulate a human empty-handed push through TFC's real loom API.
            var fake=FakePlayerFactory.get(level,new GameProfile(
                UUID.nameUUIDFromBytes("Kingdom-TFC-Loom".getBytes(StandardCharsets.UTF_8)),
                "[KingdomWeaver]"));
            fake.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            Object result=press.invoke(loom,fake);
            return result instanceof InteractionResult interaction && interaction.consumesAction();
        }catch(ReflectiveOperationException|RuntimeException ex){
            compatible=false; // Version mismatch: stop, never mint fabric.
            System.err.println("[Kingdom] Native TFC weaving stopped: "+ex);
            return false;
        }
    }
}
