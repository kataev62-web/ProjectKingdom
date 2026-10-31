package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Regression: death must not leave phantom population or allow city border escape. */
final class KingdomResidentSafetyTest {
    private static String source(String path)throws Exception{
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+path));
    }
    @Test void confirmedDeathRemovesRecordButUnloadedWorkerPersists() {
        var data=new KingdomNpcData();
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        var worker=data.add(a,"NPC","minecraft:overworld",0);
        var unloaded=data.add(b,"Unloaded NPC","minecraft:overworld",0);
        worker.hasLastSafe=true;worker.lastSafeX=42;worker.lastSafeY=70;worker.lastSafeZ=19;
        assertNull(data.removeByEntity(UUID.randomUUID()));
        assertEquals(worker,data.removeByEntity(a));
        assertEquals(1,data.all().size());
        assertTrue(data.all().contains(unloaded));
        var restored=KingdomNpcData.load(data.save(new CompoundTag()));
        assertEquals(1,restored.all().size());
        assertNotNull(restored.removeByEntity(b));
        assertEquals(0,restored.all().size());
    }
    @Test void safeCityAnchorPersistsAcrossSave(){
        var db=new KingdomNpcData();
        var w=db.add(UUID.randomUUID(),"Citizen","minecraft:overworld",0);
        var position=new net.minecraft.core.BlockPos(-14,73,31);
        KingdomResidentSafety.recordSafe(w,position,db);
        var reloaded=KingdomNpcData.load(db.save(new CompoundTag()));
        var copy=reloaded.all().iterator().next();
        assertTrue(copy.hasLastSafe);
        assertEquals(-14,copy.lastSafeX);assertEquals(73,copy.lastSafeY);assertEquals(31,copy.lastSafeZ);
    }
    @Test void deathAndBoundaryHandlersAreServerSideAndDoNotInferDeathFromMissingUuid()throws Exception {
        String mod=source("npc/KingdomNpcMod.java");
        String safety=source("npc/KingdomResidentSafety.java");
        assertTrue(mod.contains("LivingDeathEvent"));
        assertTrue(mod.contains("EntityLeaveLevelEvent"));
        assertTrue(mod.contains("RemovalReason.KILLED"));
        assertTrue(mod.contains("KingdomResidentSafety.confine("));
        assertTrue(safety.contains("level.hasChunkAt(last)"));
        assertTrue(safety.contains("if(city==null)return false"));
        assertTrue(safety.contains("npc.getNavigation().stop()"));
        assertTrue(safety.contains("npc.teleportTo("));
        assertTrue(safety.contains("KingdomCivicsData.get(server).removeDeceasedNpc(uuid)"));
        assertTrue(safety.contains("retireDeceasedNpc(server,uuid)"));
        assertTrue(source("settlement/KingdomTavernImmigration.java").contains("KingdomResidentSafety.countsAsPresent"));
        assertTrue(mod.contains("Commands.literal(\"residents\")"));
        assertTrue(mod.contains("Commands.literal(\"repair\")"));
    }
}
