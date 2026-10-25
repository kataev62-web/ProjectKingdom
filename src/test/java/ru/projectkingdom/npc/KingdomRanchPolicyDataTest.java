package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomRanchPolicyDataTest {
    @Test void cullingDisabledByDefaultAndProtectsHerd(){
        var policy=new KingdomRanchPolicyData.Policy(false,6,-1);
        assertFalse(KingdomRanchPolicyData.mayCull(policy,12,10,true,false));
        var enabled=new KingdomRanchPolicyData.Policy(true,6,11);
        assertFalse(KingdomRanchPolicyData.mayCull(enabled,12,6,true,false));
        assertFalse(KingdomRanchPolicyData.mayCull(enabled,12,10,false,false));
        assertFalse(KingdomRanchPolicyData.mayCull(enabled,12,10,true,true));
        assertFalse(KingdomRanchPolicyData.mayCull(enabled,11,10,true,false));
        assertTrue(KingdomRanchPolicyData.mayCull(enabled,12,10,true,false));
    }
    @Test void cullingLimitAndConsentPersistAcrossWorldReload(){
        var data=KingdomRanchPolicyData.load(new CompoundTag());
        UUID pen=UUID.randomUUID();
        assertFalse(data.policy(pen).cullOld());
        data.markCull(pen,120);
        var copy=KingdomRanchPolicyData.load(data.save(new CompoundTag()));
        assertEquals(120,copy.policy(pen).lastCullDay());
        assertFalse(copy.policy(pen).cullOld());
        assertEquals(6,copy.policy(pen).minHerd());
    }
}
