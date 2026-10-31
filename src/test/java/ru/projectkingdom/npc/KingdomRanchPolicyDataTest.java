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
    @Test void oldPensDoNotAutoFeedAndAllExistingFlagsRemainUnchanged(){
        UUID id=UUID.randomUUID();
        var root=new CompoundTag();
        var row=new CompoundTag();
        row.putUUID("Pen",id);row.putBoolean("CullOld",true);
        row.putBoolean("CollectEggs",true);row.putInt("MinHerd",9);
        row.putLong("LastCull",78L);
        var rows=new net.minecraft.nbt.ListTag();rows.add(row);root.put("Policies",rows);
        var state=KingdomRanchPolicyData.load(root);
        var old=state.policy(id);
        assertFalse(old.autoFeed(),"Existing worlds must leave player-controlled feeding enabled");
        assertTrue(old.cullOld());
        assertTrue(old.collectEggs());
        assertEquals(9,old.minHerd());
        state.markCull(id,79L);
        var after=KingdomRanchPolicyData.load(state.save(new CompoundTag())).policy(id);
        assertFalse(after.autoFeed());
        assertTrue(after.cullOld());
        assertTrue(after.collectEggs());
        assertEquals(79L,after.lastCullDay());
    }
    @Test void savedAutoFeedSettingSurvivesAndDoesNotChangeOtherModes(){
        UUID id=UUID.randomUUID();
        var root=new CompoundTag();
        var row=new CompoundTag();row.putUUID("Pen",id);
        row.putBoolean("AutoFeed",true);row.putBoolean("CollectEggs",false);
        row.putInt("MinHerd",6);row.putLong("LastCull",-1);
        var rows=new net.minecraft.nbt.ListTag();rows.add(row);root.put("Policies",rows);
        var state=KingdomRanchPolicyData.load(root);
        assertTrue(state.policy(id).autoFeed());
        assertFalse(state.policy(id).collectEggs());
        assertFalse(state.policy(id).cullOld());
        var after=KingdomRanchPolicyData.load(state.save(new CompoundTag()));
        assertTrue(after.policy(id).autoFeed());
        assertFalse(after.policy(id).cullOld());
    }
    @Test void eggCollectionIsOffByDefaultAndPersistedSeparatelyFromCullConsent(){
        var old=new KingdomRanchPolicyData.Policy(true,6,22);
        assertFalse(old.collectEggs());
        UUID id=UUID.randomUUID();
        var root=new CompoundTag();
        var row=new CompoundTag();
        row.putUUID("Pen",id);row.putBoolean("CullOld",true);
        row.putBoolean("CollectEggs",true);row.putInt("MinHerd",6);row.putLong("LastCull",22);
        var list=new net.minecraft.nbt.ListTag();list.add(row);root.put("Policies",list);
        var loaded=KingdomRanchPolicyData.load(root);
        assertTrue(loaded.policy(id).collectEggs());
        assertTrue(loaded.policy(id).cullOld());
        var after=KingdomRanchPolicyData.load(loaded.save(new CompoundTag()));
        assertTrue(after.policy(id).collectEggs());
        assertEquals(22,after.policy(id).lastCullDay());
        assertFalse(after.policy(UUID.randomUUID()).collectEggs());
    }
}
