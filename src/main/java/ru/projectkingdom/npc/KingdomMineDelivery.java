package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * Mine-only physically verified delivery. Does not touch the shared farmer/
 * lumberjack storage adapter or manufacture drops. An unloaded or rejected
 * destination leaves the original NBT-bearing NPC cargo intact.
 */
public final class KingdomMineDelivery {
    private KingdomMineDelivery(){}

    public enum Result { DELIVERED, WALKING, NO_CARGO, UNLOADED, NO_PERMISSION, NO_STORAGE, FULL }

    public static String message(Result result) {
        return switch(result){
            case DELIVERED -> "Добыча передана в настоящий склад; инструменты и крепления сохранены";
            case WALKING -> "Идёт к складу готовой продукции";
            case NO_CARGO -> "Нет добычи для доставки";
            case UNLOADED -> "Чанк склада продукции выгружен — доставка приостановлена";
            case NO_PERMISSION -> "Нет прав на склад готовой продукции";
            case NO_STORAGE -> "Склад продукции не привязан или физически недоступен";
            case FULL -> "Склад продукции переполнен или отклоняет добычу: груз сохранён";
        };
    }

    /** FieldInventory is persisted with a limit of 36 stacks. Counting just
     * items can lose unique NBT stacks on restart even below MAX_CARRIED.
     */
    public static boolean canCarry(KingdomNpcData.Worker worker,List<ItemStack> items){
        if(worker==null||items==null)return false;
        final List<ItemStack> stacks=new ArrayList<>();
        int count=0;
        for(ItemStack existing:worker.fieldInventory){
            if(existing.isEmpty())continue;
            count+=existing.getCount();
            if(stacks.size()>=36||count>NpcFieldWork.MAX_CARRIED)return false;
            stacks.add(existing.copy());
        }
        for(ItemStack item:items){
            if(item==null||item.isEmpty())continue;
            count+=item.getCount();
            if(count>NpcFieldWork.MAX_CARRIED)return false;
            ItemStack remaining=item.copy();
            for(ItemStack current:stacks){
                if(!ItemStack.isSameItemSameTags(current,remaining))continue;
                int room=Math.max(0,current.getMaxStackSize()-current.getCount());
                int n=Math.min(room,remaining.getCount());
                if(n>0){current.grow(n);remaining.shrink(n);}
                if(remaining.isEmpty())break;
            }
            while(!remaining.isEmpty()){
                if(stacks.size()>=36)return false;
                int n=Math.min(remaining.getCount(),remaining.getMaxStackSize());
                if(n<=0)return false;
                ItemStack part=remaining.copy();
                part.setCount(n);
                stacks.add(part);
                remaining.shrink(n);
            }
        }
        return true;
    }

    /**
     * The destination gets a COPY with the exact native NBT. Its slot is read
     * back and only the number ACTUALLY present is deducted from the source.
     * This supports inventory implementations returning defensive copies.
     */
    public static int transfer(Container target,ItemStack cargo){
        if(target==null||cargo==null||cargo.isEmpty())return 0;
        int moved=0;
        for(int i=0;i<target.getContainerSize()&&!cargo.isEmpty();i++){
            ItemStack before=target.getItem(i).copy();
            if(!before.isEmpty()&&!ItemStack.isSameItemSameTags(before,cargo))continue;
            int max=Math.min(target.getMaxStackSize(),cargo.getMaxStackSize());
            if(!before.isEmpty())max=Math.min(max,before.getMaxStackSize());
            int amount=Math.min(cargo.getCount(),max-before.getCount());
            if(amount<=0)continue;
            ItemStack proposed;
            do{
                proposed=before.isEmpty()?cargo.copy():before.copy();
                proposed.setCount(before.getCount()+amount);
                if(target.canPlaceItem(i,proposed))break;
                amount--;
            }while(amount>0);
            if(amount<=0)continue;
            target.setItem(i,proposed);
            ItemStack accepted=target.getItem(i);
            // If a modded container clamps the request, only count the
            // exact verified NBT-matching delta. Never credit an attempted write.
            if(!ItemStack.isSameItemSameTags(accepted,proposed)
                    ||accepted.getCount()<before.getCount())break;
            int actual=accepted.getCount()-before.getCount();
            if(actual<=0)continue;
            if(actual>amount)break; // unexpected mutation; never debit unknown output
            cargo.shrink(actual);
            moved+=actual;
            target.setChanged();
        }
        return moved;
    }

    /** Move actual mined ore, not TFC beams, planks, ladders or tools. */
    public static Result deliver(ServerLevel level,Villager npc,KingdomNpcData.Worker worker){
        if(level==null||npc==null||worker==null||worker.employer==null
                ||!worker.hasDestination)return Result.NO_STORAGE;
        if(KingdomMineCargo.minedCount(worker)==0)return Result.NO_CARGO;
        BlockPos target=new BlockPos(worker.destX,worker.destY,worker.destZ);
        if(!level.hasChunkAt(target))return Result.UNLOADED;
        UUID owner=worker.employer;
        if(!KingdomTerritories.get(level.getServer()).usable(level,target,owner,"finished"))
            return Result.NO_PERMISSION;
        if(npc.distanceToSqr(target.getX()+.5,target.getY(),target.getZ()+.5)>6.25){
            npc.getNavigation().moveTo(target.getX()+.5,target.getY(),target.getZ()+.5,.8);
            return Result.WALKING;
        }
        Container storage=KingdomContainerAccess.resolve(level,target);
        if(storage==null)return Result.NO_STORAGE;
        boolean delivered=false;
        for(int index=0;index<worker.fieldInventory.size();){
            ItemStack cargo=worker.fieldInventory.get(index);
            if(cargo.isEmpty()){worker.fieldInventory.remove(index);continue;}
            if(KingdomMineCargo.isSupply(cargo)){index++;continue;}
            if(transfer(storage,cargo)>0)delivered=true;
            if(cargo.isEmpty())worker.fieldInventory.remove(index);
            else index++;
        }
        if(delivered){
            storage.setChanged();
            KingdomNpcData.get(level.getServer()).setDirty();
            return Result.DELIVERED;
        }
        return Result.FULL;
    }
}
