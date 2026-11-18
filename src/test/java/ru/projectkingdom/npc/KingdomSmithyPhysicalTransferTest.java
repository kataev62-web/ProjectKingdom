package ru.projectkingdom.npc;

import java.lang.reflect.Method;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomSmithyPhysicalTransferTest {
    private static Method insert;

    @BeforeAll static void setup() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        insert=KingdomTfcSmithWork.class.getDeclaredMethod("intoChest",Container.class,ItemStack.class);
        insert.setAccessible(true);
    }
    private static boolean put(Container chest,ItemStack product)throws Exception{
        return (boolean)insert.invoke(null,chest,product);
    }

    @Test void fullWarehouseMustNotAcceptOrPartiallyConsumeRealOutput()throws Exception{
        var full=new SimpleContainer(1);
        full.setItem(0,new ItemStack(Items.COPPER_INGOT,64));
        ItemStack product=new ItemStack(Items.DIAMOND_SWORD);
        product.getOrCreateTag().putString("smith", "native_tfc_output");
        assertFalse(put(full,product));
        assertEquals(1,product.getCount());
        assertEquals(64,full.getItem(0).getCount());
        assertTrue(full.getItem(0).is(Items.COPPER_INGOT));

        var nearlyFull=new SimpleContainer(1);
        nearlyFull.setItem(0,new ItemStack(Items.COPPER_INGOT,63));
        assertFalse(put(nearlyFull,new ItemStack(Items.COPPER_INGOT,2)));
        assertEquals(63,nearlyFull.getItem(0).getCount());
    }

    @Test void warehouseTakesExactNativeStackWithNbtNeverMintsAnExtraItem()throws Exception {
        var empty=new SimpleContainer(1);
        var output=new ItemStack(Items.DIAMOND_SWORD);
        output.getOrCreateTag().putString("tfc", "a-real-smithing-result");
        assertTrue(put(empty,output));
        ItemStack delivered=empty.getItem(0);
        assertNotSame(output,delivered);
        assertEquals(1,delivered.getCount());
        assertTrue(ItemStack.isSameItemSameTags(output,delivered));
        assertEquals("a-real-smithing-result",delivered.getTag().getString("tfc"));
    }

    @Test void exactCountMergerPreservesWarehouseItems()throws Exception{
        var chest=new SimpleContainer(1);
        chest.setItem(0,new ItemStack(Items.COPPER_INGOT,60));
        ItemStack output=new ItemStack(Items.COPPER_INGOT,4);
        assertTrue(put(chest,output));
        assertEquals(64,chest.getItem(0).getCount());
        assertEquals(4,output.getCount(),"Input is only cleared by the caller after physical insertion");
        assertFalse(put(chest,new ItemStack(Items.COPPER_INGOT,1)));
        assertEquals(64,chest.getItem(0).getCount());
    }
}
