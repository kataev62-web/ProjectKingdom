package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomSmithyOrdersDataTest {
    private final UUID firm=UUID.randomUUID(),worker=UUID.randomUUID();
    private static final String RECIPE="tfc:anvil/steel_pickaxe_head",INPUT="tfc:metal/ingot/steel";

    @Test void actualWarehouseDeliveryIsOnlyWayToAdvance() {
        var db=new KingdomSmithyOrdersData();
        assertTrue(db.create(firm,worker,RECIPE,INPUT,2));
        assertFalse(db.create(firm,worker,RECIPE,INPUT,2));
        assertFalse(db.ready(firm,UUID.randomUUID(),"tfc:metal/pickaxe_head/steel",1));
        assertTrue(db.working(firm,worker));
        assertEquals(KingdomSmithyOrdersData.Phase.WORKING,db.order(firm).phase());
        assertEquals(0,db.order(firm).delivered());
        assertTrue(db.ready(firm,worker,"tfc:metal/pickaxe_head/steel",1));
        assertEquals(0,db.order(firm).delivered(),"Native smithing alone is not delivery");
        assertFalse(db.working(firm,worker),"Cannot start next item while real output waits");
        assertFalse(db.delivered(firm,worker,"minecraft:iron_pickaxe",1));
        assertFalse(db.delivered(firm,UUID.randomUUID(),"tfc:metal/pickaxe_head/steel",1));
        assertFalse(db.delivered(firm,worker,"tfc:metal/pickaxe_head/steel",2));
        assertTrue(db.delivered(firm,worker,"tfc:metal/pickaxe_head/steel",1));
        assertEquals(1,db.order(firm).delivered());
        assertEquals(KingdomSmithyOrdersData.Phase.ACTIVE,db.order(firm).phase());
        assertTrue(db.ready(firm,worker,"tfc:metal/pickaxe_head/steel",1));
        assertTrue(db.delivered(firm,worker,"tfc:metal/pickaxe_head/steel",1));
        assertTrue(db.order(firm).finished());
        assertEquals(2,db.order(firm).delivered());
        assertFalse(db.ready(firm,worker,"tfc:metal/pickaxe_head/steel",1));
        assertTrue(db.create(firm,worker,RECIPE,INPUT,1));
        assertEquals(0,db.order(firm).delivered());
    }

    @Test void countBoundariesAndSuspensionAreFailClosed() {
        var db=new KingdomSmithyOrdersData();
        assertFalse(db.create(firm,worker,RECIPE,INPUT,0));
        assertFalse(db.create(firm,worker,RECIPE,INPUT,65));
        assertFalse(db.create(firm,worker,"",INPUT,1));
        assertTrue(db.create(firm,worker,RECIPE,INPUT,1));
        assertTrue(db.togglePause(firm));
        assertEquals(KingdomSmithyOrdersData.Phase.PAUSED,db.order(firm).phase());
        assertFalse(db.working(firm,worker));
        assertFalse(db.ready(firm,worker,"tfc:metal/pickaxe_head/steel",1));
        assertTrue(db.togglePause(firm));
        assertTrue(db.ready(firm,worker,"tfc:metal/pickaxe_head/steel",1));
        assertFalse(db.togglePause(firm),"Cannot pause an already-forged native output");
        assertFalse(db.ready(firm,worker,"tfc:metal/pickaxe_head/steel",1));
    }

    @Test void restartRestoresInFlightAndCompletedCountsWithoutInventingOutput(){
        var db=new KingdomSmithyOrdersData();
        assertTrue(db.create(firm,worker,RECIPE,INPUT,3));
        assertTrue(db.ready(firm,worker,"tfc:metal/pickaxe_head/steel",1));
        var restored=KingdomSmithyOrdersData.load(db.save(new CompoundTag()));
        assertEquals(KingdomSmithyOrdersData.Phase.READY,restored.order(firm).phase());
        assertEquals("tfc:metal/pickaxe_head/steel",restored.order(firm).pendingItem());
        assertEquals(0,restored.order(firm).delivered());
        assertFalse(restored.create(firm,worker,RECIPE,INPUT,2));
        assertTrue(restored.delivered(firm,worker,"tfc:metal/pickaxe_head/steel",1));
        assertTrue(restored.working(firm,worker));
        restored.note(firm,worker,"Нагрев настоящего металла TFC");
        var after=KingdomSmithyOrdersData.load(restored.save(new CompoundTag()));
        assertEquals(1,after.order(firm).delivered());
        assertEquals(2,after.order(firm).remaining());
        assertEquals("Нагрев настоящего металла TFC",after.order(firm).message());
        assertEquals(KingdomSmithyOrdersData.Phase.WORKING,after.order(firm).phase());
    }
}
