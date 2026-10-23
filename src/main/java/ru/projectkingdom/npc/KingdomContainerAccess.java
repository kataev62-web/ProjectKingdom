package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * Safe, loaded-block storage resolver shared by binding and transactions.
 * Vanilla containers are used directly. Modded inventories (including Forge
 * storages that don't implement vanilla Container) need a mutable item handler.
 * A read-only capability is deliberately rejected: balances must never be
 * credited for a storage where real item stacks cannot be written.
 */
public final class KingdomContainerAccess {
    private KingdomContainerAccess() {}

    public static Container resolve(ServerLevel level,BlockPos pos) {
        if(level==null||pos==null||!level.hasChunkAt(pos))return null;
        BlockEntity entity=level.getBlockEntity(pos);
        if(entity instanceof Container container)return container;
        if(entity==null)return null;
        var capability=entity.getCapability(ForgeCapabilities.ITEM_HANDLER);
        var option=capability.resolve();
        if(option.isEmpty() || !(option.get() instanceof IItemHandlerModifiable mutable))return null;
        if(mutable.getSlots()<1 || mutable.getSlots()>256)return null;
        return new ModdedContainer(entity,mutable);
    }

    private static final class ModdedContainer implements Container {
        private final BlockEntity entity;
        private final IItemHandlerModifiable handler;
        private ModdedContainer(BlockEntity entity,IItemHandlerModifiable handler){
            this.entity=entity;this.handler=handler;
        }
        @Override public int getContainerSize(){return handler.getSlots();}
        @Override public boolean isEmpty(){
            for(int i=0;i<handler.getSlots();i++)if(!handler.getStackInSlot(i).isEmpty())return false;
            return true;
        }
        @Override public ItemStack getItem(int slot) {
            return slot>=0&&slot<handler.getSlots()?handler.getStackInSlot(slot):ItemStack.EMPTY;
        }
        @Override public ItemStack removeItem(int slot,int count){
            if(slot<0||slot>=handler.getSlots()||count<=0)return ItemStack.EMPTY;
            var removed=handler.extractItem(slot,count,false);
            if(!removed.isEmpty())entity.setChanged();
            return removed;
        }
        @Override public ItemStack removeItemNoUpdate(int slot){
            if(slot<0||slot>=handler.getSlots())return ItemStack.EMPTY;
            ItemStack current=handler.getStackInSlot(slot).copy();
            if(!current.isEmpty())handler.setStackInSlot(slot,ItemStack.EMPTY);
            return current;
        }
        @Override public void setItem(int slot,ItemStack stack){
            if(slot<0||slot>=handler.getSlots())return;
            if(!stack.isEmpty() && (!handler.isItemValid(slot,stack)
                    ||stack.getCount()>handler.getSlotLimit(slot)))return;
            handler.setStackInSlot(slot,stack.copy());entity.setChanged();
        }
        @Override public void setChanged(){entity.setChanged();}
        @Override public boolean stillValid(Player player){
            return entity.getLevel()!=null && entity.getLevel().getBlockEntity(entity.getBlockPos())==entity
                && player.distanceToSqr(entity.getBlockPos().getX()+0.5,
                        entity.getBlockPos().getY()+0.5,entity.getBlockPos().getZ()+0.5)<=64;
        }
        @Override public void clearContent(){
            for(int i=0;i<handler.getSlots();i++)handler.setStackInSlot(i,ItemStack.EMPTY);
            entity.setChanged();
        }
        @Override public boolean canPlaceItem(int slot,ItemStack stack){
            return slot>=0&&slot<handler.getSlots()&&handler.isItemValid(slot,stack)
                &&stack.getCount()<=handler.getSlotLimit(slot);
        }
        @Override public boolean canTakeItem(Container target,int slot,ItemStack stack){
            return slot>=0&&slot<handler.getSlots()
                &&!handler.extractItem(slot,1,true).isEmpty();
        }
    }
}
