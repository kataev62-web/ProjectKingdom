package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class KingdomNpcCareerPolicyTest {
    @Test void differentVillagersGetStableIndividualPreferences() {
        int entrepreneurs=0;
        for(int i=0;i<1000;i++){
            UUID uuid=UUID.nameUUIDFromBytes(("citizen-"+i).getBytes(StandardCharsets.UTF_8));
            var first=KingdomNpcCareerPolicy.initial(uuid);
            assertEquals(first,KingdomNpcCareerPolicy.initial(uuid));
            if(first==KingdomNpcCareerPolicy.Path.ENTREPRENEUR)entrepreneurs++;
        }
        assertTrue(entrepreneurs>=250&&entrepreneurs<=550,
                "Mixed economy must keep a meaningful share of both paths");
    }

    @Test void aRichEmployeeIsNotForcedIntoBusiness() {
        assertEquals(KingdomNpcCareerPolicy.Stage.EMPLOYED,
                KingdomNpcCareerPolicy.stage(KingdomNpcCareerPolicy.Path.EMPLOYEE,
                    30,false,true,false,100_000L));
        assertEquals(KingdomNpcCareerPolicy.Stage.SEEKING_WORK,
                KingdomNpcCareerPolicy.stage(KingdomNpcCareerPolicy.Path.EMPLOYEE,
                    30,false,false,false,100_000L));
    }

    @Test void entrepreneurialGoalNeverCancelsAValidPaidJob() {
        assertEquals(KingdomNpcCareerPolicy.Stage.SAVING,
                KingdomNpcCareerPolicy.stage(KingdomNpcCareerPolicy.Path.ENTREPRENEUR,
                    30,false,true,false,999L));
        assertEquals(KingdomNpcCareerPolicy.Stage.READY_FOR_OFFERS,
                KingdomNpcCareerPolicy.stage(KingdomNpcCareerPolicy.Path.ENTREPRENEUR,
                    30,false,true,false,1000L));
        assertEquals(KingdomNpcCareerPolicy.Stage.UNPAID,
                KingdomNpcCareerPolicy.stage(KingdomNpcCareerPolicy.Path.ENTREPRENEUR,
                    30,true,true,false,1000L));
    }

    @Test void careerTraitSurvivesWorldReloadAndMigratesOldSaves() {
        UUID id=UUID.nameUUIDFromBytes("old-kingdom-citizen".getBytes(StandardCharsets.UTF_8));
        KingdomNpcData.Worker citizen=new KingdomNpcData.Worker(1,id,"Житель","minecraft:overworld",0L);
        citizen.careerPath=KingdomNpcCareerPolicy.Path.ENTREPRENEUR;
        CompoundTag saved=citizen.save();
        assertEquals(KingdomNpcCareerPolicy.Path.ENTREPRENEUR,
                KingdomNpcData.Worker.load(saved).careerPath);
        saved.remove("CareerPath");
        assertEquals(KingdomNpcCareerPolicy.initial(id),
                KingdomNpcData.Worker.load(saved).careerPath);
        saved.putString("CareerPath","UNKNOWN");
        assertEquals(KingdomNpcCareerPolicy.initial(id),
                KingdomNpcData.Worker.load(saved).careerPath);
    }
}
