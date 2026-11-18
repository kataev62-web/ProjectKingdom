package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Regression for a destination slot accepting less than a full native output. */
final class KingdomSmithyPhysicalGuardsTest {
    private static Method insert;
    @BeforeAll static void setup() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        insert=KingdomTfcSmithWork.class.getDeclaredMethod(
                "intoChest",Container.class,ItemStack.class);
        insert.setAccessible(true);
    }
    private static boolean deliver(Container chest,ItemStack product)throws Exception {
        return (boolean)insert.invoke(null,chest,product);
    }
    @Test void nonStackableOutputCannotBeInsertedWithAnIllegalCount() throws Exception {
        var chest=new SimpleContainer(1);
        var doubleSword=new ItemStack(Items.DIAMOND_SWORD,2);
        assertFalse(deliver(chest,doubleSword));
        assertTrue(chest.isEmpty());
        assertEquals(2,doubleSword.getCount());
    }
    @Test void emptyWarehouseSlotCannotSilentlyClampOversizedProductionBatch() throws Exception {
        var chest=new SimpleContainer(1);
        var oversized=new ItemStack(Items.DIAMOND,65);
        assertFalse(deliver(chest,oversized));
        assertTrue(chest.isEmpty());
        assertEquals(65,oversized.getCount());
    }
    @Test void itemFitsOnlyWhenUnderBothContainerAndItemLimits() throws Exception {
        var limited=new SimpleContainer(1){
            @Override public int getMaxStackSize(){return 1;}
        };
        var two=new ItemStack(Items.DIAMOND,2);
        assertFalse(deliver(limited,two));
        assertTrue(limited.isEmpty());
        assertTrue(deliver(limited,new ItemStack(Items.DIAMOND,1)));
        assertEquals(1,limited.getItem(0).getCount());
    }
}
