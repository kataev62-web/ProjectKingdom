package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

final class KingdomMultiStorageLotTest {
    private static ItemStack foodLot(long freshness) {
        var item=new ItemStack(Items.APPLE,5);
        CompoundTag tag=new CompoundTag();
        tag.putLong("tfc:test_freshness",freshness);
        item.setTag(tag);
        return item;
    }
    @Test void lotsAreDistinctWhileInventoryCountsBoth() {
        var chest=new SimpleContainer(12);
        chest.setItem(9,foodLot(100));
        chest.setItem(10,foodLot(200));
        assertEquals(10,KingdomEnterpriseInternalSupply.countId(chest,"minecraft:apple",9));
        assertEquals(0,KingdomEnterpriseInternalSupply.plainCount(chest,"minecraft:apple",9));
        var filled=new SimpleContainer(11);
        filled.setItem(9,foodLot(200));
        filled.setItem(10,new ItemStack(Items.DIRT,64));
        assertEquals(0,KingdomEnterpriseInternalSupply.capacity(filled,foodLot(100),9));
        assertEquals(59,KingdomEnterpriseInternalSupply.capacity(filled,foodLot(200),9));
    }

    @Test void exactTfcLotSurvivesSaveReloadWithoutSwapping() throws Exception {
        UUID enterprise=UUID.randomUUID();
        var ledger=new KingdomSupplyOrders();
        var from=new BlockPos(1,64,1);var to=new BlockPos(2,64,1);
        var sourceLot=foodLot(100);
        var order=ledger.createInternal(enterprise,"minecraft:overworld","minecraft:apple",
                from,to,3,sourceLot);
        assertNotNull(order);
        assertTrue(order.matchesLot(foodLot(100)));
        assertFalse(order.matchesLot(foodLot(101)));
        assertFalse(order.matchesLot(new ItemStack(Items.APPLE,3)));
        UUID id=order.id;
        CompoundTag saved=ledger.save(new CompoundTag());
        Method loader=KingdomSupplyOrders.class.getDeclaredMethod("load",CompoundTag.class);
        loader.setAccessible(true);
        var restored=(KingdomSupplyOrders)loader.invoke(null,saved);
        var loaded=restored.find(id);
        assertNotNull(loaded);
        assertTrue(loaded.matchesLot(foodLot(100)));
        assertFalse(loaded.matchesLot(foodLot(200)));
        assertEquals(3,restored.reserved("minecraft:overworld",from,"minecraft:apple"));
    }

    @Test void unfinishedPhysicalTransferRequiresExplicitRecovery() throws Exception {
        var ledger=new KingdomSupplyOrders();
        UUID enterprise=UUID.randomUUID();UUID courier=UUID.randomUUID();
        var from=new BlockPos(1,64,1);var to=new BlockPos(2,64,1);
        var order=ledger.createInternal(enterprise,"minecraft:overworld","minecraft:apple",
                from,to,4,foodLot(123));
        assertNotNull(order);
        assertTrue(ledger.assign(order.id,courier));
        assertFalse(ledger.cancelInternalUntouched(order.id,UUID.randomUUID()));
        assertTrue(ledger.beginTransfer(order.id,courier,KingdomSupplyOrders.TransferKind.PICKUP,2));
        assertTrue(ledger.recordPickup(order.id,courier,2));
        assertFalse(ledger.cancelInternalUntouched(order.id,courier));
        var saved=ledger.save(new CompoundTag());
        var loader=KingdomSupplyOrders.class.getDeclaredMethod("load",CompoundTag.class);
        loader.setAccessible(true);
        var recovered=(KingdomSupplyOrders)loader.invoke(null,saved);
        assertTrue(recovered.needsReconciliation(order.id));
        assertTrue(recovered.hasActiveInternalTo(enterprise,"minecraft:overworld",to,"minecraft:apple"));
        assertFalse(recovered.cancelInternalUntouched(order.id,courier));
        assertTrue(recovered.reconcile(order.id,KingdomSupplyOrders.TransferKind.PICKUP,2));
        assertFalse(recovered.needsReconciliation(order.id));
        assertEquals(2,recovered.find(order.id).pickedUp);
    }

    @Test void changingLotBeforePickupCanBeCancelledSafely() {
        var ledger=new KingdomSupplyOrders();
        var id=UUID.randomUUID();
        var order=ledger.createInternal(id,"minecraft:overworld","minecraft:apple",
                new BlockPos(0,64,0),new BlockPos(3,64,0),4,foodLot(5));
        assertNotNull(order);
        UUID courier=UUID.randomUUID();
        assertTrue(ledger.assign(order.id,courier));
        assertTrue(ledger.cancelInternalUntouched(order.id,courier));
        assertEquals(KingdomSupplyOrders.State.CANCELLED,order.state);
        assertEquals(0,ledger.reserved("minecraft:overworld",order.source,"minecraft:apple"));
    }
}
