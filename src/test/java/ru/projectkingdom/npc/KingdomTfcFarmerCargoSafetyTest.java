package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Behavioral cargo tests do not substitute for an actual Forge + TFC in-world test. */
final class KingdomTfcFarmerCargoSafetyTest {
    @BeforeAll static void bootstrap(){
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static KingdomNpcData.Worker worker(){
        return new KingdomNpcData().add(UUID.randomUUID(),"Field worker","minecraft:overworld",0);
    }

    @Test void plannedHarvestMustFitSavedSlotCountAndPreservePerItemNbt(){
        var w=worker();
        for(int i=0;i<36;i++){
            ItemStack produce=new ItemStack(Items.DIAMOND_SWORD);
            produce.getOrCreateTag().putInt("field_crop_batch",i);
            w.fieldInventory.add(produce);
        }
        var different=new ItemStack(Items.DIAMOND_SWORD);
        different.getOrCreateTag().putInt("field_crop_batch",36);
        assertFalse(NpcFieldWork.canFit(w,List.of(different)),
                "A 37th distinct ItemStack would not survive the NPC's 36-slot NBT save");
        var identical=w.fieldInventory.get(0).copy();
        assertFalse(NpcFieldWork.canFit(w,List.of(identical)),
                "A full unstackable stack must not be merged by item ID alone");
        assertEquals(36,w.fieldInventory.size());

        var saved=new KingdomNpcData();
        var original=saved.add(UUID.randomUUID(),"Saved farmer","minecraft:overworld",0);
        for(ItemStack item:w.fieldInventory)original.fieldInventory.add(item.copy());
        var copy=KingdomNpcData.load(saved.save(new CompoundTag()));
        var restored=copy.all().iterator().next();
        assertEquals(36,restored.fieldInventory.size());
        for(int i=0;i<36;i++)
            assertEquals(i,restored.fieldInventory.get(i).getTag().getInt("field_crop_batch"));
    }

    @Test void manyItemsInOneStackAreFineButDifferentNbtMustNotCombine(){
        var w=worker();
        var seeds=new ItemStack(Items.WHEAT_SEEDS,40);
        seeds.getOrCreateTag().putString("origin","farm-1");
        w.fieldInventory.add(seeds);
        var same=seeds.copy();same.setCount(24);
        assertTrue(NpcFieldWork.canFit(w,List.of(same)));
        var different=seeds.copy();different.getOrCreateTag().putString("origin","farm-2");
        assertTrue(NpcFieldWork.canFit(w,List.of(different)));
        assertFalse(NpcFieldWork.canFit(w,List.of(new ItemStack(Items.WHEAT_SEEDS,577))));
    }

    @Test void copiedContainerViewsStillReceiveActualWarehouseTransfer(){
        var c=new SimpleContainer(1){
            @Override public ItemStack getItem(int slot){return super.getItem(slot).copy();}
        };
        var earlier=new ItemStack(Items.WHEAT,60);
        earlier.getOrCreateTag().putString("batch","north-field");
        c.setItem(0,earlier);
        var cargo=earlier.copy();cargo.setCount(7);
        assertEquals(4,NpcFieldWork.transferToWarehouse(c,cargo));
        assertEquals(64,c.getItem(0).getCount());
        assertEquals(3,cargo.getCount());
        assertEquals("north-field",c.getItem(0).getTag().getString("batch"));
        assertEquals(0,NpcFieldWork.transferToWarehouse(c,cargo));
        assertEquals(3,cargo.getCount());
    }

    @Test void rejectedWarehouseWriteDoesNotDebitWorkerOrInventDelivery(){
        var rejected=new SimpleContainer(1){
            @Override public void setItem(int slot,ItemStack stack){/* inaccessible inventory */ }
            @Override public ItemStack getItem(int slot){return ItemStack.EMPTY;}
        };
        var crop=new ItemStack(Items.CARROT,3);
        crop.getOrCreateTag().putString("harvest","kept-with-nbt");
        assertEquals(0,NpcFieldWork.transferToWarehouse(rejected,crop));
        assertEquals(3,crop.getCount());
        assertEquals("kept-with-nbt",crop.getTag().getString("harvest"));
        assertTrue(rejected.isEmpty());
    }

    @Test void fullOrTooSmallWarehouseNeverTakesMoreThanActualCapacity(){
        var warehouse=new SimpleContainer(1){
            @Override public int getMaxStackSize(){return 2;}
        };
        var cargo=new ItemStack(Items.POTATO,5);
        assertEquals(2,NpcFieldWork.transferToWarehouse(warehouse,cargo));
        assertEquals(2,warehouse.getItem(0).getCount());
        assertEquals(3,cargo.getCount());
        assertEquals(0,NpcFieldWork.transferToWarehouse(warehouse,cargo));
        assertEquals(3,cargo.getCount());
    }

    @Test void farmingLifecycleUsesOnlyNativeCropsAndLoadedLegalPhysicalActions() throws Exception{
        String farm=Files.readString(Path.of(
            "src/main/java/ru/projectkingdom/npc/KingdomFarmWorker.java"));
        String stock=Files.readString(Path.of(
            "src/main/java/ru/projectkingdom/npc/NpcFieldWork.java"));
        String nutrients=Files.readString(Path.of(
            "src/main/java/ru/projectkingdom/npc/KingdomFarmNutrition.java"));
        assertTrue(farm.contains("KingdomTfcFieldRules.simpleCrop("));
        assertTrue(farm.contains("KingdomFarmClimate.read(level,pos,seedling).suitable()"));
        assertTrue(farm.contains("Block.getDrops(crop,level,pos,entity,npc,ItemStack.EMPTY)"));
        assertTrue(farm.contains("if(!NpcFieldWork.canFit(w,drops))") ||
            farm.contains("||!NpcFieldWork.canFit(w,drops)"));
        assertTrue(farm.contains("NpcFieldWork.takeSeedById(w,field.seedId())"));
        assertTrue(farm.contains("KingdomTerritories.get(level.getServer())"));
        assertTrue(farm.contains("level.hasChunkAt(crop)"));
        assertTrue(farm.contains("NpcFieldWork.canFit(w,List.of(proposed))"));
        assertTrue(nutrients.contains("NpcFieldWork.canFit(worker,java.util.List.of(one))"));
        assertTrue(stock.contains("KingdomFarmWorker.farm(level,w)"),
            "NPC farm owners must reserve seeds without a payroll record");
        assertTrue(stock.contains("transferToWarehouse(c,s)"));
        assertTrue(stock.contains("KingdomNpcData.get(level.getServer()).setDirty()"));
    }
}
