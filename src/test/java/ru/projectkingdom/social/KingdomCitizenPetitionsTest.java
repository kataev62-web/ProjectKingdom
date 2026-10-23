package ru.projectkingdom.social;

import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomCitizenPetitionsTest {
    private static final KingdomCitizenPetitionsData.Topic ROADS=KingdomCitizenPetitionsData.Topic.ROADS;
    @Test void permissionCooldownAndReplayCannotCreateOrDecideExtraPetitions(){
        var data=new KingdomCitizenPetitionsData();
        UUID town=UUID.randomUUID(),citizen=UUID.randomUUID(),mayor=UUID.randomUUID();
        assertNull(data.fileVerified(town,citizen,ROADS,2,100,1));
        assertNull(data.fileVerified(town,citizen,ROADS,3,29,1));
        var first=data.fileVerified(town,citizen,ROADS,3,30,1);
        assertNotNull(first);
        assertNull(data.fileVerified(town,citizen,ROADS,3,30,1));
        assertNull(data.fileVerified(town,citizen,ROADS,3,30,3));
        assertNotNull(data.fileVerified(town,citizen,ROADS,3,30,4));
        assertEquals(2,data.pending(town,3).size());
        assertNull(data.fileVerified(town,citizen,ROADS,3,30,7)); // two pending cap
        assertNull(data.reviewOldest(town,first.id(),citizen,true,8)); // cannot vote own
        assertNull(data.reviewOldest(town,UUID.randomUUID(),mayor,true,8)); // stale menu
        var approved=data.reviewOldest(town,first.id(),mayor,true,8);
        assertEquals(KingdomCitizenPetitionsData.Status.APPROVED,approved.status());
        assertNull(data.reviewOldest(town,first.id(),mayor,true,8)); // replay
        assertEquals(1,data.pending(town,5).size());
        assertEquals(KingdomCitizenPetitionsData.Status.APPROVED,
                data.forMember(town,citizen,2).get(0).status());
    }
    @Test void docketPersistsAndDoesNotCrossCities() throws Exception {
        var data=new KingdomCitizenPetitionsData();
        UUID city=UUID.randomUUID(),person=UUID.randomUUID();
        var first=data.fileVerified(city,person,ROADS,4,90,10);
        assertNotNull(first);
        Method loader=KingdomCitizenPetitionsData.class.getDeclaredMethod("load",CompoundTag.class);
        loader.setAccessible(true);
        var restored=(KingdomCitizenPetitionsData)loader.invoke(null,data.save(new CompoundTag()));
        assertEquals(first.id(),restored.pending(city,1).get(0).id());
        assertTrue(restored.pending(UUID.randomUUID(),2).isEmpty());
        assertNull(restored.fileVerified(city,person,ROADS,4,90,11));
        assertNotNull(restored.fileVerified(city,person,ROADS,4,90,13));
    }
}
