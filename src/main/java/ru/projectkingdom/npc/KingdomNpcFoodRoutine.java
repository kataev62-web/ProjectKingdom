package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/** A farm owner can actually eat its own FINISHED warehouse stock: one real item per day. */
public final class KingdomNpcFoodRoutine {
    private KingdomNpcFoodRoutine() {}

    private static int priority(KingdomNpcData.Worker w,KingdomNpcNutrition.Meal food) {
        int score=food.hunger()*2;
        for(int i=0;i<KingdomNpcNutrition.COUNT;i++)
            score+=food.nutrients()[i] * Math.max(0,100-KingdomNpcNutrition.nutrient(w,i))/40;
        return score;
    }
    public static boolean tick(ServerLevel level,Villager npc,KingdomNpcData.Worker w) {
        UUID farm=KingdomNpcFarmOwner.ownedFarm(level.getServer(),w);
        if(farm==null||w.contractPaused||!w.hasDestination||!npc.isAlive())return false;
        long today=level.getServer().overworld().getDayTime()/24000L;
        if(w.lastMealDay==today || (w.hunger>=75 && KingdomNpcNutrition.quality(w)>=55))
            return false;
        BlockPos pos=new BlockPos(w.destX,w.destY,w.destZ);
        if(!level.hasChunkAt(pos) || !KingdomTerritories.get(level.getServer())
                .usable(level,pos,w.entityId,"finished")
                || !(level.getBlockEntity(pos) instanceof Container foodStore))return false;
        int selected=-1, top=Integer.MIN_VALUE;
        KingdomNpcNutrition.Meal selectedMeal=null;
        for(int i=0;i<foodStore.getContainerSize();i++){
            ItemStack in=foodStore.getItem(i);
            if(in.isEmpty()||!foodStore.canTakeItem(foodStore,i,in))continue;
            var meal=KingdomNpcNutrition.inspect(in);
            if(meal==null)continue; // No spoiled/nonfood items.
            int score=priority(w,meal);
            if(score>top){top=score;selected=i;selectedMeal=meal;}
        }
        if(selected<0)return false;
        if(npc.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>6.25){
            npc.getNavigation().moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,.8);
            return false;
        }
        ItemStack eaten=foodStore.removeItem(selected,1);
        if(eaten.isEmpty())return false;
        KingdomNpcNutrition.Meal actual=KingdomNpcNutrition.inspect(eaten);
        if(actual==null) {
            // Unexpected stack mutation: put the item back or leave it in the real NPC bag.
            ItemStack old=foodStore.getItem(selected);
            if(old.isEmpty())foodStore.setItem(selected,eaten);
            else if(ItemStack.isSameItemSameTags(old,eaten)
                &&old.getCount()<old.getMaxStackSize())old.grow(1);
            else NpcFieldWork.put(w,eaten);
            foodStore.setChanged();KingdomNpcData.get(level.getServer()).setDirty();
            return false;
        }
        KingdomNpcNutrition.eat(w,actual);
        w.lastMealDay=today;
        foodStore.setChanged();
        KingdomNpcData.get(level.getServer()).setDirty();
        return true;
    }
}
