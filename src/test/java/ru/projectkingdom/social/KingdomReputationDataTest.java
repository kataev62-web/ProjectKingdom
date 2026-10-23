package ru.projectkingdom.social;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class KingdomReputationDataTest {
    private static final KingdomReputationPolicy.Event TAX=KingdomReputationPolicy.Event.TAX_PAYMENT;
    @Test void citySpecificPointsCannotBeReplayedOrSplitToFarm() {
        var data=new KingdomReputationData();
        UUID person=UUID.randomUUID(),avalon=UUID.randomUUID(),other=UUID.randomUUID();
        UUID receipt=UUID.randomUUID();
        assertTrue(data.recordVerified(avalon,person,TAX,receipt,100,1));
        assertEquals(4,data.score(avalon,person));
        assertFalse(data.recordVerified(avalon,person,TAX,receipt,100,1));
        assertEquals(0,data.score(other,person));
        assertTrue(data.recordVerified(avalon,person,TAX,UUID.randomUUID(),100,1));
        assertFalse(data.recordVerified(avalon,person,TAX,UUID.randomUUID(),100,1));
        assertEquals(8,data.score(avalon,person));
        assertFalse(data.recordVerified(avalon,person,TAX,UUID.randomUUID(),100,0));
        assertEquals(8,data.score(avalon,person));
        assertTrue(data.recordVerified(avalon,person,TAX,UUID.randomUUID(),100,2));
        assertEquals(12,data.score(avalon,person));
    }
    @Test void persistsScoreReceiptsAndDailyLimit() {
        var data=new KingdomReputationData();
        UUID person=UUID.randomUUID(),town=UUID.randomUUID(),receipt=UUID.randomUUID();
        assertTrue(data.recordVerified(town,person,KingdomReputationPolicy.Event.LAND_PURCHASE,receipt,10,3));
        CompoundTag saved=data.save(new CompoundTag());
        var loaded=read(saved);
        assertEquals(8,loaded.score(town,person));
        assertFalse(loaded.recordVerified(town,person,KingdomReputationPolicy.Event.LAND_PURCHASE,receipt,10,4));
        assertFalse(loaded.recordVerified(town,person,TAX,UUID.randomUUID(),100,3));
        assertTrue(loaded.recordVerified(town,person,TAX,UUID.randomUUID(),100,4));
    }
    private static KingdomReputationData read(CompoundTag root) {
        try {
            var method=KingdomReputationData.class.getDeclaredMethod("load",CompoundTag.class);
            method.setAccessible(true);
            return (KingdomReputationData)method.invoke(null,root);
        }catch(ReflectiveOperationException exception){throw new AssertionError(exception);}
    }
    @Test void scoringNeverUsesUnverifiedTradeIntents() {
        assertEquals(0,KingdomReputationPolicy.earned(TAX,0));
        assertEquals(2,KingdomReputationPolicy.earned(TAX,10));
        assertEquals(1,KingdomReputationPolicy.earned(KingdomReputationPolicy.Event.EARNED_WAGE,10));
        assertEquals("Уважаемый горожанин",KingdomReputationPolicy.standing(30));
    }
}
