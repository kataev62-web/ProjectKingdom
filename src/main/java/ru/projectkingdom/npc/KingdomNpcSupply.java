package ru.projectkingdom.npc;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Physical NPC visit to a warehouse for an actual quotation. No remote checkout or phantom cash. */
public final class KingdomNpcSupply {
    private KingdomNpcSupply() {}
    private static final String COAL="minecraft:charcoal";
    private static final int TARGET=20;
    private static final EnterpriseStockPolicy.Thresholds COAL_LIMITS=new EnterpriseStockPolicy.Thresholds(8,TARGET);
    /** -1 is unknown physical stock: refuse the purchase rather than assuming an empty chest. */
    private static int charcoalNeed(ServerLevel level,KingdomNpcData.Worker w,BlockPos work){
        var p=KingdomNpcWorkshopData.get(level.getServer()).plan(w.entityId);
        if(p!=null){
            var stock=KingdomEnterpriseStock.inspect(level,p.enterprise(),COAL,
                    ru.projectkingdom.enterprise.EnterpriseBindingRole.RAW_STORAGE,
                    new BlockPos(w.sourceX,w.sourceY,w.sourceZ));
            return stock.complete()?EnterpriseStockPolicy.reorderQuantity(COAL_LIMITS,stock.state(0)):-1;
        }
        if(!level.hasChunkAt(work)||!(level.getBlockEntity(work) instanceof Container chest))return -1;
        int physical=0;
        for(int i=0;i<chest.getContainerSize();i++){
            ItemStack stack=chest.getItem(i);
            if(!stack.isEmpty()&&COAL.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
                physical+=stack.getCount();
        }
        return EnterpriseStockPolicy.reorderQuantity(COAL_LIMITS,physical,0);
    }
    /** Delivery fee is only a quote; no coins are debited until a safe checkout exists. */
    private static final long DELIVERY_FEE=10;
    /** Only an exact set of real coins can leave the enterprise cashbox. */
    private static boolean takeExactCash(Container box, KingdomNpcData.Worker worker, long total) {
        if(box==null || total<=0 || total>1_000_000 || !worker.fieldInventory.isEmpty())return false;
        int[] units={10000,100,1};
        long[] counts=new long[3];
        List<ItemStack> before=new ArrayList<>();
        for(int i=0;i<box.getContainerSize();i++) {
            ItemStack stack=box.getItem(i);before.add(stack.copy());
            for(int j=0;j<3;j++)if(KingdomCurrency.value(stack)==units[j])counts[j]+=stack.getCount();
        }
        long[] selected=null;
        for(long gold=Math.min(counts[0],total/10000);gold>=0 && selected==null;gold--) {
            long remainder=total-gold*10000;
            long silver=Math.min(counts[1],remainder/100);
            if(remainder-silver*100<=counts[2])selected=new long[]{gold,silver,remainder-silver*100};
        }
        if(selected==null)return false; // No virtual change and no value creation.
        List<ItemStack> carried=new ArrayList<>();
        for(int j=0;j<3;j++) {
            long left=selected[j];
            for(int i=0;i<before.size()&&left>0;i++) {
                ItemStack stack=before.get(i);
                if(KingdomCurrency.value(stack)!=units[j])continue;
                int take=(int)Math.min(left,stack.getCount());stack.shrink(take);left-=take;
                while(take>0) {
                    int n=Math.min(take,64);
                    ItemStack coin=new ItemStack(j==0?KingdomCurrency.GOLD.get():j==1?KingdomCurrency.SILVER.get():KingdomCurrency.COPPER.get(),n);
                    carried.add(coin);take-=n;
                }
            }
            if(left!=0)return false;
        }
        if(carried.size()>36)return false;
        for(int i=0;i<before.size();i++)box.setItem(i,before.get(i));
        box.setChanged();worker.fieldInventory.addAll(carried);
        return true;
    }
    private static boolean returnCash(Container box, KingdomNpcData.Worker worker) {
        if(box==null)return false;
        List<ItemStack> slots=new ArrayList<>();
        for(int i=0;i<box.getContainerSize();i++)slots.add(box.getItem(i).copy());
        for(ItemStack carried:worker.fieldInventory) {
            if(KingdomCurrency.value(carried)==0)return false;
            int remaining=carried.getCount();
            for(int i=0;i<slots.size()&&remaining>0;i++) {
                ItemStack stack=slots.get(i);
                if(!ItemStack.isSameItemSameTags(stack,carried))continue;
                int add=Math.min(remaining,Math.min(stack.getMaxStackSize(),box.getMaxStackSize())-stack.getCount());
                if(add>0){stack.grow(add);remaining-=add;}
            }
            for(int i=0;i<slots.size()&&remaining>0;i++) {
                if(!slots.get(i).isEmpty()||!box.canPlaceItem(i,carried))continue;
                int add=Math.min(remaining,Math.min(carried.getMaxStackSize(),box.getMaxStackSize()));
                ItemStack stack=carried.copy();stack.setCount(add);slots.set(i,stack);remaining-=add;
            }
            if(remaining>0)return false;
        }
        for(int i=0;i<slots.size();i++)box.setItem(i,slots.get(i));
        box.setChanged();worker.fieldInventory.clear();return true;
    }
    private static long carriedValue(KingdomNpcData.Worker worker) {
        long total=0;
        for(ItemStack stack:worker.fieldInventory)total+=KingdomCurrency.value(stack)*stack.getCount();
        return total;
    }
    /** The worker pays at the counter, not remotely from the workshop. */
    private static boolean buyAtCounter(ServerLevel level, KingdomNpcData.Worker worker, BlockPos desk, BlockPos work) {
        if(!KingdomWarehouseMarket.get(level.getServer()).isDesk(level,desk))return false;
        if(charcoalNeed(level,worker,work)<worker.supplyCount)return false;
        if(!level.hasChunkAt(work) || !(level.getBlockEntity(work) instanceof Container))return false;
        if(!KingdomTerritories.get(level.getServer()).canUse(level,work,worker.employer))return false;
        List<KingdomWarehouseMarket.Quote> offers=KingdomWarehouseMarket.get(level.getServer()).search(level,worker.supplyItem,worker.supplyCount);
        if(offers.isEmpty())return false;
        KingdomWarehouseMarket.Quote quote=offers.get(0);
        long goods=quote.totalCopper(), total=goods+DELIVERY_FEE;
        if(goods<=0 || total>1_000_000 || carriedValue(worker)!=total || quote.stall().pos().equals(work))return false;
        KingdomSupplyOrders ledger=KingdomSupplyOrders.get(level.getServer());
        KingdomSupplyOrders.Order order=ledger.create(worker.employer,quote.stall().owner(),
            level.dimension().location().toString(),worker.supplyItem,quote.stall().pos(),work,
            worker.supplyCount,goods,DELIVERY_FEE,KingdomSupplyOrders.Mode.COURIER);
        if(order==null)return false;
        if(!ledger.escrow(order.id,goods,DELIVERY_FEE)) {
            ledger.cancel(order.id,worker.employer);return false;
        }
        // Coins become escrowed cash only after a valid order exists. Never duplicate carried coins.
        worker.fieldInventory.clear();worker.supplyOrderId=order.id.toString();return true;
    }


    private static Container enterpriseCashbox(ServerLevel level, BlockPos work) {
        // A dedicated physical cashbox must touch the workshop inventory.
        for(Direction dir:Direction.values()) {
            BlockPos pos=work.relative(dir);
            if(!level.hasChunkAt(pos))continue;
            BlockEntity entity=level.getBlockEntity(pos);
            if(entity instanceof Container cashbox)return cashbox;
        }
        return null;
    }

    private static long cashAvailable(Container cashbox) {
        if(cashbox==null)return 0;
        long value=0;
        for(int i=0;i<cashbox.getContainerSize();i++) {
            ItemStack stack=cashbox.getItem(i);
            if(stack.is(KingdomCurrency.COPPER.get()))value+=stack.getCount();
            else if(stack.is(KingdomCurrency.SILVER.get()))value+=100L*stack.getCount();
            else if(stack.is(KingdomCurrency.GOLD.get()))value+=10000L*stack.getCount();
        }
        return value;
    }

    /** Order IDs survive world reloads; failed deliveries must not trigger repeat purchases. */
    private static boolean hasOutstandingOrder(ServerLevel level, KingdomNpcData.Worker worker) {
        if(worker.supplyOrderId==null || worker.supplyOrderId.isBlank())return false;
        try {
            KingdomSupplyOrders.Order o=KingdomSupplyOrders.get(level.getServer())
                    .find(UUID.fromString(worker.supplyOrderId));
            if(o==null || o.state==KingdomSupplyOrders.State.CANCELLED
                    || o.state==KingdomSupplyOrders.State.DELIVERED) {
                worker.supplyOrderId="";
                KingdomNpcData.get(level.getServer()).setDirty();
                return false;
            }
            return true;
        } catch(IllegalArgumentException badId) {
            worker.supplyOrderId="";
            KingdomNpcData.get(level.getServer()).setDirty();
            return false;
        }
    }
    public static boolean tick(ServerLevel level, Villager npc, KingdomNpcData.Worker worker) {
        if(!"smith".equals(worker.job) || worker.contractPaused || worker.ageYears<18
                || worker.employer==null || !worker.hasWorkstation
                || (!"stay".equals(worker.order) && !"wander".equals(worker.order)))return false;
        long now=level.getGameTime();
        BlockPos work=new BlockPos(worker.workX,worker.workY,worker.workZ);
        if(!level.hasChunkAt(work))return false;
        if("RETURNING_UNUSED_CASH".equals(worker.supplyStatus)) {
            if(npc.distanceToSqr(work.getX()+0.5,work.getY()+0.5,work.getZ()+0.5)>9) {
                if(npc.getNavigation().isDone())npc.getNavigation().moveTo(work.getX()+0.5,work.getY(),work.getZ()+0.5,0.8);
                return false;
            }
            if(returnCash(enterpriseCashbox(level,work),worker)) {
                worker.supplyStatus="RETURNING_TO_WORK";worker.supplyNextAttempt=now+1200;
            }
            return true;
        }
        if("FETCHING_CASH".equals(worker.supplyStatus)) {
            if(npc.distanceToSqr(work.getX()+0.5,work.getY()+0.5,work.getZ()+0.5)>9) {
                if(npc.getNavigation().isDone())npc.getNavigation().moveTo(work.getX()+0.5,work.getY(),work.getZ()+0.5,0.8);
                return false;
            }
            Container cashbox=enterpriseCashbox(level,work);
            if(takeExactCash(cashbox,worker,worker.supplyQuoteCopper)) {
                worker.supplyStatus="CARRYING_CASH_TO_WAREHOUSE";
            } else {worker.supplyStatus="CASH_DENOMINATIONS_MISSING";worker.supplyNextAttempt=now+1200;}
            return true;
        }
        if("IDLE".equals(worker.supplyStatus)) {
            // A pending paid order reserves real stock. Do not buy the same missing
            // charcoal again while the courier is still on the road.
            if(hasOutstandingOrder(level,worker)) {
                worker.supplyStatus="WAITING_DELIVERY";return true;
            }
            if(now<worker.supplyNextAttempt || npc.distanceToSqr(work.getX()+0.5,work.getY(),work.getZ()+0.5)>100)return false;
            // Shared physical count includes enterprise storage and shipments already ordered.
            int needed=charcoalNeed(level,worker,work);
            if(needed<=0){worker.supplyNextAttempt=now+1200;return needed==0;}
            BlockPos desk=KingdomWarehouseMarket.get(level.getServer()).nearestDesk(level,npc.blockPosition(),128);
            if(desk==null){worker.supplyNextAttempt=now+1200;worker.supplyStatus="NO_WAREHOUSE";return true;}
            worker.supplyItem=COAL;worker.supplyCount=needed;
            worker.supplyDeskX=desk.getX();worker.supplyDeskY=desk.getY();worker.supplyDeskZ=desk.getZ();
            worker.supplyStatus="WALKING_TO_WAREHOUSE";return true;
        }
        if("NO_WAREHOUSE".equals(worker.supplyStatus)
                || "OUT_OF_STOCK".equals(worker.supplyStatus)
                || "INSUFFICIENT_CASH".equals(worker.supplyStatus)
                || "AWAITING_CASH_CHECKOUT".equals(worker.supplyStatus)
                || "CASH_DENOMINATIONS_MISSING".equals(worker.supplyStatus)
                || "PRICE_CHANGED".equals(worker.supplyStatus)) {
            // A failed quotation must not strand a worker forever at the counter.
            if(now<worker.supplyNextAttempt)return false;
            worker.supplyStatus="RETURNING_TO_WORK";
            return true;
        }
        if("WAITING_DELIVERY".equals(worker.supplyStatus)) {
            if(hasOutstandingOrder(level,worker))return false;
            worker.supplyStatus="IDLE";
            worker.supplyNextAttempt=now+200;
            return true;
        }
        if("RETURNING_TO_WORK".equals(worker.supplyStatus)) {
            if(npc.distanceToSqr(work.getX()+0.5,work.getY()+0.5,work.getZ()+0.5)>9) {
                if(npc.getNavigation().isDone())npc.getNavigation().moveTo(work.getX()+0.5,work.getY(),work.getZ()+0.5,0.8);
                return false;
            }
            worker.supplyStatus="IDLE";
            worker.supplyNextAttempt=now+1200;
            return true;
        }
        if(!"WALKING_TO_WAREHOUSE".equals(worker.supplyStatus)
                && !"CARRYING_CASH_TO_WAREHOUSE".equals(worker.supplyStatus))return false;
        BlockPos desk=new BlockPos(worker.supplyDeskX,worker.supplyDeskY,worker.supplyDeskZ);
        if(!level.hasChunkAt(desk) || !KingdomWarehouseMarket.get(level.getServer()).isDesk(level,desk)) {
            worker.supplyStatus=worker.fieldInventory.isEmpty()?"NO_WAREHOUSE":"RETURNING_UNUSED_CASH";
            worker.supplyNextAttempt=now+1200;return true;
        }
        if(npc.distanceToSqr(desk.getX()+0.5,desk.getY()+0.5,desk.getZ()+0.5)>9) {
            if(npc.getNavigation().isDone())npc.getNavigation().moveTo(desk.getX()+0.5,desk.getY(),desk.getZ()+0.5,0.8);
            return false;
        }
        npc.getNavigation().stop();
        if("CARRYING_CASH_TO_WAREHOUSE".equals(worker.supplyStatus)) {
            if(buyAtCounter(level,worker,desk,work))worker.supplyStatus="RETURNING_TO_WORK";
            else {worker.supplyStatus="RETURNING_UNUSED_CASH";worker.supplyNextAttempt=now+1200;}
            return true;
        }
        List<KingdomWarehouseMarket.Quote> quotes=KingdomWarehouseMarket.get(level.getServer()).search(level,worker.supplyItem,worker.supplyCount);
        // A quote is NOT a purchase. The enterprise needs a physical cashbox and a
        // server-side debit/escrow operation before a courier order may be created.
        if(quotes.isEmpty())worker.supplyStatus="OUT_OF_STOCK";
        else {
            // NPC can compare real offers, but no remote withdrawal from the forge.
            // Physical coins must be carried to the counter in a later transaction-safe phase.
            long cheapest=quotes.get(0).totalCopper();
            Container cashbox=enterpriseCashbox(level,work);
            worker.supplyQuoteCopper=cheapest+DELIVERY_FEE;
            worker.supplyStatus=cashAvailable(cashbox)>=worker.supplyQuoteCopper
                    ? "FETCHING_CASH" : "INSUFFICIENT_CASH";
        }
        worker.supplyNextAttempt=now+1200;
        return true;
    }
}
