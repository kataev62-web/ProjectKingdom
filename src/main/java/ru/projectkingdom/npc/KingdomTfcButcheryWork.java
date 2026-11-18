package ru.projectkingdom.npc;

import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

/**
 * Owner-OPT-IN natural TFC meat and hide loot, NEVER minted crafting products.
 * Native animal death is synchronous: LivingDropsEvent transfers its real drops
 * into the worker's persistent bag, leaving overflow on the ground.
 */
public final class KingdomTfcButcheryWork {
    private KingdomTfcButcheryWork(){}
    private static final Map<UUID,KingdomNpcData.Worker> ACTIVE=new HashMap<>();
    private static Class<?> animalProperties;
    private static Method getAgeType,isFertilized,getCalendar,getTotalDays;
    private static boolean loaded,compatible;
    private static boolean init(){
        if(loaded)return compatible;
        loaded=true;
        if(!ModList.get().isLoaded("tfc"))return false;
        try{
            animalProperties=Class.forName("net.dries007.tfc.common.entities.livestock.TFCAnimalProperties");
            getAgeType=animalProperties.getMethod("getAgeType");
            isFertilized=animalProperties.getMethod("isFertilized");
            getCalendar=animalProperties.getMethod("getCalendar");
            // Reflect on the public TFC interface, not its possibly non-public implementation.
            getTotalDays=Class.forName("net.dries007.tfc.util.calendar.ICalendar").getMethod("getTotalDays");
            compatible=true;
        }catch(ReflectiveOperationException|LinkageError ex){
            System.err.println("[Kingdom] TFC butchery disabled: "+ex);
        }
        return compatible;
    }
    private static boolean eligible(Animal animal){
        if(!init()||!animalProperties.isInstance(animal))return false;
        try{
            String age=String.valueOf(getAgeType.invoke(animal));
            return "OLD".equals(age)&&!Boolean.TRUE.equals(isFertilized.invoke(animal));
        }catch(ReflectiveOperationException|RuntimeException ex){return false;}
    }
    private static long tfcDay(Animal animal) {
        try {
            Object calendar=getCalendar.invoke(animal);
            return ((Number)getTotalDays.invoke(calendar)).longValue();
        }catch(ReflectiveOperationException|RuntimeException ex){return -1;}
    }

    public static boolean tick(ServerLevel level,Villager npc,KingdomNpcData.Worker worker,
                               KingdomRanchPenData.Pen pen,List<Animal> herd){
        if(!"breeder".equals(worker.job)||worker.butcherDelivery||!worker.fieldInventory.isEmpty()
            ||!pen.enabled()||!init())return false;
        var state=KingdomRanchPolicyData.get(level.getServer());
        var policy=state.policy(pen.id());
        UUID owner=KingdomNpcFarmOwner.workOwner(level,worker);
        if(owner==null||!pen.dimension().equals(level.dimension().location().toString()))return false;
        var claims=KingdomTerritories.get(level.getServer());
        // Count only animals on accessible, loaded land: an unclaimed/foreign
        // animal must neither inflate the herd threshold nor be culled.
        List<Animal> ownedHerd=herd.stream()
            .filter(animal->animal.isAlive()&&pen.contains(animal)
                &&level.hasChunkAt(animal.blockPosition())
                &&claims.canUse(level,animal.blockPosition(),owner)).toList();
        if(!policy.cullOld()||ownedHerd.size()<=policy.minHerd())return false;
        for(Animal animal:ownedHerd){
            // Revalidate immediately before an irreversible action.
            if(!claims.canUse(level,animal.blockPosition(),owner)||!eligible(animal))continue;
            long day=tfcDay(animal);
            if(day<0 || !KingdomRanchPolicyData.mayCull(policy,day,ownedHerd.size(),true,false))
                continue;
            if(npc.distanceToSqr(animal)>9){
                npc.getNavigation().moveTo(animal,.75);return false;
            }
            // Land ownership is checked here even if another caller supplies the herd.
            ACTIVE.put(animal.getUUID(),worker);
            try{
                if(!animal.hurt(level.damageSources().mobAttack(npc),Float.MAX_VALUE))
                    return false;
                if(!animal.isAlive()){
                    state.markCull(pen.id(),day);
                    worker.workUnits++;worker.energy=Math.max(0,worker.energy-4);
                    worker.skillXp+=2;
                    worker.butcherDelivery=!worker.fieldInventory.isEmpty();
                    KingdomNpcData.get(level.getServer()).setDirty();
                    return true;
                }
            }finally{
                ACTIVE.remove(animal.getUUID());
            }
        }
        return false;
    }

    /** Called by the registered Kingdom NPC Forge listener. */
    public static void onRealDrops(LivingDropsEvent event){
        if(event.isCanceled())return;
        KingdomNpcData.Worker worker=ACTIVE.get(event.getEntity().getUUID());
        if(worker==null)return;
        var iter=event.getDrops().iterator();
        while(iter.hasNext()){
            ItemEntity entity=iter.next();
            var stack=entity.getItem();
            if(stack.isEmpty()||!NpcFieldWork.canFit(worker,List.of(stack)))continue;
            NpcFieldWork.put(worker,stack.copy());
            worker.fieldHarvested+=stack.getCount();
            iter.remove(); // This exact real drop has moved into the NPC bag, not duplicated.
        }
    }
}
