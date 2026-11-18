package ru.projectkingdom.npc;

import java.lang.reflect.Method;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Behavioral regression of the actual private native-to-native and anvil-to-chest methods. */
final class KingdomSmithyVerifiedTransfersTest {
    private static Method move,deliver;
    public static class Machine extends BlockEntity {
        public Machine(){super(BlockEntityType.CHEST,BlockPos.ZERO,Blocks.CHEST.defaultBlockState());}
        public boolean isItemValid(int slot,ItemStack stack){return !stack.isEmpty();}
    }

    @BeforeAll static void bootstrap()throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        move=KingdomTfcSmithWork.class.getDeclaredMethod("moveNative",
            BlockEntity.class,IItemHandlerModifiable.class,int.class,
            BlockEntity.class,IItemHandlerModifiable.class,int.class);
        move.setAccessible(true);
        deliver=KingdomTfcSmithWork.class.getDeclaredMethod("deliverNative",
            Container.class,BlockEntity.class,IItemHandlerModifiable.class);
        deliver.setAccessible(true);
    }
    private static boolean transfer(ItemStackHandler source,ItemStackHandler destination)throws Exception{
        return (boolean)move.invoke(null,new Machine(),source,0,new Machine(),destination,0);
    }
    private static boolean warehouse(Container chest,ItemStackHandler anvil)throws Exception{
        return (boolean)deliver.invoke(null,chest,new Machine(),anvil);
    }
    @Test void nativeHandlerReturningCopiesMustStillMoveExactlyOneStack()throws Exception {
        var from=new ItemStackHandler(1){
            @Override public ItemStack getStackInSlot(int slot){
                return super.getStackInSlot(slot).copy();
            }
        };
        var to=new ItemStackHandler(1){
            @Override public ItemStack getStackInSlot(int slot){
                return super.getStackInSlot(slot).copy();
            }
        };
        var original=new ItemStack(Items.COPPER_INGOT,1);
        original.getOrCreateTag().putString("TFC","forging:partial");
        from.setStackInSlot(0,original.copy());
        assertTrue(transfer(from,to));
        assertTrue(from.getStackInSlot(0).isEmpty());
        assertEquals(1,to.getStackInSlot(0).getCount());
        assertEquals("forging:partial",to.getStackInSlot(0).getTag().getString("TFC"));
    }
    @Test void rejectedNativeInsertionDoesNotClearSource()throws Exception {
        var from=new ItemStackHandler(1);
        from.setStackInSlot(0,new ItemStack(Items.COPPER_INGOT,1));
        var to=new ItemStackHandler(1){
            @Override public void setStackInSlot(int slot,ItemStack stack){}
        };
        assertFalse(transfer(from,to));
        assertEquals(1,from.getStackInSlot(0).getCount());
        assertTrue(to.getStackInSlot(0).isEmpty());
    }
    @Test void rejectedNativeExtractionMustRollbackDestination()throws Exception {
        var from=new ItemStackHandler(1){
            @Override public void setStackInSlot(int slot,ItemStack stack){
                if(!stack.isEmpty())super.setStackInSlot(slot,stack);
            }
        };
        from.setStackInSlot(0,new ItemStack(Items.COPPER_INGOT,1));
        var to=new ItemStackHandler(1);
        assertFalse(transfer(from,to));
        assertEquals(1,from.getStackInSlot(0).getCount());
        assertTrue(to.getStackInSlot(0).isEmpty());
    }
    @Test void refusedAnvilClearMustNotLeaveDuplicateDelivery()throws Exception {
        var from=new ItemStackHandler(1){
            @Override public void setStackInSlot(int slot,ItemStack stack){
                if(!stack.isEmpty())super.setStackInSlot(slot,stack);
            }
        };
        from.setStackInSlot(0,new ItemStack(Items.DIAMOND_SWORD));
        var chest=new SimpleContainer(1);
        assertFalse(warehouse(chest,from));
        assertTrue(chest.getItem(0).isEmpty());
        assertEquals(1,from.getStackInSlot(0).getCount());
    }
    @Test void deliveryFromCopyingAnvilStackKeepsNativeNbtExactly()throws Exception {
        var from=new ItemStackHandler(1){
            @Override public ItemStack getStackInSlot(int slot){
                return super.getStackInSlot(slot).copy();
            }
        };
        var sword=new ItemStack(Items.DIAMOND_SWORD);
        sword.getOrCreateTag().putString("TFC","smith:forged");
        from.setStackInSlot(0,sword);
        var chest=new SimpleContainer(1);
        assertTrue(warehouse(chest,from));
        assertTrue(from.getStackInSlot(0).isEmpty());
        assertEquals("smith:forged",chest.getItem(0).getTag().getString("TFC"));
    }
}
