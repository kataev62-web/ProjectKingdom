package ru.projectkingdom.npc;

import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.*;

/** Independent runnable workshop checks; stubs do NOT substitute for a Forge integration test. */
public final class Stage1WorkshopHarness {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static class Chest extends BlockEntity implements Container {
        final ItemStack[] slots=new ItemStack[27];
        Chest(){Arrays.fill(slots,ItemStack.EMPTY);}
        @Override public int getContainerSize(){return 27;}
        @Override public ItemStack getItem(int i){return slots[i];}
        @Override public void setItem(int i,ItemStack item){slots[i]=item;}
    }
    public static void main(String[] args)throws Exception {
        MinecraftServer server=new MinecraftServer();
        var owner=UUID.randomUUID();var supplier=UUID.randomUUID();var employee=UUID.randomUUID();
        var a=new ServerPlayer(server,owner);var b=new ServerPlayer(server,supplier);
        a.inventory.wallet.money=1000;b.inventory.wallet.money=1000;
        var cash=KingdomEnterpriseData.get(server);
        var buyer=cash.create(owner,"Bakery");
        var source=new Chest();var finished=new Chest();var sellerChest=new Chest();
        var sourcePos=new BlockPos(0,0,0);var station=new BlockPos(0,0,1);
        var destPos=new BlockPos(0,0,2);var sellerPos=new BlockPos(2,0,0);
        server.world.entities.put(sourcePos,source);server.world.entities.put(destPos,finished);
        server.world.entities.put(sellerPos,sellerChest);
        check(cash.bindCounter(a,sourcePos),"buyer counter");
        check(cash.deposit(a,500),"buyer payment funded by real wallet");
        var charcoal=BuiltInRegistries.ITEM.get("minecraft:charcoal");
        var stick=BuiltInRegistries.ITEM.get("minecraft:stick");
        source.setItem(0,new ItemStack(charcoal,1));source.setItem(1,new ItemStack(stick,1));
        KingdomNpcData.Worker w=new KingdomNpcData.Worker(employee,owner);
        w.workX=station.getX();w.workY=station.getY();w.workZ=station.getZ();
        w.sourceX=sourcePos.getX();w.sourceY=sourcePos.getY();w.sourceZ=sourcePos.getZ();
        w.destX=destPos.getX();w.destY=destPos.getY();w.destZ=destPos.getZ();
        KingdomNpcData.get(server).add(w);
        var villager=new Villager();server.world.people.put(employee,villager);
        check(KingdomEnterprisePayrollData.get(server).hire(server,owner,buyer.id(),employee,10),"employment");
        check(KingdomNpcWorkshop.configure(a,w).startsWith("План записан"),"create plan from real crafting grid");
        var plans=KingdomNpcWorkshopData.get(server);
        check(plans.plan(employee)!=null,"plan exists");
        check(!KingdomNpcWorkshop.tick(server.world,villager,w),"work starts with no output");
        check(finished.getItem(0).isEmpty(),"no output immediately");
        server.world.day=199;
        check(!KingdomNpcWorkshop.tick(server.world,villager,w),"wait actual time");
        check(finished.getItem(0).isEmpty(),"cannot skip work time");
        server.world.day=200;
        check(KingdomNpcWorkshop.tick(server.world,villager,w),"real crafting finishes");
        check(source.getItem(0).isEmpty()&&source.getItem(1).isEmpty(),"real inputs consumed");
        check(finished.getItem(0).getCount()==4,"real product output");
        check(w.produced==4&&w.workUnits==1,"production counted only on item creation");
        check(cash.find(buyer.id()).cash()==500,"production does not mint any money");

        // Supplier publishes real, physical stock. Buyer worker shops ONLY at adjacent counters.
        var shop=cash.create(supplier,"Fuel sales");
        b.pos=sellerPos;
        check(cash.bindCounter(b,sellerPos),"supplier counter");
        sellerChest.setItem(0,new ItemStack(charcoal,16));sellerChest.setItem(1,new ItemStack(stick,16));
        var o1=cash.listItem(b,"minecraft:charcoal",2);
        var o2=cash.listItem(b,"minecraft:stick",3);
        check(o1!=null&&o2!=null,"supplier offers visible");
        server.world.day=240;
        check(!KingdomNpcWorkshop.tick(server.world,villager,w),"procurement is not instant manufacturing");
        check(cash.find(buyer.id()).cash()==492,"NPC paid for actual charcoal");
        check(cash.find(shop.id()).cash()==8,"seller received buyer funds");
        check(source.getItem(0).isEmpty()&&source.getItem(9).getCount()==4,"NPC purchase goes to input buffer, not blueprint");
        check(sellerChest.getItem(0).getCount()==12,"supplier loses physical goods");
        // A journaled purchase quarantines both counterparties until a verified
        // reconciliation. The next NPC purchase must not bypass that safeguard.
        var firstTrade=cash.trades().stream().filter(t->t.state()==KingdomEnterpriseData.TradeState.NEEDS_RECONCILIATION)
                .findFirst().orElseThrow();
        check(cash.assessTrade(server.world,firstTrade.id())==TradeWriteAheadJournal.RecoveryAssessment.AFTER,
                "first procurement matches recorded AFTER state");
        check(cash.acknowledgeVerified(server.world,firstTrade.id()),"verified first procurement is released");
        server.world.day=280;
        check(!KingdomNpcWorkshop.tick(server.world,villager,w),"buys missing second material");
        check(cash.find(buyer.id()).cash()==480,"money debited only with goods");
        var secondTrade=cash.trades().stream().filter(t->t.state()==KingdomEnterpriseData.TradeState.NEEDS_RECONCILIATION)
                .findFirst().orElseThrow();
        check(cash.assessTrade(server.world,secondTrade.id())==TradeWriteAheadJournal.RecoveryAssessment.AFTER,
                "second procurement matches recorded AFTER state");
        check(cash.acknowledgeVerified(server.world,secondTrade.id()),"verified second procurement is released");
        server.world.day=320;
        check(!KingdomNpcWorkshop.tick(server.world,villager,w),"time starts only once refilled");
        check(source.getItem(0).getCount()==1&&source.getItem(1).getCount()==1,"physical stock feeds real recipe");
        server.world.day=519;
        check(!KingdomNpcWorkshop.tick(server.world,villager,w),"no premature second batch");
        server.world.day=520;
        check(KingdomNpcWorkshop.tick(server.world,villager,w),"second batch produces only after time");
        check(finished.getItem(0).getCount()==8,"crafted stock accumulates, no virtual production");
        // Existing product offer is backed by finished-stock items, not a phantom order.
        var torchOffer=cash.offers().stream().filter(o->o.itemId().equals("minecraft:torch"))
                .findFirst().orElse(null);
        check(torchOffer!=null&&employee.equals(torchOffer.stockWorker()),"NPC listed real finished goods");
        var customerId=UUID.randomUUID();var customer=new ServerPlayer(server,customerId);
        customer.inventory.wallet.money=1000;
        var customerFirm=cash.create(customerId,"General shop");
        var customerChest=new Chest();var customerPos=new BlockPos(4,0,0);
        server.world.entities.put(customerPos,customerChest);customer.pos=customerPos;
        check(cash.bindCounter(customer,customerPos),"new buyer counter");
        check(cash.deposit(customer,300),"third-party buyer funds");
        // Buyer must physically reach both registered counters; in this local setup
        // the two desks are four blocks apart and accessible without a courier.
        customer.pos=new BlockPos(2,0,0);
        check(cash.buy(customer,torchOffer.id(),3),"workshop stock sold for real funds");
        check(finished.getItem(0).getCount()==5,"buyer removed real finished stock");
        check(customerChest.getItem(0).getCount()==3,"customer received genuine crafted items");
        check(cash.find(customerFirm.id()).cash()==270&&cash.find(buyer.id()).cash()==510,
                "money moved from customer firm to producer firm");
        var customerTrade=cash.trades().stream().filter(t->t.state()==KingdomEnterpriseData.TradeState.NEEDS_RECONCILIATION)
                .findFirst().orElseThrow();
        check(cash.assessTrade(server.world,customerTrade.id())==TradeWriteAheadJournal.RecoveryAssessment.AFTER,
                "customer trade matches recorded AFTER state");
        check(cash.acknowledgeVerified(server.world,customerTrade.id()),"verified customer trade is released");
        long previous=cash.find(customerFirm.id()).cash();
        for(int i=0;i<customerChest.getContainerSize();i++)
            customerChest.setItem(i,new ItemStack(BuiltInRegistries.ITEM.get("minecraft:coal"),64));
        check(!cash.buy(customer,torchOffer.id(),1),"full purchaser inventory rejects sale");
        check(cash.find(customerFirm.id()).cash()==previous&&finished.getItem(0).getCount()==5,
                "failed sale cannot lose source stock or customer money");
        check(!cash.buyForNpc(server.world,UUID.randomUUID(),buyer.id(),o1.id(),1),
                "unregistered villager cannot spend corporate money");
        long total=cash.find(buyer.id()).cash()+cash.find(shop.id()).cash();
        check(total==530,"NPC-local trade plus sold goods preserves the expected receipts");
        check(total+cash.find(customerFirm.id()).cash()==800,"every business copper balance conserved");
        var before=plans.save(new CompoundTag());
        Method load=KingdomNpcWorkshopData.class.getDeclaredMethod("load",CompoundTag.class);
        load.setAccessible(true);var saved=(KingdomNpcWorkshopData)load.invoke(null,before);
        check(saved.plan(employee)!=null&&saved.enabled(employee),"work plan and hysteresis persist");
        check(saved.plan(employee).grid().get(0).equals("minecraft:charcoal"),"input blueprint persists");
        check(plans.rotatePrice(employee),"owner can reprice workshop offer");
        check(plans.plan(employee).salePrice()==50,"price rotation persists in memory");
        check(cash.publishWorkshop(server,employee,buyer.id(),50).unitPrice()==50,
                "finished output offer re-prices without changing inventory");
        check(plans.rotateStock(employee),"owner can change production targets");
        check(plans.plan(employee).low()==8&&plans.plan(employee).high()==64,
                "bounded min-max stock settings");
        var updated=(KingdomNpcWorkshopData)load.invoke(null,plans.save(new CompoundTag()));
        check(updated.plan(employee).salePrice()==50&&updated.plan(employee).high()==64,
                "price and stock thresholds survive world restart");
        System.out.println("PASS: NPC workshop real recipe/time/stock, physical inputs and output, adjacent NPC purchases, money conservation, save/load");
    }
}
