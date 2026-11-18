package ru.projectkingdom.npc;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;

class KingdomProfessionCargoBehaviorTest {
    @BeforeAll static void bootstrap(){
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void rejectedWriteKeepsCargo(){
        var destination=new SimpleContainer(1){
            @Override public void setItem(int slot,ItemStack item){}
        };
        var cargo=new ItemStack(Items.OAK_LOG,8);
        assertFalse(NpcFieldWork.storeCargo(destination,cargo));
        assertEquals(8,cargo.getCount());
        assertTrue(destination.getItem(0).isEmpty());
    }
    @Test void copiedInventoryViewReceivesGoodsWithNbt(){
        var destination=new SimpleContainer(1){
            @Override public ItemStack getItem(int slot){return super.getItem(slot).copy();}
        };
        var initial=new ItemStack(Items.OAK_LOG,60);
        initial.getOrCreateTag().putString("owner","test");
        destination.setItem(0,initial.copy());
        var cargo=initial.copy();cargo.setCount(8);
        assertTrue(NpcFieldWork.storeCargo(destination,cargo));
        assertEquals(64,destination.getItem(0).getCount());
        assertEquals(4,cargo.getCount());
        assertEquals("test",destination.getItem(0).getTag().getString("owner"));
        assertFalse(NpcFieldWork.storeCargo(destination,cargo));
        assertEquals(4,cargo.getCount());
    }
    @Test void partialWriteConsumesOnlyConfirmedGoods(){
        var destination=new SimpleContainer(1){
            @Override public void setItem(int slot,ItemStack item){
                ItemStack accepted=item.copy();accepted.setCount(Math.min(3,item.getCount()));
                super.setItem(slot,accepted);
            }
        };
        var cargo=new ItemStack(Items.OAK_LOG,8);
        assertTrue(NpcFieldWork.storeCargo(destination,cargo));
        assertEquals(3,destination.getItem(0).getCount());
        assertEquals(5,cargo.getCount());
    }
    @Test void rejectedManualPaymentAndRestartPreserveOldDebt() throws Exception {
        var worker=java.util.UUID.randomUUID();var firm=java.util.UUID.randomUUID();
        var row=new net.minecraft.nbt.CompoundTag();
        row.putUUID("Firm",firm);row.putUUID("Worker",worker);
        row.putLong("Rate",0);row.putLong("Day",7);row.putLong("Owed",123);
        row.putBoolean("Active",false);
        var jobs=new net.minecraft.nbt.ListTag();jobs.add(row);
        var root=new net.minecraft.nbt.CompoundTag();root.put("Jobs",jobs);
        var load=KingdomEnterprisePayrollData.class.getDeclaredMethod("load",net.minecraft.nbt.CompoundTag.class);
        load.setAccessible(true);
        var data=(KingdomEnterprisePayrollData)load.invoke(null,root);
        assertEquals(0,data.payNpcFromWallet(null,null));
        var restored=(KingdomEnterprisePayrollData)load.invoke(null,data.save(new net.minecraft.nbt.CompoundTag()));
        assertEquals(123,restored.totalDue(null,worker));
        assertEquals(7,restored.contract(firm,worker).lastDay());
        assertFalse(restored.contract(firm,worker).active());
    }
    @Test void switchingProfessionRetainsOldToolAndUsesCarriedAxe(){
        var w=new KingdomNpcData.Worker(1,java.util.UUID.randomUUID(),"Test","minecraft:overworld",0);
        w.fieldTool=new ItemStack(Items.IRON_PICKAXE);
        w.fieldInventory.add(new ItemStack(Items.IRON_AXE));
        assertTrue(KingdomTfcForestry.equipCarriedAxe(w,s->s.is(Items.IRON_AXE)));
        assertTrue(w.fieldTool.is(Items.IRON_AXE));
        assertEquals(1,w.fieldInventory.size());
        assertTrue(w.fieldInventory.get(0).is(Items.IRON_PICKAXE));
        assertTrue(KingdomTfcForestry.equipCarriedAxe(w,s->s.is(Items.IRON_AXE)));
        assertEquals(1,w.fieldInventory.size());
    }
}
