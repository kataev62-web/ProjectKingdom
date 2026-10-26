package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;

/**
 * Stage 3 municipal canteen: real NPC resident walks to a *registered*, loaded
 * central town warehouse and consumes ONE of its real, unspoiled TFC food items.
 *
 * Never moves food remotely, creates products, debits money, or supplies private
 * company employees from public inventory. Does not load missing chunks.
 */
public final class KingdomMunicipalCanteen {
    private KingdomMunicipalCanteen() {}

    static boolean eligible(boolean allowedByMayor,boolean member,boolean employed,
                            boolean hasPrivateFarm,boolean sameTown,
                            boolean forecastKnown,boolean alreadyFed){
        return allowedByMayor&&member&&!employed&&!hasPrivateFarm
            &&sameTown&&forecastKnown&&!alreadyFed;
    }

    /** Cheap guard used by the existing NPC movement controller. */
    public static boolean walkingForMeal(net.minecraft.server.MinecraftServer server,
                                         KingdomNpcData.Worker w) {
        if(server==null||w==null||w.employer!=null||w.contractPaused)return false;
        var town=KingdomCivicsData.get(server).townOf(w.entityId);
        if(town==null||!KingdomMunicipalRationPolicy.get(server).enabled(town.id()))
            return false;
        long today=KingdomSeasonCalendar.totalDays(server);
        return today>=0&&w.lastMealDay!=today
            && (w.hunger<75||KingdomNpcNutrition.quality(w)<55);
    }
    public static boolean tick(ServerLevel level,Villager resident,KingdomNpcData.Worker worker){
        if(worker==null||resident==null||!resident.isAlive()||worker.employer!=null
                ||worker.contractPaused||!worker.dimension.equals(
                    level.dimension().location().toString()))return false;
        // Stagger registered residents so central storage is not scanned every NPC tick.
        long nowTick=level.getGameTime();
        if(Math.floorMod(worker.entityId.hashCode(),15)
                !=(int)Math.floorMod(level.getServer().getTickCount()/40L,15L))
            return false;
        long today=KingdomSeasonCalendar.totalDays(level.getServer());
        if(today<0)return false;
        if(worker.lastMealDay==today||(worker.hunger>=75
                && KingdomNpcNutrition.quality(worker)>=55))return false;

        var civics=KingdomCivicsData.get(level.getServer());
        var membership=civics.townOf(worker.entityId);
        if(membership==null)return false;
        UUID town=membership.id();
        var home=worker.hasHome
            ? new BlockPos(worker.homeX,worker.homeY,worker.homeZ)
            : resident.blockPosition();
        var homeTown=level.hasChunkAt(home)?civics.townAt(level,home):null;
        var here=civics.townAt(level,resident.blockPosition());
        var reserve=KingdomMunicipalFoodReserves.get(level.getServer());
        var forecast=reserve.forecast(town);
        boolean known=forecast!=null && forecast.complete()
            && forecast.day()==today && forecast.edible()>0;
        if(!eligible(KingdomMunicipalRationPolicy.get(level.getServer()).enabled(town),
                civics.isMember(town,worker.entityId),worker.employer!=null,
                KingdomNpcFarmOwner.ownedFarm(level.getServer(),worker)!=null,
                here!=null && here.id().equals(town) && homeTown!=null
                    && homeTown.id().equals(town),known,worker.lastMealDay==today))
            return false;
        // The forecast was created from real loaded chests. Verify them again
        // before ANY mutation, rather than trusting persisted or older counts.
        var storage=KingdomMunicipalFoodStorage.inspect(level.getServer(),town);
        if(!storage.complete())return false;
        var selected= (KingdomMunicipalFoodStorage.Slot)null;
        int bestSlot=-1,bestScore=Integer.MIN_VALUE;
        long bestExpiry=Long.MAX_VALUE;
        long clock=KingdomFoodFreshness.now();
        double nearest=Double.MAX_VALUE;
        for(var store:storage.containers()){
            if(store.level()!=level||!level.hasChunkAt(store.pos()))continue;
            double distance=resident.distanceToSqr(store.pos().getX()+.5,
                store.pos().getY()+.5,store.pos().getZ()+.5);
            if(distance>128*128)continue;
            Container chest=store.container();
            for(int i=0;i<chest.getContainerSize();i++){
                ItemStack stack=chest.getItem(i);
                if(stack.isEmpty()||!chest.canTakeItem(chest,i,stack))continue;
                var meal=KingdomNpcNutrition.inspect(stack);
                if(meal==null)continue;
                long expiry=KingdomFoodFreshness.expiry(stack);
                int score=KingdomFoodFreshness.mealScore(worker,meal,expiry,clock);
                if(score>bestScore || score==bestScore && (expiry<bestExpiry
                        || expiry==bestExpiry && distance<nearest)){
                    selected=store;bestSlot=i;bestScore=score;
                    bestExpiry=expiry;nearest=distance;
                }
            }
        }
        if(selected==null)return false;
        BlockPos destination=selected.pos();
        if(resident.distanceToSqr(destination.getX()+.5,destination.getY()+.5,
                destination.getZ()+.5)>6.25){
            resident.getNavigation().moveTo(destination.getX()+.5,
                destination.getY(),destination.getZ()+.5,.8);
            return false;
        }
        // Recheck physical ownership, membership and access after navigation.
        if(!KingdomMunicipalFoodStorage.destination(level.getServer(),town,
                worker.dimension,destination)
                ||!KingdomMunicipalRationPolicy.get(level.getServer()).enabled(town)
                ||!civics.isMember(town,worker.entityId)
                ||worker.employer!=null ||worker.lastMealDay==today)return false;
        Container chest=KingdomContainerAccess.resolve(level,destination);
        if(chest==null||bestSlot>=chest.getContainerSize())return false;
        ItemStack chosen=chest.getItem(bestSlot);
        var meal=KingdomNpcNutrition.inspect(chosen);
        if(meal==null||!chest.canTakeItem(chest,bestSlot,chosen))return false;
        ItemStack food=chest.removeItem(bestSlot,1);
        if(food.isEmpty())return false;
        var actual=KingdomNpcNutrition.inspect(food);
        if(actual==null){
            ItemStack previous=chest.getItem(bestSlot);
            if(previous.isEmpty())chest.setItem(bestSlot,food);
            else if(ItemStack.isSameItemSameTags(previous,food)
                    &&previous.getCount()<Math.min(previous.getMaxStackSize(),
                        chest.getMaxStackSize()))previous.grow(food.getCount());
            else net.minecraft.world.level.block.Block.popResource(level,destination,food);
            chest.setChanged();return false;
        }
        KingdomNpcNutrition.eat(worker,actual);
        worker.lastMealDay=today;
        chest.setChanged();
        KingdomNpcData.get(level.getServer()).setDirty();
        return true;
    }
}
