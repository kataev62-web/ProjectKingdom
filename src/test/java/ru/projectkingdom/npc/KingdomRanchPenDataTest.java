package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomRanchPenDataTest {
    @Test void penBoundsAndDimensionDoNotIncludeOutsideLand(){
        var pen=new KingdomRanchPenData.Pen(UUID.randomUUID(),UUID.randomUUID(),
            "minecraft:overworld",10,25,70,15,30,true);
        assertEquals(16,pen.maxX()-pen.minX()+1);
        assertEquals(new BlockPos(17,71,22),pen.center());
    }
    @Test void penContainsLivestockOnUnevenGroundButNotOutsideItsFootprint(){
        var pen=new KingdomRanchPenData.Pen(UUID.randomUUID(),UUID.randomUUID(),
            "minecraft:overworld",10,25,70,15,30,true);
        assertTrue(pen.contains(new BlockPos(12,86,18),"minecraft:overworld"));
        assertTrue(pen.contains(new BlockPos(12,62,18),"minecraft:overworld"));
        assertFalse(pen.contains(new BlockPos(12,87,18),"minecraft:overworld"));
        assertFalse(pen.contains(new BlockPos(26,70,18),"minecraft:overworld"));
        assertFalse(pen.contains(new BlockPos(12,70,18),"minecraft:the_nether"));
    }
    @Test void savesAfterRestartAndRejectsOversizedPens(){
        UUID id=UUID.randomUUID(),farm=UUID.randomUUID();
        CompoundTag root=new CompoundTag();
        var rows=new ListTag();
        for(int i=0;i<2;i++){
            var tag=new CompoundTag();
            tag.putUUID("Id",UUID.randomUUID());tag.putUUID("Farm",farm);
            tag.putString("Dimension","minecraft:overworld");
            tag.putInt("X1",8);tag.putInt("X2",i==0?16:24);
            tag.putInt("Y",63);tag.putInt("Z1",4);tag.putInt("Z2",12);
            tag.putBoolean("Enabled",true);rows.add(tag);
        }
        root.put("Pens",rows);
        var data=KingdomRanchPenData.load(root);
        assertEquals(1,data.of(farm).size());
        assertEquals(data.of(farm),KingdomRanchPenData.load(data.save(new CompoundTag())).of(farm));
    }
    @Test void breederHasRealPhysicalDuty(){
        assertEquals(KingdomNpcDuty.Kind.FIELD,KingdomNpcDuty.kind("breeder"));
        assertTrue(KingdomNpcDuty.hasPhysicalExecutor("breeder"));
        assertTrue(KingdomNpcDuty.mayExecute("breeder","work","CONTINUE_ASSIGNED_JOB",true));
        assertFalse(KingdomNpcDuty.mayExecute("breeder","stay","CONTINUE_ASSIGNED_JOB",true));
        assertFalse(KingdomNpcDuty.hasPhysicalExecutor("hunter"));
    }
}
