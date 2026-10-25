package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/** Physical municipal procurement never bypasses the write-ahead payment journal. */
final class KingdomMunicipalProcurementTest {
    @Test void journalRequiresFinishedPaymentAndSurvivesRestart(){
        var state=new KingdomMunicipalProcurementData();
        var orders=new KingdomSupplyOrders();
        UUID town=UUID.randomUUID(),actor=UUID.randomUUID(),seller=UUID.randomUUID();
        var r=state.prepare(town,actor,"minecraft:apple",5,500,orders);
        assertNotNull(r);
        assertNull(state.prepare(town,actor,"minecraft:apple",5,500,orders));
        var order=orders.createMunicipal(town,seller,"minecraft:overworld",
                "minecraft:apple",new BlockPos(1,65,1),new BlockPos(5,65,1),
                5,500,new ItemStack(Items.APPLE,5));
        assertNotNull(order);
        assertFalse(state.authorizedDelivery(order));
        assertTrue(state.advance(r.id(),KingdomMunicipalProcurementData.Phase.PREPARED,
                KingdomMunicipalProcurementData.Phase.CASH_REMOVED,null));
        assertTrue(state.advance(r.id(),KingdomMunicipalProcurementData.Phase.CASH_REMOVED,
                KingdomMunicipalProcurementData.Phase.TREASURY_DEBITED,null));
        assertFalse(state.authorizedDelivery(order));
        assertTrue(orders.escrow(order.id,500,0));
        assertTrue(order.deliveryPaid);
        assertEquals(500,order.goodsEscrow);
        assertTrue(state.advance(r.id(),KingdomMunicipalProcurementData.Phase.TREASURY_DEBITED,
                KingdomMunicipalProcurementData.Phase.ORDERED,order.id));
        assertTrue(state.authorizedDelivery(order));
        var restored=KingdomMunicipalProcurementData.load(state.save(new CompoundTag()));
        assertTrue(restored.authorizedDelivery(order));
        assertEquals(KingdomMunicipalProcurementData.Phase.ORDERED,restored.get(r.id()).phase());
        assertEquals(500,restored.get(r.id()).copper());
    }

    @Test void incompletePaymentNeverAutoRepeatsOrCancels(){
        var state=new KingdomMunicipalProcurementData();
        var orders=new KingdomSupplyOrders();
        UUID town=UUID.randomUUID();
        var r=state.prepare(town,UUID.randomUUID(),"minecraft:bread",2,200,orders);
        assertNotNull(r);
        assertTrue(state.advance(r.id(),KingdomMunicipalProcurementData.Phase.PREPARED,
                KingdomMunicipalProcurementData.Phase.CASH_REMOVED,null));
        assertFalse(state.cancelUnused(r.id()));
        assertNull(state.prepare(town,UUID.randomUUID(),"minecraft:bread",2,200,orders));
        var recovered=KingdomMunicipalProcurementData.load(state.save(new CompoundTag()));
        assertTrue(recovered.active(town,"minecraft:bread",orders));
        assertNull(recovered.prepare(town,UUID.randomUUID(),"minecraft:bread",2,200,orders));
    }

    @Test void noFundsUsedIfPrepaymentProposalAbandoned(){
        var data=new KingdomMunicipalProcurementData();
        var orders=new KingdomSupplyOrders();
        UUID town=UUID.randomUUID();
        var receipt=data.prepare(town,UUID.randomUUID(),"minecraft:apple",1,42,orders);
        assertNotNull(receipt);
        assertTrue(data.cancelUnused(receipt.id()));
        assertFalse(data.active(town,"minecraft:apple",orders));
    }

    @Test void cityStorageChecksRealCapacityOfExactTfcLot(){
        var c=new SimpleContainer(2);
        var lot=new ItemStack(Items.APPLE,5);
        CompoundTag fresh=new CompoundTag();fresh.putLong("tfc:created",100);
        lot.setTag(fresh);
        var other=lot.copy();other.getTag().putLong("tfc:created",300);
        c.setItem(0,other);
        c.setItem(1,new ItemStack(Items.DIRT,64));
        assertFalse(KingdomMunicipalProcurement.fits(c,lot,1));
        c.setItem(1,ItemStack.EMPTY);
        assertTrue(KingdomMunicipalProcurement.fits(c,lot,5));
        assertFalse(KingdomMunicipalProcurement.fits(c,lot,65));
    }

    @Test void generalMarketOrderCannotForgeMunicipalFreeDelivery(){
        var ledger=new KingdomSupplyOrders();
        assertNull(ledger.create(UUID.randomUUID(),UUID.randomUUID(),
                "minecraft:overworld","minecraft:apple",new BlockPos(0,70,0),
                new BlockPos(2,70,0),4,0,0,KingdomSupplyOrders.Mode.MUNICIPAL));
        assertNull(ledger.createMunicipal(UUID.randomUUID(),UUID.randomUUID(),
                "minecraft:overworld","minecraft:apple",new BlockPos(0,70,0),
                new BlockPos(2,70,0),17,2000,new ItemStack(Items.APPLE,17)));
    }
}
