package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomForestDataTest {
    private static CompoundTag plot(UUID id,UUID firm,int maxX,int maxZ){
        CompoundTag row=new CompoundTag();
        row.putUUID("Id",id);row.putUUID("Business",firm);
        row.putString("Dimension","minecraft:overworld");
        row.putInt("MinX",5);row.putInt("MaxX",maxX);row.putInt("Y",63);
        row.putInt("MinZ",7);row.putInt("MaxZ",maxZ);
        row.putString("Sapling","tfc:wood/sapling/oak");
        row.putBoolean("Enabled",true);
        row.putInt("Minimum",32);row.putInt("Maximum",128);
        row.putBoolean("Replant",true);
        return row;
    }
    @Test void plotBoundsRemainRestrictedToOwnedRectangle(){
        var a=new KingdomForestData.Area(UUID.randomUUID(),UUID.randomUUID(),
            "minecraft:overworld",-4,11,65,5,20,
            "tfc:wood/sapling/oak",true,32,128,true);
        assertTrue(a.inside(new BlockPos(-4,70,5)));
        assertTrue(a.inside(new BlockPos(11,64,20)));
        assertFalse(a.inside(new BlockPos(12,64,20)));
        assertThrows(IllegalArgumentException.class,()->
            new KingdomForestData.Area(UUID.randomUUID(),UUID.randomUUID(),
                "minecraft:overworld",0,16,63,0,15,"",true,1,10,true));
    }
    @Test void worldReloadRestoresAreasAndMinimumMaximumPolicy(){
        UUID id=UUID.randomUUID(),firm=UUID.randomUUID();
        CompoundTag root=new CompoundTag();
        ListTag plots=new ListTag();plots.add(plot(id,firm,12,10));root.put("Areas",plots);
        var data=KingdomForestData.load(root);
        assertEquals(1,data.of(firm).size());
        var area=data.of(firm).get(0);
        assertEquals("tfc:wood/sapling/oak",area.sapling());
        assertTrue(area.replant());
        assertEquals(128,area.maxLogs());
        assertFalse(data.harvestAllowed(id,new EnterpriseStockPolicy.Thresholds(32,128),130));
        assertTrue(data.harvestAllowed(id,new EnterpriseStockPolicy.Thresholds(32,128),20));
        assertTrue(data.harvestAllowed(id,new EnterpriseStockPolicy.Thresholds(32,128),70));
        var after=KingdomForestData.load(data.save(new CompoundTag()));
        assertEquals(area,after.of(firm).get(0));
        assertTrue(after.harvestAllowed(id,new EnterpriseStockPolicy.Thresholds(32,128),70));
        assertFalse(after.harvestAllowed(id,new EnterpriseStockPolicy.Thresholds(32,128),128));
    }
    @Test void manipulatedOversizedPlotsDoNotLoad(){
        UUID firm=UUID.randomUUID();
        CompoundTag root=new CompoundTag();ListTag plots=new ListTag();
        plots.add(plot(UUID.randomUUID(),firm,25,10));
        plots.add(plot(UUID.randomUUID(),firm,12,24));
        root.put("Areas",plots);
        assertTrue(KingdomForestData.load(root).of(firm).isEmpty());
    }
}
