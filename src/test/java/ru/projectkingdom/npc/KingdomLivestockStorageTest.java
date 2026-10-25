package ru.projectkingdom.npc;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomLivestockStorageTest {
    @Test void fullWarehouseDoesNotMintOrOverwriteProducts(){
        var store=new SimpleContainer(1);
        store.setItem(0,new ItemStack(Items.APPLE,64));
        var eggs=new ItemStack(Items.EGG,1);
        assertFalse(KingdomLivestockStorage.canInsert(store,eggs));
        assertFalse(KingdomLivestockStorage.insert(store,eggs));
        assertEquals(64,store.getItem(0).getCount());
        assertTrue(store.getItem(0).is(Items.APPLE));
    }

    @Test void realItemStacksMergeOnlyWithMatchingProductsAndRoom(){
        var store=new SimpleContainer(2);
        assertTrue(KingdomLivestockStorage.insert(store,new ItemStack(Items.EGG,2)));
        assertTrue(KingdomLivestockStorage.insert(store,new ItemStack(Items.EGG,3)));
        assertEquals(5,store.getItem(0).getCount());
        assertTrue(KingdomLivestockStorage.insert(store,new ItemStack(Items.BEEF,1)));
        assertTrue(store.getItem(1).is(Items.BEEF));
        assertEquals(5,store.getItem(0).getCount());
    }
}
