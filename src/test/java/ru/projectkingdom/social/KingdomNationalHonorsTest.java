package ru.projectkingdom.social;

import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomNationalHonorsTest {
    @Test void sovereignMeritCannotBeGrantedTwiceOrToTheIssuer(){
        var state=new KingdomNationalHonorsData();
        UUID nation=UUID.randomUUID(),recipient=UUID.randomUUID(),ruler=UUID.randomUUID();
        assertFalse(state.grantVerified(nation,recipient,ruler,4,150,1));
        assertFalse(state.grantVerified(nation,recipient,ruler,5,99,1));
        assertFalse(state.grantVerified(nation,ruler,ruler,9,200,1));
        assertTrue(state.grantVerified(nation,recipient,ruler,5,100,1));
        assertFalse(state.grantVerified(nation,recipient,ruler,9,200,2));
        assertNull(state.award(UUID.randomUUID(),recipient));
        assertEquals(ruler,state.award(nation,recipient).issuer());
    }
    @Test void orderPersistsAndKingCannotAssignFromClient() throws Exception{
        var d=new KingdomNationalHonorsData();
        UUID nation=UUID.randomUUID(),subject=UUID.randomUUID(),king=UUID.randomUUID();
        assertTrue(d.grantVerified(nation,subject,king,5,150,4));
        var load=KingdomNationalHonorsData.class.getDeclaredMethod("load",CompoundTag.class);
        load.setAccessible(true);
        var restored=(KingdomNationalHonorsData)load.invoke(null,d.save(new CompoundTag()));
        assertEquals(king,restored.award(nation,subject).issuer());
        assertEquals(4,restored.award(nation,subject).day());
    }
}
