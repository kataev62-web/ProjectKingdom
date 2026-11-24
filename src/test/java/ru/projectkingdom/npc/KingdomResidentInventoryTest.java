package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.Test;

/**
 * The personal inventory of a resident.
 *
 * <p>The container of MCA has nine rows of three slots. The Kingdom must fill
 * it, take from it and mirror it without ever creating an item.
 */
final class KingdomResidentInventoryTest {

    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static KingdomNpcData.Worker worker() {
        return new KingdomNpcData.Worker(1, UUID.randomUUID(), "Resident", "minecraft:overworld", 0);
    }

    @Test void theInsertFillsTheStacksAndTheEmptySlots() {
        SimpleContainer bag = new SimpleContainer(9);
        ItemStack stack = new ItemStack(Items.WHEAT, 100);
        assertTrue(KingdomResidentInventory.insert(bag, stack), "the whole stack must fit");
        assertTrue(stack.isEmpty(), "nothing may be created");
        assertEquals(100, KingdomResidentInventory.count(bag, Items.WHEAT), "the count must be exact");
        assertEquals(64, bag.getItem(0).getCount(), "the first slot holds a full stack");
        assertEquals(36, bag.getItem(1).getCount(), "the rest goes into the second slot");
    }

    @Test void theInsertKeepsTheRestWhenTheBagIsFull() {
        SimpleContainer bag = new SimpleContainer(1);
        ItemStack stack = new ItemStack(Items.WHEAT, 100);
        assertFalse(KingdomResidentInventory.insert(bag, stack), "the bag is full");
        assertEquals(36, stack.getCount(), "the rest stays with the caller");
        assertEquals(64, KingdomResidentInventory.count(bag, Items.WHEAT), "the bag holds what it can");
    }

    @Test void theExtractTakesOnlyWhatMatches() {
        SimpleContainer bag = new SimpleContainer(4);
        bag.setItem(0, new ItemStack(Items.WHEAT, 10));
        bag.setItem(1, new ItemStack(Items.IRON_INGOT, 5));
        List<ItemStack> taken = KingdomResidentInventory.extract(bag, stack -> stack.is(Items.WHEAT), 4);
        assertEquals(1, taken.size(), "one stack was taken");
        assertEquals(4, taken.get(0).getCount(), "only the requested amount");
        assertEquals(6, KingdomResidentInventory.count(bag, Items.WHEAT), "the rest stays in the bag");
        assertEquals(5, KingdomResidentInventory.count(bag, Items.IRON_INGOT), "the other items stay");
    }

    @Test void theExtractNeverTakesMoreThanTheBagHolds() {
        SimpleContainer bag = new SimpleContainer(2);
        bag.setItem(0, new ItemStack(Items.WHEAT, 3));
        List<ItemStack> taken = KingdomResidentInventory.extract(bag, stack -> stack.is(Items.WHEAT), 10);
        int total = taken.stream().mapToInt(ItemStack::getCount).sum();
        assertEquals(3, total, "the bag cannot give more than it holds");
        assertEquals(0, KingdomResidentInventory.count(bag, Items.WHEAT), "the bag is empty now");
    }

    @Test void theMirrorOfTheWorkerHoldsWhatTheBagHolds() {
        SimpleContainer bag = new SimpleContainer(9);
        bag.setItem(0, new ItemStack(Items.IRON_PICKAXE));
        bag.setItem(1, new ItemStack(Items.WHEAT, 12));
        KingdomNpcData.Worker worker = worker();
        KingdomResidentInventory.syncToWorker(worker, bag);
        assertFalse(worker.fieldTool.isEmpty(), "the tool must be in the mirror");
        assertTrue(worker.fieldTool.is(Items.IRON_PICKAXE), "the tool of the mirror must be the real one");
        assertEquals(1, worker.fieldInventory.size(), "the cargo holds the rest");
        assertEquals(12, worker.fieldInventory.get(0).getCount(), "the cargo keeps the count");
    }

    @Test void theMigrationMovesTheOldBagIntoTheRealInventory() {
        KingdomNpcData.Worker worker = worker();
        worker.fieldTool = new ItemStack(Items.IRON_PICKAXE);
        worker.fieldInventory = new java.util.ArrayList<>(List.of(new ItemStack(Items.WHEAT, 20)));
        SimpleContainer bag = new SimpleContainer(9);

        int stored = KingdomResidentInventory.loadFromWorker(worker, bag);
        assertEquals(2, stored, "the tool and the cargo must be stored");
        assertEquals(20, KingdomResidentInventory.count(bag, Items.WHEAT), "the cargo is in the bag");
        assertEquals(1, KingdomResidentInventory.count(bag, Items.IRON_PICKAXE), "the tool is in the bag");
        assertTrue(worker.fieldTool.isEmpty(), "the mirror is empty after the migration");
        assertTrue(worker.fieldInventory.isEmpty(), "the cargo of the mirror is empty");
    }

    @Test void theMigrationKeepsWhatDoesNotFit() {
        KingdomNpcData.Worker worker = worker();
        worker.fieldInventory = new java.util.ArrayList<>(List.of(new ItemStack(Items.WHEAT, 200)));
        SimpleContainer bag = new SimpleContainer(1);

        KingdomResidentInventory.loadFromWorker(worker, bag);
        assertEquals(64, KingdomResidentInventory.count(bag, Items.WHEAT), "the bag holds what it can");
        assertEquals(1, worker.fieldInventory.size(), "the rest stays in the mirror");
        assertEquals(136, worker.fieldInventory.get(0).getCount(), "nothing may be lost");
    }
}
