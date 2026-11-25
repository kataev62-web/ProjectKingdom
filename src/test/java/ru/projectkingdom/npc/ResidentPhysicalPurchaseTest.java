package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResidentPhysicalPurchaseTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    private static long value(ItemStack stack) {
        if (stack.is(Items.GOLD_NUGGET)) return 10_000;
        if (stack.is(Items.IRON_NUGGET)) return 100;
        if (stack.is(Items.COPPER_INGOT)) return 1;
        return 0;
    }
    private static List<ItemStack> purse(ItemStack... stacks) { return new ArrayList<>(List.of(stacks)); }
    private static SimpleContainer stock(int count) {
        var container = new SimpleContainer(4); container.setItem(0,new ItemStack(Items.WHEAT,count)); return container;
    }
    @Test void hundredCopperReachSellerUnchangedAndGoodsReachBagExactlyOnce() {
        var tagged = new ItemStack(Items.COPPER_INGOT,64); tagged.getOrCreateTag().putString("origin","wage");
        var purse = purse(tagged,new ItemStack(Items.COPPER_INGOT,36));
        var seller=stock(3); var bag=new SimpleContainer(2);
        var plan=ResidentPhysicalPurchase.prepare(purse,seller,bag,s -> s.is(Items.WHEAT),2,100,ResidentPhysicalPurchaseTest::value);
        assertNotNull(plan); assertEquals(3,seller.getItem(0).getCount());
        assertTrue(plan.commit()); assertFalse(plan.commit());
        assertEquals(1,seller.getItem(0).getCount()); assertEquals(2,bag.getItem(0).getCount());
        assertEquals(64,seller.getItem(1).getCount()); assertEquals("wage",seller.getItem(1).getTag().getString("origin"));
        assertEquals(36,seller.getItem(2).getCount()); assertTrue(seller.getItem(2).is(Items.COPPER_INGOT));
        assertTrue(purse.stream().allMatch(ItemStack::isEmpty));
    }
    @Test void noExactChangeAndFullBagAndFullCashboxLeaveEverythingUntouched() {
        var purse=purse(new ItemStack(Items.IRON_NUGGET)); var seller=stock(3); var bag=new SimpleContainer(1);
        assertNull(ResidentPhysicalPurchase.prepare(purse,seller,bag,s -> s.is(Items.WHEAT),1,99,ResidentPhysicalPurchaseTest::value));
        bag.setItem(0,new ItemStack(Items.STONE,64));
        assertNull(ResidentPhysicalPurchase.prepare(purse,seller,bag,s -> s.is(Items.WHEAT),1,100,ResidentPhysicalPurchaseTest::value));
        var full=new SimpleContainer(1); full.setItem(0,new ItemStack(Items.WHEAT,3));
        assertNull(ResidentPhysicalPurchase.prepare(purse,full,new SimpleContainer(1),s -> s.is(Items.WHEAT),1,100,ResidentPhysicalPurchaseTest::value));
        assertEquals(1,purse.get(0).getCount()); assertEquals(3,seller.getItem(0).getCount());
    }
    @Test void staleInventoryCannotCommitAndWriteRefusalRollsBackBothSides() {
        var purse=purse(new ItemStack(Items.COPPER_INGOT,10)); var seller=stock(3);
        var refusing=new SimpleContainer(1) { @Override public void setItem(int slot,ItemStack stack) {} };
        var plan=ResidentPhysicalPurchase.prepare(purse,seller,refusing,s -> s.is(Items.WHEAT),1,2,ResidentPhysicalPurchaseTest::value);
        assertNotNull(plan); assertFalse(plan.commit());
        assertEquals(3,seller.getItem(0).getCount()); assertEquals(10,purse.get(0).getCount());
        var stale=ResidentPhysicalPurchase.prepare(purse,seller,new SimpleContainer(1),s -> s.is(Items.WHEAT),1,2,ResidentPhysicalPurchaseTest::value);
        seller.getItem(0).shrink(1);
        assertFalse(stale.commit()); assertEquals(2,seller.getItem(0).getCount());
    }
    @Test void writeThenThrowRestoresGoodsCoinsAndPurse() {
        var purse=purse(new ItemStack(Items.COPPER_INGOT,10)); var seller=stock(3);
        var bag=new SimpleContainer(1) {
            boolean failed;
            @Override public void setItem(int slot,ItemStack stack) {
                super.setItem(slot,stack);
                if (!failed) {failed=true;throw new IllegalStateException("after write");}
            }
        };
        var plan=ResidentPhysicalPurchase.prepare(purse,seller,bag,s -> s.is(Items.WHEAT),1,2,ResidentPhysicalPurchaseTest::value);
        assertFalse(plan.commit()); assertEquals(3,seller.getItem(0).getCount());
        assertTrue(bag.isEmpty()); assertEquals(10,purse.get(0).getCount());
    }
    @Test void failedRollbackIsExplicitAndCannotBeRetried() {
        var purse=purse(new ItemStack(Items.COPPER_INGOT,10));
        var seller=new SimpleContainer(2) {
            boolean debited;
            @Override public void setItem(int slot,ItemStack stack) {
                if(debited&&slot==0&&stack.getCount()==3)return;
                super.setItem(slot,stack);
                if(slot==0&&stack.getCount()==2)debited=true;
            }
        };
        seller.setItem(0,new ItemStack(Items.WHEAT,3));
        var refusing=new SimpleContainer(1) { @Override public void setItem(int slot,ItemStack stack) {} };
        var plan=ResidentPhysicalPurchase.prepare(purse,seller,refusing,s -> s.is(Items.WHEAT),1,2,ResidentPhysicalPurchaseTest::value);
        assertThrows(IllegalStateException.class,plan::commit);assertFalse(plan.commit());
        assertEquals(10,purse.get(0).getCount());
    }
    @Test void repeatedWagesMoveOnlyNewCoinsIntoOneSavedPurse() {
        var source=new SimpleContainer(1);source.setItem(0,new ItemStack(Items.COPPER_INGOT,30));
        var purse=purse(new ItemStack(Items.COPPER_INGOT,5));
        var first=ResidentPhysicalPurchase.wage(purse,source,7,s -> s.is(Items.COPPER_INGOT),ResidentPhysicalPurchaseTest::value);
        assertEquals(7,first.paid());assertTrue(first.plan().commit());assertFalse(first.plan().commit());
        var next=ResidentPhysicalPurchase.wage(purse,source,3,s -> s.is(Items.COPPER_INGOT),ResidentPhysicalPurchaseTest::value);
        assertTrue(next.plan().commit()); assertEquals(15,purse.get(0).getCount()); assertEquals(20,source.getItem(0).getCount());
        assertNull(ResidentPhysicalPurchase.wage(purse,source,0,s -> true,ResidentPhysicalPurchaseTest::value));
    }
    @Test void automaticWagePreservesExactCopperAndTagsAndRefusesUnbackedChange() {
        var cash=new SimpleContainer(2);
        var coins=new ItemStack(Items.COPPER_INGOT,64);coins.getOrCreateTag().putString("origin","company");
        cash.setItem(0,coins);cash.setItem(1,new ItemStack(Items.COPPER_INGOT,36));
        var purse=purse();
        var plan=ResidentPhysicalPurchase.cashWage(purse,cash,100,ResidentPhysicalPurchaseTest::value);
        assertNotNull(plan);assertTrue(plan.commit());assertTrue(cash.isEmpty());
        assertEquals(2,purse.size());assertEquals(64,purse.get(0).getCount());
        assertEquals("company",purse.get(0).getTag().getString("origin"));
        assertTrue(purse.stream().allMatch(s -> s.is(Items.COPPER_INGOT)));
        cash.setItem(0,new ItemStack(Items.GOLD_NUGGET));
        assertNull(ResidentPhysicalPurchase.cashWage(purse,cash,100,ResidentPhysicalPurchaseTest::value));
        assertEquals(1,cash.getItem(0).getCount());
    }
    @Test void rejectedDeathDropsRemainOwnedAndAcceptedDropsCannotRepeat() {
        var owned=purse(new ItemStack(Items.WHEAT,3),new ItemStack(Items.COPPER_INGOT,5));
        var accepted=new ArrayList<ItemStack>();
        assertFalse(ResidentOwnedDrops.release(owned,stack -> {
            if(stack.is(Items.WHEAT))return false;
            accepted.add(stack);return true;
        }));
        assertEquals(1,owned.size());assertEquals(1,accepted.size());
        assertTrue(ResidentOwnedDrops.release(owned,stack -> {accepted.add(stack);return true;}));
        assertEquals(2,accepted.size());assertTrue(ResidentOwnedDrops.release(owned,stack -> fail("repeat")));
    }
    @Test void inheritanceMovesActualBagAndExtrasWithoutCountingMirrorAndFullHeirBlocks() {
        var donor=stock(3);var extras=purse(new ItemStack(Items.COPPER_INGOT,5));var heir=new SimpleContainer(2);
        var transfer=ResidentPhysicalPurchase.inheritance(extras,donor,heir);
        assertTrue(transfer.commit()); assertFalse(transfer.commit());assertTrue(donor.isEmpty());assertTrue(extras.isEmpty());
        assertEquals(3,heir.getItem(0).getCount());assertEquals(5,heir.getItem(1).getCount());
        var full=new SimpleContainer(1); full.setItem(0,new ItemStack(Items.STONE,64));
        assertNull(ResidentPhysicalPurchase.inheritance(purse(new ItemStack(Items.COPPER_INGOT)),stock(3),full));
    }
    @Test void durableEvidenceQuarantinesRestartEvenAfterReceipt(@TempDir java.nio.file.Path path) throws Exception {
        var journal=new ResidentPurchaseJournal(path);assertFalse(journal.blocked());
        CompoundTag evidence=new CompoundTag(); evidence.putString("Item","minecraft:wheat");
        evidence.putUUID("Operation",UUID.randomUUID());
        assertTrue(journal.prepare(evidence));journal.finish(true);assertFalse(journal.blocked());
        assertFalse(journal.prepare(evidence));
        var restarted=new ResidentPurchaseJournal(path);assertTrue(restarted.blocked());assertFalse(restarted.prepare(evidence));
    }
    @Test void wrongActiveToolStillRequiresTheProfessionTool() {
        var worker=new KingdomNpcData.Worker(1,UUID.randomUUID(),"Resident","minecraft:overworld",0);
        worker.fieldTool=new ItemStack(Items.IRON_SHOVEL);
        worker.fieldInventory.add(new ItemStack(Items.WHEAT));
        worker.job="miner";assertEquals("minecraft:iron_pickaxe",KingdomResidentLife.missingTool(worker));
        worker.job="lumberjack";assertEquals("minecraft:iron_axe",KingdomResidentLife.missingTool(worker));
        worker.job="farmer";assertEquals("minecraft:iron_hoe",KingdomResidentLife.missingTool(worker));
        worker.job="courier";assertNull(KingdomResidentLife.missingTool(worker));
    }
    @Test void appropriateSecondaryToolsAvoidDuplicatePurchasesWithoutMovingInventory() {
        var worker=new KingdomNpcData.Worker(1,UUID.randomUUID(),"Resident","minecraft:overworld",0);
        var shovel=new ItemStack(Items.IRON_SHOVEL);
        worker.fieldTool=shovel;
        var tools=List.of(new ItemStack(Items.IRON_PICKAXE),new ItemStack(Items.IRON_AXE),new ItemStack(Items.IRON_HOE));
        worker.fieldInventory.addAll(tools);
        for(var job:List.of("miner","lumberjack","farmer")) {
            worker.job=job;assertNull(KingdomResidentLife.missingTool(worker));
            assertSame(shovel,worker.fieldTool);assertEquals(tools,worker.fieldInventory);
        }
        worker.fieldInventory.clear();worker.fieldTool=tools.get(0);worker.job="miner";
        assertNull(KingdomResidentLife.missingTool(worker));
        worker.fieldTool=tools.get(1);worker.job="lumberjack";assertNull(KingdomResidentLife.missingTool(worker));
        worker.fieldTool=tools.get(2);worker.job="farmer";assertNull(KingdomResidentLife.missingTool(worker));
    }
    @Test void requestDeduplicatesAfterRestartAndDoesNotInventEmployment() {
        var data=new KingdomResidentDevelopment();UUID resident=UUID.randomUUID();
        var request=new KingdomResidentLife.Decision(KingdomResidentLife.Action.APPLY_FOR_VACANCY,
                UUID.randomUUID(),"miner","",0,10,"request");
        assertTrue(KingdomResidentLife.recordRequest(data,resident,request));
        assertFalse(KingdomResidentLife.recordRequest(data,resident,request));
        var loaded=KingdomResidentDevelopment.load(data.save(new CompoundTag()));
        assertFalse(KingdomResidentLife.recordRequest(loaded,resident,request));
        assertEquals(0,loaded.removeExpiredGoals(resident,100));
        assertEquals(1,loaded.goalsOf(resident).size());assertTrue(loaded.careerOf(resident).isEmpty());
    }
}
