package ru.projectkingdom.npc;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/** Real item custody from livestock products to an authorized finished warehouse. */
public final class KingdomLivestockStorage {
    private KingdomLivestockStorage(){}

    public static boolean canInsert(Container container,ItemStack stack){
        if(stack.isEmpty())return false;
        for(int i=0;i<container.getContainerSize();i++){
            if(!container.canPlaceItem(i,stack))continue;
            ItemStack dest=container.getItem(i);
            int size=Math.min(container.getMaxStackSize(),stack.getMaxStackSize());
            if(dest.isEmpty() && stack.getCount()<=size)return true;
            if(ItemStack.isSameItemSameTags(dest,stack)
                    && dest.getCount()+stack.getCount()<=Math.min(size,dest.getMaxStackSize()))
                return true;
        }
        return false;
    }
    public static boolean insert(Container container,ItemStack stack){
        if(!canInsert(container,stack))return false;
        for(int i=0;i<container.getContainerSize();i++){
            if(!container.canPlaceItem(i,stack))continue;
            ItemStack existing=container.getItem(i);
            if(existing.isEmpty()){
                container.setItem(i,stack.copy());container.setChanged();return true;
            }
            if(ItemStack.isSameItemSameTags(existing,stack)
                    && existing.getCount()+stack.getCount()<=
                        Math.min(existing.getMaxStackSize(),container.getMaxStackSize())){
                existing.grow(stack.getCount());container.setChanged();return true;
            }
        }
        return false;
    }
    /** Deposit ONLY actual animal products, never reserve feed or empty vessels. */
    public static boolean deposit(ServerLevel level,Villager npc,KingdomNpcData.Worker w,
                                  Predicate<ItemStack> product){
        if(w.fieldInventory.isEmpty()||!w.hasDestination)return false;
        var owner=KingdomNpcFarmOwner.workOwner(level,w);
        if(owner==null)return false;
        BlockPos pos=new BlockPos(w.destX,w.destY,w.destZ);
        if(!level.hasChunkAt(pos)||!KingdomTerritories.get(level.getServer())
                .usable(level,pos,owner,"finished")
                ||!(level.getBlockEntity(pos) instanceof Container dest))return false;
        for(int i=0;i<w.fieldInventory.size();i++){
            ItemStack carried=w.fieldInventory.get(i);
            if(!product.test(carried)||!canInsert(dest,carried))continue;
            if(npc.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>6.25){
                npc.getNavigation().moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,.75);
                return false;
            }
            if(!insert(dest,carried))return false;
            w.deliveredItems+=carried.getCount();
            w.fieldInventory.remove(i);
            KingdomNpcData.get(level.getServer()).setDirty();
            return true;
        }
        return false;
    }
}
