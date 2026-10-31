package ru.projectkingdom.npc;

import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.IForgeShearable;
import net.minecraftforge.fml.ModList;
import ru.projectkingdom.enterprise.EnterpriseKind;

/**
 * True livestock work using native TFC feeding state and Forge shearing hooks.
 * Mod-private interfaces are invoked reflectively to keep one Forge-only JAR.
 * One action per NPC, every bounded worker tick. No entity spawn/milk/egg minting.
 */
public final class KingdomLivestockWork {
    private KingdomLivestockWork(){}
    private static boolean loaded,compatible;
    private static Class<?> tfcAnimal;
    private static Method hungry,isFood,getCalendar,lastFed,getFamiliarity,
        setFamiliarity,getFamiliarityCap,setFamiliarityDecay;
    private static boolean init(){
        if(loaded)return compatible;
        loaded=true;
        if(!ModList.get().isLoaded("tfc"))return false;
        try{
            tfcAnimal=Class.forName("net.dries007.tfc.common.entities.livestock.TFCAnimalProperties");
            hungry=tfcAnimal.getMethod("isHungry");
            isFood=tfcAnimal.getMethod("isFood",ItemStack.class);
            getCalendar=tfcAnimal.getMethod("getCalendar");
            lastFed=tfcAnimal.getMethod("setLastFed",long.class);
            getFamiliarity=tfcAnimal.getMethod("getFamiliarity");
            setFamiliarity=tfcAnimal.getMethod("setFamiliarity",float.class);
            getFamiliarityCap=tfcAnimal.getMethod("getAdultFamiliarityCap");
            setFamiliarityDecay=tfcAnimal.getMethod("setLastFamiliarityDecay",long.class);
            compatible=true;
        }catch(ReflectiveOperationException|LinkageError ex){
            System.err.println("[Kingdom] TFC husbandry unavailable: "+ex);
        }
        return compatible;
    }
    private static boolean isTfc(Animal animal){
        return init() && tfcAnimal.isInstance(animal);
    }
    private static boolean hungry(Animal animal) {
        try{return Boolean.TRUE.equals(hungry.invoke(animal));}
        catch(ReflectiveOperationException|RuntimeException ex){return false;}
    }
    private static boolean food(Animal animal,ItemStack item) {
        if(item.isEmpty()||item.hasCraftingRemainingItem())return false;
        try{return Boolean.TRUE.equals(isFood.invoke(animal,item));}
        catch(ReflectiveOperationException|RuntimeException ex){return false;}
    }
    private static boolean feed(Animal animal,ItemStack item){
        if(!hungry(animal)||!food(animal,item))return false;
        try{
            Object calendar=getCalendar.invoke(animal);
            long day=((Number)calendar.getClass().getMethod("getTotalDays").invoke(calendar)).longValue();
            float familiar=((Number)getFamiliarity.invoke(animal)).floatValue();
            float cap=((Number)getFamiliarityCap.invoke(animal)).floatValue();
            // Native TFC properties, following its feed-state semantics.
            lastFed.invoke(animal,day);
            setFamiliarityDecay.invoke(animal,day);
            setFamiliarity.invoke(animal,Math.min(cap,familiar+0.06f));
            animal.heal(1f);
            item.shrink(1);
            return true;
        }catch(ReflectiveOperationException|RuntimeException ex){return false;}
    }
    private static UUID farm(ServerLevel level,KingdomNpcData.Worker w) {
        UUID owned=KingdomNpcFarmOwner.ownedFarm(level.getServer(),w);
        if(owned!=null)return owned;
        if(!"breeder".equals(w.job)||w.employer==null
            ||!KingdomNpcWorkday.employed(level.getServer(),w))return null;
        var enterprises=KingdomEnterpriseData.get(level.getServer());
        for(var contract:KingdomEnterprisePayrollData.get(level.getServer()).all()){
            if(!contract.active()||!contract.worker().equals(w.entityId)
                ||(!w.workplaceId.isBlank()&&!w.workplaceId.equals(contract.enterprise().toString()))
                ||enterprises.kind(contract.enterprise())!=EnterpriseKind.FARM)continue;
            var firm=enterprises.find(contract.enterprise());
            var core=enterprises.core(contract.enterprise());
            if(firm!=null&&firm.owner().equals(w.employer)
                &&core!=null&&core.active()&&core.kind()==EnterpriseKind.FARM)
                return firm.id();
        }
        return null;
    }
    static BlockPos workSite(ServerLevel level,KingdomNpcData.Worker w){
        UUID farm=farm(level,w);if(farm==null)return null;
        for(var pen:KingdomRanchPenData.get(level.getServer()).of(farm)){
            BlockPos center=pen.center();
            if(pen.enabled()&&pen.dimension().equals(w.dimension)
                &&level.hasChunkAt(center))return center;
        }
        return null;
    }

    /** Whole-herd count is independent of TFC compatibility; status is unknown in unloaded pens. */
    public record PenStatus(int animals,int tfcAnimals,int hungry,int woolReady,int milkReady,
                            int edibleEggs,boolean areaLoaded,boolean tfcAvailable) {
        public int otherAnimals(){return animals-tfcAnimals;}
        public PenStatus(int animals,int hungry,int woolReady){
            this(animals,animals,hungry,woolReady,0,0,true,true);
        }
        public PenStatus(int animals,int hungry,int woolReady,int milkReady,int eggs){
            this(animals,animals,hungry,woolReady,milkReady,eggs,true,true);
        }
    }
    /** A pen can straddle up to four chunks. Never silently render unknown as zero. */
    public static boolean areaLoaded(ServerLevel level,KingdomRanchPenData.Pen pen){
        if(!pen.dimension().equals(level.dimension().location().toString()))return false;
        for(int cx=pen.minX()>>4;cx<=pen.maxX()>>4;cx++)
            for(int cz=pen.minZ()>>4;cz<=pen.maxZ()>>4;cz++)
                if(!level.hasChunk(cx,cz))return false;
        return true;
    }

    /** Read-only diagnostic. Count every living Animal; only TFC animals support native ranch jobs. */
    public static PenStatus inspect(ServerLevel level,KingdomRanchPenData.Pen pen){
        if(!areaLoaded(level,pen))return new PenStatus(0,0,0,0,0,0,false,false);
        boolean tfcReady=init();
        AABB bounds=new AABB(pen.minX(),pen.y()-3,pen.minZ(),
            pen.maxX()+1,pen.y()+4,pen.maxZ()+1);
        int count=0,tfcCount=0,needsFood=0,ready=0,milkReady=0;
        for(Animal animal:level.getEntitiesOfClass(Animal.class,bounds,
                entity->entity.isAlive()&&pen.contains(entity))){
            count++;
            if(!tfcReady||!tfcAnimal.isInstance(animal))continue;
            tfcCount++;
            if(hungry(animal))needsFood++;
            if(KingdomTfcMilkWork.readyForMilking(animal))milkReady++;
            if(animal instanceof IForgeShearable shearable
                && animal.getClass().getName().startsWith("net.dries007.tfc.common.entities.livestock")
                && shearable.isShearable(new ItemStack(Items.SHEARS),level,animal.blockPosition()))ready++;
        }
        return new PenStatus(count,tfcCount,needsFood,ready,milkReady,
            KingdomTfcNestWork.availableEggs(level,pen),true,tfcReady);
    }

    private static boolean nearby(Villager npc,BlockPos pos){
        return npc.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=8;
    }
    private static void walk(Villager npc,BlockPos pos) {
        npc.getNavigation().moveTo(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,.75);
    }
    private static boolean takeFood(ServerLevel level,Villager npc,KingdomNpcData.Worker w,
                                     Animal target,Container raw,BlockPos rawPos){
        for(ItemStack in:w.fieldInventory)if(food(target,in))return true;
        if(!nearby(npc,rawPos)){walk(npc,rawPos);return false;}
        for(int i=0;i<raw.getContainerSize();i++){
            ItemStack stack=raw.getItem(i);
            if(!stack.isEmpty() && raw.canTakeItem(raw,i,stack)
                && food(target,stack) && NpcFieldWork.canFit(w,List.of(stack.copyWithCount(1)))){
                ItemStack taken=raw.removeItem(i,1);
                if(taken.isEmpty())return false;
                NpcFieldWork.put(w,taken);raw.setChanged();
                KingdomNpcData.get(level.getServer()).setDirty();return true;
            }
        }
        return false;
    }
    private static boolean feedAnimal(ServerLevel level,Villager npc,KingdomNpcData.Worker w,
                                      Animal animal,Container raw,BlockPos rawPos){
        if(!hungry(animal)||!takeFood(level,npc,w,animal,raw,rawPos))return false;
        if(npc.distanceToSqr(animal)>9){npc.getNavigation().moveTo(animal,.75);return false;}
        for(int i=0;i<w.fieldInventory.size();i++){
            ItemStack in=w.fieldInventory.get(i);
            if(!food(animal,in))continue;
            if(!feed(animal,in))return false;
            if(in.isEmpty())w.fieldInventory.remove(i);
            w.workUnits++;w.energy=Math.max(0,w.energy-1);w.skillXp++;
            KingdomNpcData.get(level.getServer()).setDirty();
            return true;
        }
        return false;
    }
    private static boolean shearAnimal(ServerLevel level,Villager npc,KingdomNpcData.Worker w,
                                       Animal animal,Container raw,BlockPos rawPos){
        if(!(animal instanceof IForgeShearable shearable))return false;
        // Only genuine TFC wool animals. Other shearable mods require their own rules.
        if(!animal.getClass().getName().startsWith("net.dries007.tfc.common.entities.livestock"))
            return false;
        ItemStack shears=w.fieldTool;
        if(!shears.is(Items.SHEARS)){
            if(!nearby(npc,rawPos)){walk(npc,rawPos);return false;}
            for(int i=0;i<raw.getContainerSize();i++) {
                ItemStack in=raw.getItem(i);
                if(in.is(Items.SHEARS) && raw.canTakeItem(raw,i,in)){
                    if(!shears.isEmpty())return false;
                    w.fieldTool=raw.removeItem(i,1);
                    raw.setChanged();KingdomNpcData.get(level.getServer()).setDirty();
                    return true;
                }
            }
            return false;
        }
        if(!shearable.isShearable(shears,level,animal.blockPosition()))return false;
        // TFC-native anticipated output; first verify the carried inventory can hold it.
        ItemStack expected;
        try {
            Method getWool=animal.getClass().getMethod("getWoolItem");
            expected=(ItemStack)getWool.invoke(animal);
        }catch(ReflectiveOperationException|RuntimeException ex){return false;}
        if(expected.isEmpty()||!NpcFieldWork.canFit(w,List.of(expected)))return false;
        if(npc.distanceToSqr(animal)>9){npc.getNavigation().moveTo(animal,.75);return false;}
        // The Forge event is invoked by TFC onSheared (including product cancellation).
        List<ItemStack> drops=shearable.onSheared(null,shears,level,animal.blockPosition(),0);
        if(drops==null||drops.stream().allMatch(ItemStack::isEmpty))return false;
        // If another mod increases the event product beyond our prediction,
        // keep EVERY physical drop rather than silently deleting an overflow.
        if(!NpcFieldWork.canFit(w,drops)){
            for(ItemStack drop:drops)if(!drop.isEmpty())
                net.minecraft.world.level.block.Block.popResource(level,animal.blockPosition(),drop);
            shears.hurtAndBreak(1,npc,v-> {});
            return true;
        }
        for(ItemStack drop:drops) if(!drop.isEmpty()) {
            NpcFieldWork.put(w,drop);
            w.fieldHarvested+=drop.getCount();
        }
        shears.hurtAndBreak(1,npc,v-> {});
        w.workUnits++;w.skillXp++;w.energy=Math.max(0,w.energy-1);
        KingdomNpcData.get(level.getServer()).setDirty();
        return true;
    }

    public static boolean tick(ServerLevel level,Villager npc,KingdomNpcData.Worker w){
        if(w==null||!init()||!("breeder".equals(w.job)||"farmer".equals(w.job))
            ||!w.dimension.equals(level.dimension().location().toString())
            ||w.contractPaused||!w.hasSource||!w.hasDestination
            ||!KingdomNpcDuty.autonomousOrder(w.order)
            ||!"CONTINUE_ASSIGNED_JOB".equals(w.autonomousIntent)
            ||!NpcLife.ableToWork(w.ageYears,w.hunger,w.energy,w.contractPaused))return false;
        UUID enterprise=farm(level,w);
        if(enterprise==null)return false;
        UUID owner=KingdomNpcFarmOwner.workOwner(level,w);
        if(owner==null)return false;
        BlockPos rawPos=new BlockPos(w.sourceX,w.sourceY,w.sourceZ);
        BlockPos finishedPos=new BlockPos(w.destX,w.destY,w.destZ);
        var territory=KingdomTerritories.get(level.getServer());
        if(!level.hasChunkAt(rawPos)||!level.hasChunkAt(finishedPos)
            ||!territory.usable(level,rawPos,owner,"raw")
            ||!territory.usable(level,finishedPos,owner,"finished")
            ||!(level.getBlockEntity(rawPos) instanceof Container raw)
            ||!(level.getBlockEntity(finishedPos) instanceof Container finished))return false;
        // Physical goods must be delivered before acquiring more supplies.
        if(w.butcherDelivery){
            if(w.fieldInventory.isEmpty()){
                w.butcherDelivery=false;
                KingdomNpcData.get(level.getServer()).setDirty();
                return false;
            }
            return NpcFieldWork.deposit(level,npc,w);
        }
        java.util.function.Predicate<ItemStack> product=stack->!stack.isEmpty()
            && (KingdomTfcNestWork.edibleEgg(stack)
                || KingdomTfcMilkWork.hasMilk(stack)
                || "tfc:wool".equals(net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(stack.getItem()).toString()));
        if(w.fieldInventory.stream().anyMatch(product))
            return KingdomLivestockStorage.deposit(level,npc,w,product);
        // Bounded: maximum four pens, each 16 x 16 in loaded chunks.
        for(var pen:KingdomRanchPenData.get(level.getServer()).of(enterprise)){
            if(!pen.enabled()||!pen.dimension().equals(w.dimension))continue;
            if(!areaLoaded(level,pen))continue;
            if(!territory.canUse(level,pen.center(),owner))continue;
            AABB area=new AABB(pen.minX(),pen.y()-3,pen.minZ(),
                pen.maxX()+1,pen.y()+4,pen.maxZ()+1);
            var herd=level.getEntitiesOfClass(Animal.class,area,
                entity->entity.isAlive()&&pen.contains(entity)&&isTfc(entity));
            var policy=KingdomRanchPolicyData.get(level.getServer()).policy(pen.id());
            for(Animal animal:herd){
                if(!isTfc(animal)||!territory.canUse(level,animal.blockPosition(),owner))continue;
                // TFC permits feeding once per day. Do not consume the player's
                // opportunity to tame/feed animals unless the owner opts in.
                if(policy.autoFeed()&&hungry(animal)
                    &&feedAnimal(level,npc,w,animal,raw,rawPos))return true;
                if(KingdomTfcMilkWork.tick(level,npc,w,animal,raw,rawPos,finished))
                    return true;
                if(shearAnimal(level,npc,w,animal,raw,rawPos))return true;
            }
            if(KingdomTfcNestWork.tick(level,npc,w,pen,finished))return true;
            if(KingdomTfcButcheryWork.tick(level,npc,w,pen,herd))return true;
        }
        return false;
    }
}
