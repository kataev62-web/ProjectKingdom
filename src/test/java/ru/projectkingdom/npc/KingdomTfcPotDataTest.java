package ru.projectkingdom.npc;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomTfcPotDataTest {
    @Test void batchOwnershipSurvivesRestart(){
        var state=KingdomTfcPotData.load(new CompoundTag());
        UUID worker=UUID.randomUUID(),other=UUID.randomUUID();
        var batch=new KingdomTfcPotData.Batch(worker,"minecraft:overworld",1234L,
            List.of("tfc:food/rice_grain","tfc:food/rice_grain"),
            List.of("tfc:food/cooked_rice","tfc:food/cooked_rice"));
        state.put(batch);
        assertFalse(state.reserved(worker,"minecraft:overworld",1234L));
        assertTrue(state.reserved(other,"minecraft:overworld",1234L));
        var loaded=KingdomTfcPotData.load(state.save(new CompoundTag()));
        assertEquals(batch,loaded.get(worker));
        loaded.remove(worker);
        assertNull(loaded.get(worker));
    }
    @Test void invalidBatchesCannotBeStoredOrRevived(){
        assertThrows(IllegalArgumentException.class,()->
            new KingdomTfcPotData.Batch(UUID.randomUUID(),"minecraft:overworld",0L,
                List.of(),List.of()));
        assertThrows(IllegalArgumentException.class,()->
            new KingdomTfcPotData.Batch(UUID.randomUUID(),"minecraft:overworld",0L,
                List.of("tfc:food/rice_grain"),List.of()));
    }
    @Test void licensedKitchenTradesOnly() {
        assertTrue(KingdomProfessionWorkRules.pot("cook",ru.projectkingdom.enterprise.EnterpriseKind.BAKERY));
        assertTrue(KingdomProfessionWorkRules.pot("baker",ru.projectkingdom.enterprise.EnterpriseKind.BAKERY));
        assertTrue(KingdomProfessionWorkRules.pot("butcher",ru.projectkingdom.enterprise.EnterpriseKind.BAKERY));
        assertFalse(KingdomProfessionWorkRules.pot("miner",ru.projectkingdom.enterprise.EnterpriseKind.BAKERY));
        assertFalse(KingdomProfessionWorkRules.pot("cook",ru.projectkingdom.enterprise.EnterpriseKind.SMITHY));
    }
    @Test void freshCountNeverChangesInventory(){
        var store=new SimpleContainer(2);
        store.setItem(0,new ItemStack(Items.APPLE,3));
        assertEquals(3,KingdomTfcPotWork.freshCount(store,"minecraft:apple"));
        assertEquals(0,KingdomTfcPotWork.freshCount(store,"minecraft:beef"));
        assertEquals(3,store.getItem(0).getCount());
    }
}
