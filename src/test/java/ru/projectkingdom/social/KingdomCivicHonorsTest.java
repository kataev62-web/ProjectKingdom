package ru.projectkingdom.social;

import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomCivicHonorsTest {
    @Test void awardsCannotBeClaimedWithoutVerifiedRankReputationOrIssuer(){
        var data=new KingdomCivicHonorsData();
        UUID town=UUID.randomUUID(),recipient=UUID.randomUUID(),mayor=UUID.randomUUID();
        assertFalse(data.awardVerified(town,recipient,KingdomCivicHonor.SERVICE,recipient,5,200,10));
        assertFalse(data.awardVerified(town,recipient,KingdomCivicHonor.SERVICE,mayor,2,200,10));
        assertFalse(data.awardVerified(town,recipient,KingdomCivicHonor.SERVICE,mayor,3,29,10));
        assertTrue(data.awardVerified(town,recipient,KingdomCivicHonor.SERVICE,mayor,3,30,10));
        assertFalse(data.awardVerified(town,recipient,KingdomCivicHonor.SERVICE,mayor,5,200,10));
        assertNull(data.best(UUID.randomUUID(),recipient));
        assertEquals(KingdomCivicHonor.SERVICE,data.best(town,recipient));
        assertTrue(data.awardVerified(town,recipient,KingdomCivicHonor.MERIT,mayor,4,60,12));
        assertEquals(KingdomCivicHonor.MERIT,data.best(town,recipient));
        assertEquals(2,data.awards(town,recipient).size());
    }
    @Test void saveAndLoadPreserveMunicipalDistinctionsAndProvenance() throws Exception {
        var data=new KingdomCivicHonorsData();
        UUID town=UUID.randomUUID(),person=UUID.randomUUID(),by=UUID.randomUUID();
        assertTrue(data.awardVerified(town,person,KingdomCivicHonor.DISTINCTION,by,5,100,99));
        Method load=KingdomCivicHonorsData.class.getDeclaredMethod("load",CompoundTag.class);
        load.setAccessible(true);
        var restored=(KingdomCivicHonorsData)load.invoke(null,data.save(new CompoundTag()));
        assertEquals(KingdomCivicHonor.DISTINCTION,restored.best(town,person));
        assertEquals(by,restored.awards(town,person).get(KingdomCivicHonor.DISTINCTION).by());
        assertEquals(99,restored.awards(town,person).get(KingdomCivicHonor.DISTINCTION).day());
    }
}
