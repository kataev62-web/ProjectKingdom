package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A relocated building must not move the shared physical materials depot.
 * Legacy saved worlds did not contain a dedicated marker anchor.
 */
class KingdomArchitectSitePersistenceTest {
    @Test void distantArchitectOriginKeepsItsActualWorkshopMarker() {
        var db=new KingdomConstructionData();
        UUID worker=UUID.randomUUID();
        BlockPos workshop=new BlockPos(100,72,-50);
        var site=db.add(worker,"minecraft:overworld","HOUSE",147,75,-25,workshop);
        assertEquals(workshop,db.marker(site));
        assertEquals(147,site.x());
        assertEquals(75,site.y());
        assertEquals(-25,site.z());
        assertEquals(site.id(),db.atMarker("minecraft:overworld",100,72,-50).id());
        assertNull(db.atMarker("minecraft:overworld",145,75,-27));
        CompoundTag saved=db.save(new CompoundTag());
        var read=KingdomConstructionData.load(saved);
        assertEquals(workshop,read.marker(read.find(site.id())));
        assertEquals(site.id(),read.atMarker("minecraft:overworld",100,72,-50).id());
        assertEquals(0,read.progress(site.id()));
    }

    @Test void legacyWorldProjectFallsBackToOriginalOffset() {
        UUID owner=UUID.randomUUID();
        var db=new KingdomConstructionData();
        var site=db.add(owner,"minecraft:overworld","HOUSE",20,69,31);
        CompoundTag saved=db.save(new CompoundTag());
        // Simulate a world created before architect previews existed.
        var entry=saved.getList("Projects",net.minecraft.nbt.Tag.TAG_COMPOUND)
            .getCompound(0);
        entry.remove("MarkerX");entry.remove("MarkerY");entry.remove("MarkerZ");
        var restored=KingdomConstructionData.load(saved);
        assertEquals(new BlockPos(18,69,29),restored.marker(restored.find(site.id())));
        assertEquals(site.id(),restored.atMarker("minecraft:overworld",18,69,29).id());
    }

    @Test void cancelledProjectReleasesWorkshopMarker() {
        var db=new KingdomConstructionData();
        UUID owner=UUID.randomUUID();
        var marker=new BlockPos(-5,80,-5);
        var site=db.add(owner,"minecraft:overworld","SMITHY",0,80,0,marker);
        assertNotNull(db.atMarker("minecraft:overworld",-5,80,-5));
        assertTrue(db.setStatus(site.id(),owner,KingdomConstructionData.Status.CANCELLED));
        assertNull(db.atMarker("minecraft:overworld",-5,80,-5));
    }
}
