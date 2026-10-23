package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomFarmFieldDataTest {
    @Test void rectangleIsRestrictedToCropLayerAndContainsCorners() {
        var plot=new KingdomFarmFieldData.Field(UUID.randomUUID(),UUID.randomUUID(),
            "minecraft:overworld",-4,11,63,5,20,"tfc:seeds/barley",true);
        assertEquals(256,plot.area());
        assertTrue(plot.contains(new BlockPos(-4,64,5)));
        assertTrue(plot.contains(new BlockPos(11,64,20)));
        assertFalse(plot.contains(new BlockPos(12,64,20)));
        assertFalse(plot.contains(new BlockPos(11,63,20)));
        assertEquals("tfc:crop/barley",KingdomFarmFieldData.cropId("tfc:seeds/barley"));
        assertEquals("",KingdomFarmFieldData.cropId("minecraft:wheat_seeds"));
    }
    @Test void restartPreservesFieldDimensionsAndPlannedSeed() {
        UUID farm=UUID.randomUUID(),id=UUID.randomUUID();
        CompoundTag tag=new CompoundTag(),row=new CompoundTag();
        row.putUUID("Id",id);row.putUUID("Farm",farm);
        row.putString("Dimension","minecraft:overworld");
        row.putInt("X1",5);row.putInt("X2",12);row.putInt("Y",78);
        row.putInt("Z1",7);row.putInt("Z2",10);
        row.putString("Seed","tfc:seeds/onion");row.putBoolean("Enabled",true);
        ListTag rows=new ListTag();rows.add(row);tag.put("Fields",rows);
        var data=KingdomFarmFieldData.load(tag);
        assertEquals(1,data.of(farm).size());
        var plot=data.of(farm).get(0);
        assertEquals(32,plot.area());
        assertEquals("tfc:seeds/onion",plot.seedId());
        var again=KingdomFarmFieldData.load(data.save(new CompoundTag()));
        assertEquals(plot,again.of(farm).get(0));
    }
    @Test void tamperedAndOversizedRegionsNeverLoad() {
        CompoundTag root=new CompoundTag();ListTag plots=new ListTag();
        for(int i=0;i<3;i++){
            CompoundTag bad=new CompoundTag();bad.putUUID("Id",UUID.randomUUID());
            bad.putUUID("Farm",UUID.randomUUID());
            bad.putString("Dimension","minecraft:overworld");
            bad.putInt("X1",0);bad.putInt("X2",i==0?16:(i==1?-1:0));
            bad.putInt("Y",63);bad.putInt("Z1",0);bad.putInt("Z2",i==2?17:0);
            plots.add(bad);
        }
        root.put("Fields",plots);
        assertTrue(KingdomFarmFieldData.load(root).save(new CompoundTag())
            .getList("Fields",10).isEmpty());
    }
}
