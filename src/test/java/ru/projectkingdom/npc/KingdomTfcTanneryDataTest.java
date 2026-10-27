package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import ru.projectkingdom.enterprise.EnterpriseKind;

class KingdomTfcTanneryDataTest {
    @Test void onlyActualNineHideProcessingRecipesAreAuthorized() {
        for(String id:new String[]{"small","medium","large"})
            for(String step:new String[]{"soaked_hide","prepared_hide","leather"})
                assertTrue(KingdomTfcTanneryWork.supportedRecipe("tfc:barrel/"+id+"_"+step));
        assertFalse(KingdomTfcTanneryWork.supportedRecipe("tfc:barrel/milk_vinegar"));
        assertFalse(KingdomTfcTanneryWork.supportedRecipe("tfc:barrel/tannin"));
        assertFalse(KingdomTfcTanneryWork.supportedRecipe("minecraft:crafting_table"));
        assertFalse(KingdomTfcTanneryWork.supportedRecipe("tfc:barrel/large_cheese"));
    }
    @Test void tanneryHasPhysicalWorkshopExecutorButNoFreeResources(){
        assertEquals(KingdomNpcDuty.Kind.WORKSHOP,KingdomNpcDuty.kind("tanner"));
        assertTrue(KingdomNpcDuty.hasPhysicalExecutor("tanner"));
        assertTrue(KingdomProfessionWorkRules.tanning("tanner",EnterpriseKind.TANNERY));
        assertFalse(KingdomProfessionWorkRules.tanning("tanner",EnterpriseKind.FARM));
        assertFalse(KingdomNpcDuty.mayExecute("tanner","stay","CONTINUE_ASSIGNED_JOB",true));
    }
    @Test void reservationPersistsThroughWorldReloadAndPreventsCrossWorkerPickup(){
        var data=new KingdomTfcTanneryData();
        UUID worker=UUID.randomUUID(),other=UUID.randomUUID();
        var batch=new KingdomTfcTanneryData.Batch(worker,"minecraft:overworld",512L,
            "tfc:small_raw_hide","tfc:small_soaked_hide","tfc:barrel/small_soaked_hide");
        assertTrue(data.claim(batch));
        assertTrue(data.occupied(other,"minecraft:overworld",512L));
        assertFalse(data.claim(new KingdomTfcTanneryData.Batch(other,"minecraft:overworld",
            512L,"tfc:medium_raw_hide","tfc:medium_soaked_hide",
            "tfc:barrel/medium_soaked_hide")));
        var restored=KingdomTfcTanneryData.load(data.save(new CompoundTag()));
        assertEquals(batch,restored.get(worker));
        assertTrue(restored.occupied(other,"minecraft:overworld",512L));
        restored.release(worker);
        assertFalse(restored.occupied(other,"minecraft:overworld",512L));
    }
    @Test void corruptedOrForeignRecipesAreRejectedOnLoad(){
        assertThrows(IllegalArgumentException.class,()->
            new KingdomTfcTanneryData.Batch(UUID.randomUUID(),"minecraft:overworld",1L,
                "tfc:small_raw_hide","minecraft:leather","tfc:barrel/vinegar"));
        assertThrows(IllegalArgumentException.class,()->
            new KingdomTfcTanneryData.Batch(UUID.randomUUID(),"",1L,
                "a","b","tfc:barrel/small_leather"));
    }
}
