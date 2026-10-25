package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

/**
 * Native fluid transfer: ONE real fluid-containing item from a bound RAW chest
 * into a TFC pot. Never creates or refills fluid and never changes calendar time.
 *
 * Deliberately handles single, nonstacking vessels only. Stacked cups/buckets,
 * player-placed external pipe networks and custom transactional handlers are not
 * suitable for this adapter and may be handled by a separate logistics API.
 */
public final class KingdomTfcPotWater {
    private KingdomTfcPotWater() {}

    private static boolean sameFluid(FluidStack a,FluidStack b) {
        return !a.isEmpty() && !b.isEmpty()
            && a.getFluid()==b.getFluid() && a.isFluidEqual(b);
    }

    /**
     * Consume one filled vessel (count=1), refill a real machine, and keep the
     * emptied vessel in the SAME chest slot. Forge capabilities own every mutation.
     * Return false on partial transfer/unsupported item: no phantom water.
     */
    public static boolean fill(ServerLevel level,BlockPos at,Container raw,BlockEntity pot) {
        if(level==null||at==null||raw==null||pot==null||!level.hasChunkAt(at))return false;
        IFluidHandler receiver=pot.getCapability(ForgeCapabilities.FLUID_HANDLER,null).orElse(null);
        if(receiver==null||receiver.getTanks()!=1)return false;
        for(int slot=0;slot<raw.getContainerSize();slot++){
            ItemStack filled=raw.getItem(slot);
            if(filled.isEmpty()||filled.getCount()!=1||!raw.canTakeItem(raw,slot,filled))continue;
            ItemStack working=filled.copy();
            IFluidHandlerItem item=working.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
            if(item==null)continue;
            FluidStack offered=item.drain(Integer.MAX_VALUE,IFluidHandler.FluidAction.SIMULATE);
            if(offered.isEmpty()||offered.getAmount()<=0)continue;
            int accepted=receiver.fill(offered,IFluidHandler.FluidAction.SIMULATE);
            // Partial draining can break discrete containers and reverse filling;
            // this conservative path transfers an entire vessel or does nothing.
            if(accepted!=offered.getAmount())continue;
            FluidStack removed=item.drain(offered.getAmount(),IFluidHandler.FluidAction.EXECUTE);
            if(removed.getAmount()!=offered.getAmount()||!sameFluid(removed,offered))continue;
            int actual=receiver.fill(removed,IFluidHandler.FluidAction.EXECUTE);
            if(actual!=removed.getAmount()){
                // Restore what has not been placed in the real tank. If the
                // handler violates its simulation result, do not overwrite RAW.
                FluidStack unaccepted=removed.copy();
                unaccepted.setAmount(Math.max(0,removed.getAmount()-actual));
                if(!unaccepted.isEmpty())
                    item.fill(unaccepted,IFluidHandler.FluidAction.EXECUTE);
                // The accepted liquid remains physically in the pot. The
                // caller can investigate a nonstandard handler; no fake batch.
                raw.setItem(slot,item.getContainer());
                raw.setChanged();
                return actual>0;
            }
            raw.setItem(slot,item.getContainer());
            raw.setChanged();
            pot.setChanged();
            return true;
        }
        return false;
    }
}
