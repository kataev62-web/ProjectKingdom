package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;

/** Server-side courier pickup and delivery; never teleports cargo or spawns items. */
public final class KingdomCourierService {
    private KingdomCourierService() {}

    /** Process at most one inventory operation per second, only in loaded chunks. */
    public static boolean tick(ServerLevel level, Villager npc, KingdomNpcData.Worker worker) {
        if (!"courier".equals(worker.order) || worker.contractPaused || worker.ageYears<18) return false;
        KingdomSupplyOrders ledger=KingdomSupplyOrders.get(level.getServer());
        KingdomSupplyOrders.Order order=null;
        for (var candidate:ledger.all()) {
            if(candidate.mode==KingdomSupplyOrders.Mode.COURIER && npc.getUUID().equals(candidate.courier)
                && (candidate.state==KingdomSupplyOrders.State.ASSIGNED || candidate.state==KingdomSupplyOrders.State.IN_TRANSIT)) {
                order=candidate;break;
            }
        }
        if(order==null || !order.dimension.equals(level.dimension().location().toString())) {
            updateStatus(level,worker,(!worker.cargo.isEmpty() || worker.cartCargoCount>0
                    || worker.cartCargoOrder!=null)?"ORPHANED_CARGO_NEEDS_REVIEW":"IDLE");
            return false;
        }
        if(ledger.needsReconciliation(order.id)) {
            updateStatus(level,worker,"TRANSFER_RECONCILIATION_REQUIRED");return false;
        }
        // A courier carrying another order must not be dispatched to this one.
        if(worker.cartCargoOrder!=null && !order.id.equals(worker.cartCargoOrder)) {
            updateStatus(level,worker,"CART_ORDER_MISMATCH");return false;
        }
        if(!order.deliveryPaid){updateStatus(level,worker,"AWAITING_DELIVERY_PAYMENT");return false;}
        // Pair only real nearby AstikorCarts. Existing pairing persists across saves.
        if(worker.courierCart==null && worker.cargo.isEmpty() && worker.cartCargoCount==0)
            KingdomCourierCart.pair(level,npc,worker);
        if(!KingdomCourierCart.accompany(level,npc,worker)) {
            updateStatus(level,worker,worker.cartCargoCount>0?"CART_MISSING_WITH_CARGO":"CART_OUT_OF_RANGE");
            return false;
        }
        // Navigation recovery only: retry the route, never teleport an entity or cargo.
        BlockPos goal=(worker.cartCargoCount>0 || !worker.cargo.isEmpty() || order.pickedUp>=order.count)
                ?order.destination:order.source;
        if(!watchProgress(level,npc,worker,goal))return false;
        // A persisted cargo must belong to a picked-up but undelivered batch of this order.
        // Do not insert unknown cargo into a buyer chest after a restart or reassignment.
        if(!worker.cargo.isEmpty()) {
            ResourceLocation cargoId=ResourceLocation.tryParse(order.itemId);
            if(cargoId==null || !BuiltInRegistries.ITEM.containsKey(cargoId)
                    || !BuiltInRegistries.ITEM.getKey(worker.cargo.getItem()).equals(cargoId)
                    || order.state!=KingdomSupplyOrders.State.IN_TRANSIT
                    || worker.cargo.getCount()>order.pickedUp-order.delivered)return false;
        }
        // Cart cargo is stored in the actual AstikorCarts item handler, not in worker NBT.
        // An unloaded/missing cart blocks delivery rather than fabricating lost cargo.
        if(worker.cartCargoCount>0) {
            if(!order.id.equals(worker.cartCargoOrder) || order.state!=KingdomSupplyOrders.State.IN_TRANSIT
                    || worker.cartCargoCount>order.pickedUp-order.delivered)return false;
            IItemHandler cartInv=KingdomCourierCart.inventory(level,worker);
            if(cartInv==null){updateStatus(level,worker,"CART_INVENTORY_UNAVAILABLE");return false;}
            if(!arrived(level,npc,order.destination))return false;
            BlockEntity targetBe=level.getBlockEntity(order.destination);
            if(!(targetBe instanceof Container target))return false;
            ResourceLocation itemId=ResourceLocation.tryParse(order.itemId);
            if(itemId==null || !BuiltInRegistries.ITEM.containsKey(itemId))return false;
            ItemStack sample=new ItemStack(BuiltInRegistries.ITEM.get(itemId));
            if(!KingdomCourierCart.hasCargo(cartInv,sample,worker.cartCargoCount)) {
                updateStatus(level,worker,"CART_CARGO_MISMATCH");return false;
            }
            int canInsert=capacity(target,sample);
            if(canInsert<=0){updateStatus(level,worker,"DESTINATION_FULL");return false;}
            if(order.state!=KingdomSupplyOrders.State.IN_TRANSIT || order.pickedUp-order.delivered<1) return false;
            int planned=Math.min(canInsert,worker.cartCargoCount);
            if(!ledger.beginTransfer(order.id,npc.getUUID(),KingdomSupplyOrders.TransferKind.DELIVERY,planned))return false;
            ItemStack extracted=KingdomCourierCart.unload(cartInv,sample,planned);
            if(extracted.isEmpty()) {
                ledger.abortTransfer(order.id,npc.getUUID(),KingdomSupplyOrders.TransferKind.DELIVERY,planned);
                return false;
            }
            if(extracted.getCount()!=planned) {
                if(!restoreCart(cartInv,extracted))throw new IllegalStateException("Kingdom: partial cart extraction could not be restored");
                // Do not automatically clear journal: unexpected mutation needs review.
                return false;
            }
            // Insert actual extracted items; only then acknowledge their delivery.
            ItemStack remainder=extracted.copy();
            int delivered=insert(target,remainder);
            if(!remainder.isEmpty() && !restoreCart(cartInv,remainder))
                throw new IllegalStateException("Kingdom: cannot restore undelivered cart cargo");
            if(delivered<=0)return false;
            if(delivered!=planned)return false; // Leave journal pending for manual reconciliation.
            target.setChanged();
            if(!ledger.recordDelivery(order.id,npc.getUUID(),delivered))
                throw new IllegalStateException("Kingdom: cart delivery ledger rejected real insertion");
            worker.cartCargoCount-=delivered;
            if(!ledger.finishTransfer(order.id,npc.getUUID(),KingdomSupplyOrders.TransferKind.DELIVERY,delivered))
                throw new IllegalStateException("Kingdom: failed to finish cart delivery journal");
            if(worker.cartCargoCount==0)worker.cartCargoOrder=null;
            KingdomNpcData.get(level.getServer()).setDirty();
            updateStatus(level,worker,order.state==KingdomSupplyOrders.State.DELIVERED?"DELIVERED":"RETURNING_FOR_NEXT_BATCH");
            return true;
        }
        if(!worker.cargo.isEmpty()) {
            // Cargo is persisted on the worker; never remove it if destination is inaccessible.
            BlockPos dest=order.destination;
            if(!arrived(level,npc,dest))return false;
            BlockEntity be=level.getBlockEntity(dest);
            if(!(be instanceof Container target))return false;
            // Compute capacity without mutating the destination, then commit ledger
            // and real inventory in the same server tick. The batch may be partial.
            ItemStack remaining=worker.cargo.copy();
            int capacity=capacity(target,remaining);
            if(capacity<=0){updateStatus(level,worker,"DESTINATION_FULL");return false;}
            int toDeliver=Math.min(capacity,remaining.getCount());
            if(!ledger.beginTransfer(order.id,npc.getUUID(),KingdomSupplyOrders.TransferKind.DELIVERY,toDeliver))return false;
            ItemStack portion=remaining.copy();portion.setCount(toDeliver);
            int inserted=insert(target,portion);
            if(inserted!=toDeliver)throw new IllegalStateException("Kingdom courier: inventory capacity changed during delivery");
            if(!ledger.recordDelivery(order.id,npc.getUUID(),inserted))
                throw new IllegalStateException("Kingdom courier: delivery ledger rejected inserted items");
            remaining.shrink(inserted);
            target.setChanged();
            worker.cargo=remaining.isEmpty()?ItemStack.EMPTY:remaining;
            KingdomNpcData.get(level.getServer()).setDirty();
            if(!ledger.finishTransfer(order.id,npc.getUUID(),KingdomSupplyOrders.TransferKind.DELIVERY,inserted))
                throw new IllegalStateException("Kingdom: failed to finish worker delivery journal");
            updateStatus(level,worker,order.state==KingdomSupplyOrders.State.DELIVERED?"DELIVERED":"RETURNING_FOR_NEXT_BATCH");
            return true;
        }
        if(order.pickedUp>=order.count) {
            updateStatus(level,worker,"AWAITING_LAST_DELIVERY");return false;
        }
        if(worker.cartCargoCount>0)return false;
        BlockPos source=order.source;
        if(!arrived(level,npc,source))return false;
        BlockEntity be=level.getBlockEntity(source);
        if(!(be instanceof Container seller))return false;
        // The seller keeps a dedicated cashbox immediately ABOVE the sale chest.
        // The courier pays the full order price there before removing the first item.
        if(!order.goodsPaid && !order.buyer.equals(order.seller)) {
            if(!level.hasChunkAt(source.above()))return false;
            BlockEntity cashEntity=level.getBlockEntity(source.above());
            if(!(cashEntity instanceof Container cashbox))return false;
            if(!ledger.paySellerAtPickup(order.id,npc.getUUID(),cashbox))return false;
        }
        ResourceLocation id=ResourceLocation.tryParse(order.itemId);
        if(id==null || !BuiltInRegistries.ITEM.containsKey(id))return false;
        for(int slot=0;slot<seller.getContainerSize();slot++) {
            ItemStack stack=seller.getItem(slot);
            if(stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id) || !seller.canTakeItem(seller,slot,stack))continue;
            IItemHandler cartInv=KingdomCourierCart.inventory(level,worker);
            boolean cartAvailable=cartInv!=null && worker.cargo.isEmpty() && worker.cartCargoCount==0
                    && KingdomCourierCart.empty(cartInv);
            int amount=Math.min(stack.getCount(),order.count-order.pickedUp);
            if(cartAvailable)amount=Math.min(amount,KingdomCourierCart.capacity(cartInv,stack,amount));
            else amount=Math.min(amount,16);
            if(amount<=0)return false;
            if(!ledger.beginTransfer(order.id,npc.getUUID(),KingdomSupplyOrders.TransferKind.PICKUP,amount))return false;
            // Remove actual stock first; only record the amount actually extracted.
            ItemStack extracted=seller.removeItem(slot,amount);
            if(extracted.isEmpty()) {
                ledger.abortTransfer(order.id,npc.getUUID(),KingdomSupplyOrders.TransferKind.PICKUP,amount);
                return false;
            }
            if(extracted.getCount()!=amount)return false; // Unexpected extraction: quarantine transaction.
            if(!ledger.recordPickup(order.id,npc.getUUID(),extracted.getCount())) {
                ItemStack rollback=extracted.copy();
                if(insert(seller,rollback)!=extracted.getCount())
                    throw new IllegalStateException("Kingdom courier: failed to restore seller stock");
                seller.setChanged();
                ledger.abortTransfer(order.id,npc.getUUID(),KingdomSupplyOrders.TransferKind.PICKUP,amount);
                return false;
            }
            seller.setChanged();
            if(cartAvailable) {
                ItemStack remainder=extracted.copy();
                int loaded=KingdomCourierCart.load(cartInv,remainder);
                if(loaded!=extracted.getCount()) {
                    // An unexpected capability failure is fatal rather than silently losing goods.
                    throw new IllegalStateException("Kingdom: cart inventory rejected reserved cargo");
                }
                worker.cartCargoOrder=order.id;
                worker.cartCargoCount=loaded;
            } else worker.cargo=extracted;
            KingdomNpcData.get(level.getServer()).setDirty();
            if(!ledger.finishTransfer(order.id,npc.getUUID(),KingdomSupplyOrders.TransferKind.PICKUP,amount))
                throw new IllegalStateException("Kingdom: failed to finish pickup journal");
            updateStatus(level,worker,"IN_TRANSIT");
            return true;
        }
        updateStatus(level,worker,"SELLER_OUT_OF_STOCK");
        return false;
    }
    /** Every 20 server ticks, recover a stuck path by recalculating it.
     * After repeated failures, report a fault but keep trying at a lower frequency.
     * A moving courier (or arrival at goal) resets the stall counter.
     */
    private static boolean watchProgress(ServerLevel level,Villager npc,KingdomNpcData.Worker worker,BlockPos goal) {
        long now=level.getGameTime();
        BlockPos here=npc.blockPosition();
        if(worker.courierLastProgressTick==0 || now<worker.courierLastProgressTick
                || here.distSqr(new BlockPos(worker.courierLastX,worker.courierLastY,worker.courierLastZ))>=4
                || here.distSqr(goal)<=9) {
            worker.courierLastProgressTick=now;
            worker.courierLastX=here.getX();worker.courierLastY=here.getY();worker.courierLastZ=here.getZ();
            if(worker.courierStallAttempts!=0){worker.courierStallAttempts=0;KingdomNpcData.get(level.getServer()).setDirty();}
            return true;
        }
        if(now-worker.courierLastProgressTick<200)return true;
        worker.courierLastProgressTick=now;
        worker.courierStallAttempts=Math.min(1000,worker.courierStallAttempts+1);
        npc.getNavigation().stop();
        npc.getNavigation().moveTo(goal.getX()+0.5,goal.getY(),goal.getZ()+0.5,0.8);
        KingdomNpcData.get(level.getServer()).setDirty();
        if(worker.courierStallAttempts>=6) {
            updateStatus(level,worker,"ROUTE_STUCK_RETRYING");
            // Retry once per 10 seconds, with no forced position changes.
        } else updateStatus(level,worker,"RECALCULATING_ROUTE");
        return false;
    }

    private static void updateStatus(ServerLevel level,KingdomNpcData.Worker worker,String status) {
        if(!status.equals(worker.courierStatus)) {
            worker.courierStatus=status;
            KingdomNpcData.get(level.getServer()).setDirty();
        }
    }

    private static boolean restoreCart(IItemHandler inv,ItemStack remainder) {
        int count=remainder.getCount();
        return KingdomCourierCart.load(inv,remainder)==count;
    }
    private static boolean arrived(ServerLevel level,Villager npc,BlockPos pos) {
        if(!level.hasChunkAt(pos))return false;
        if(npc.distanceToSqr(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5)>9.0) {
            if(npc.getNavigation().isDone())npc.getNavigation().moveTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0.8);
            return false;
        }
        npc.getNavigation().stop();return true;
    }
    /** How many items can be inserted without touching the target inventory. */
    private static int capacity(Container container, ItemStack item) {
        int free=0;
        for(int slot=0;slot<container.getContainerSize();slot++) {
            if(!container.canPlaceItem(slot,item))continue;
            ItemStack existing=container.getItem(slot);
            if(existing.isEmpty())free+=Math.min(item.getMaxStackSize(),container.getMaxStackSize());
            else if(ItemStack.isSameItemSameTags(existing,item))
                free+=Math.max(0,Math.min(existing.getMaxStackSize(),container.getMaxStackSize())-existing.getCount());
            if(free>=item.getCount())return item.getCount();
        }
        return Math.min(free,item.getCount());
    }
    private static int insert(Container container,ItemStack remainder) {
        int original=remainder.getCount();
        for(int slot=0;slot<container.getContainerSize() && !remainder.isEmpty();slot++) {
            if(!container.canPlaceItem(slot,remainder))continue;
            ItemStack existing=container.getItem(slot);
            if(existing.isEmpty()) {
                int n=Math.min(remainder.getCount(),Math.min(remainder.getMaxStackSize(),container.getMaxStackSize()));
                ItemStack part=remainder.copy();part.setCount(n);container.setItem(slot,part);remainder.shrink(n);
            }else if(ItemStack.isSameItemSameTags(existing,remainder)) {
                int n=Math.min(remainder.getCount(),Math.min(existing.getMaxStackSize(),container.getMaxStackSize())-existing.getCount());
                if(n>0){existing.grow(n);remainder.shrink(n);container.setChanged();}
            }
        }
        return original-remainder.getCount();
    }
}
