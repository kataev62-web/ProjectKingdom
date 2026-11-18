package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;
import java.util.function.Predicate;
import org.junit.jupiter.api.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraftforge.items.*;

/** Run against archived PR72, never silently count as passing RC tests. */
public class IndependentSmithyPhysicalTransferReviewTest {
    @BeforeAll static void bootstrap(){
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
    }
    private static boolean deliver(Container target,ItemStack goods)throws Exception{
        Method m=KingdomTfcSmithWork.class.getDeclaredMethod("intoChest",Container.class,ItemStack.class);
        m.setAccessible(true);return (boolean)m.invoke(null,target,goods);
    }
    public static class Machine extends BlockEntity {
        public Machine(){super(BlockEntityType.CHEST,BlockPos.ZERO,Blocks.CHEST.defaultBlockState());}
        public boolean isItemValid(int slot,ItemStack item){return true;}
    }
    private static boolean load(Container raw,ItemStackHandler handler)throws Exception{
        Method m=KingdomTfcSmithWork.class.getDeclaredMethod("fromChest",Container.class,BlockEntity.class,IItemHandlerModifiable.class,int.class,Predicate.class);
        m.setAccessible(true);return (boolean)m.invoke(null,raw,new Machine(),handler,0,(Predicate<ItemStack>)s->true);
    }
    @Test void deliveryMustNotReportSuccessWhenWriteWasRejected()throws Exception{
        var target=new SimpleContainer(1){@Override public void setItem(int slot,ItemStack stack){}};
        boolean delivered=deliver(target,new ItemStack(Items.COPPER_INGOT));
        assertFalse(delivered,"Otherwise tick clears the anvil and credits an undelivered product");
    }
    @Test void mergeIntoCopyingInventoryMustActuallyIncreaseStock()throws Exception{
        var target=new SimpleContainer(1){@Override public ItemStack getItem(int slot){return super.getItem(slot).copy();}};
        target.setItem(0,new ItemStack(Items.COPPER_INGOT,10));
        assertTrue(deliver(target,new ItemStack(Items.COPPER_INGOT,2)));
        assertEquals(12,target.getItem(0).getCount(),"No source removal or progress credit before actual stock increase");
    }
    @Test void rejectedForgeInsertionMustNotConsumeRawMetal()throws Exception{
        var raw=new SimpleContainer(new ItemStack(Items.COPPER_INGOT,10));
        var handler=new ItemStackHandler(1){@Override public void setStackInSlot(int slot,ItemStack stack){}};
        load(raw,handler);
        assertEquals(10,raw.getItem(0).getCount()+handler.getStackInSlot(0).getCount(),"Conservation after rejected insertion");
    }
    @Test void copyingRawInventoryMustNotDuplicateMetal()throws Exception{
        var raw=new SimpleContainer(1){@Override public ItemStack getItem(int slot){return super.getItem(slot).copy();}};
        raw.setItem(0,new ItemStack(Items.COPPER_INGOT,10));
        var handler=new ItemStackHandler(1);
        load(raw,handler);
        assertEquals(10,raw.getItem(0).getCount()+handler.getStackInSlot(0).getCount(),"Conservation after loading actual forge slot");
    }
    @Test void copyingSourceRetainsNestedNbtAndExactQuantity()throws Exception{
        var raw=new SimpleContainer(1){@Override public ItemStack getItem(int slot){return super.getItem(slot).copy();}};
        var metal=new ItemStack(Items.COPPER_INGOT,10);
        var state=new net.minecraft.nbt.CompoundTag();state.putString("stage","partial");state.putFloat("temperature",900);
        metal.getOrCreateTag().put("reviewSyntheticState",state);
        raw.setItem(0,metal.copy());var forge=new ItemStackHandler(1);
        assertTrue(load(raw,forge));
        assertEquals(9,raw.getItem(0).getCount());assertEquals(1,forge.getStackInSlot(0).getCount());
        assertEquals(metal.getTag(),forge.getStackInSlot(0).getTag());
        assertEquals(metal.getTag(),raw.getItem(0).getTag());
    }
    @Test void fullWarehousePreservesProductAndNestedNbt()throws Exception{
        var product=new ItemStack(Items.COPPER_INGOT,2);
        product.getOrCreateTag().putString("reviewSyntheticState","worked");
        var target=new SimpleContainer(new ItemStack(Items.STONE,64));
        assertFalse(deliver(target,product));
        assertEquals(2,product.getCount());assertEquals("worked",product.getTag().getString("reviewSyntheticState"));
        assertEquals(64,target.getItem(0).getCount());assertTrue(target.getItem(0).is(Items.STONE));
    }
}
