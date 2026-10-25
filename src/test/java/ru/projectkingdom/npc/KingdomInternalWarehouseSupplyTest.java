package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

final class KingdomInternalWarehouseSupplyTest {
    @Test void internalOrderCarriesZeroCashAndSurvivesSingleReservation() {
        var ledger = new KingdomSupplyOrders();
        UUID firm=UUID.randomUUID();
        var source=new BlockPos(10,64,10);
        var target=new BlockPos(13,64,10);
        var first=ledger.createInternal(firm,"minecraft:overworld","minecraft:iron_ingot",source,target,4);
        assertNotNull(first);
        assertEquals(KingdomSupplyOrders.Mode.INTERNAL, first.mode);
        assertEquals(firm,first.buyer);
        assertEquals(firm,first.seller);
        assertEquals(0,first.goodsPrice);
        assertEquals(0,first.deliveryFee);
        assertEquals(0,first.goodsEscrow);
        assertEquals(0,first.deliveryEscrow);
        assertTrue(first.deliveryPaid);
        assertFalse(first.goodsPaid);
        assertEquals(4,ledger.reserved("minecraft:overworld",source,"minecraft:iron_ingot"));
        assertNull(ledger.createInternal(firm,"minecraft:overworld","minecraft:iron_ingot",source,target,4));
        assertTrue(ledger.hasActiveInternal(firm,"minecraft:overworld",source,target,"minecraft:iron_ingot"));
        assertTrue(ledger.cancelInternalUnassigned(first.id));
        assertFalse(ledger.hasActiveInternal(firm,"minecraft:overworld",source,target,"minecraft:iron_ingot"));
        assertEquals(0,ledger.reserved("minecraft:overworld",source,"minecraft:iron_ingot"));
    }

    @Test void interCompanyFreeTransferIsRejectedByGeneralOrderFactory() {
        var ledger = new KingdomSupplyOrders();
        UUID firm=UUID.randomUUID();
        assertNull(ledger.create(firm,firm,"minecraft:overworld","minecraft:stone",
                new BlockPos(0,64,0),new BlockPos(1,64,0),1,0,0,
                KingdomSupplyOrders.Mode.INTERNAL));
        assertNull(ledger.createInternal(firm,"minecraft:overworld","minecraft:stone",
                new BlockPos(0,64,0),new BlockPos(0,64,0),1));
        assertNull(ledger.createInternal(firm,"minecraft:overworld","minecraft:stone",
                new BlockPos(0,64,0),new BlockPos(1,64,0),17));
    }

    @Test void cannotCancelAfterCourierAssignment() {
        var ledger=new KingdomSupplyOrders();
        var order=ledger.createInternal(UUID.randomUUID(),"minecraft:overworld","minecraft:stone",
                new BlockPos(0,64,0),new BlockPos(3,64,0),3);
        assertNotNull(order);
        assertTrue(ledger.assign(order.id,UUID.randomUUID()));
        assertFalse(ledger.cancelInternalUnassigned(order.id));
    }

    @Test void recipeSlotsAreExcludedFromCapacity() {
        var target=new SimpleContainer(12);
        var sample=new ItemStack(Items.IRON_INGOT,4);
        // Slots 0..8 hold the stored recipe and must never become a delivery destination.
        for(int i=9;i<12;i++)target.setItem(i,new ItemStack(Items.DIRT,64));
        assertEquals(0,KingdomEnterpriseInternalSupply.capacity(target,sample,9));
        target.setItem(10,ItemStack.EMPTY);
        assertEquals(64,KingdomEnterpriseInternalSupply.capacity(target,sample,9));
        assertEquals(0,KingdomEnterpriseInternalSupply.plainCount(target,"minecraft:iron_ingot",9));
    }
}
