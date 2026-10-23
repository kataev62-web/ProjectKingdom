package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomTfcQuernDataTest {
    @Test void onlyOwnerWorkerMayClaimExistingStation() {
        var data = KingdomTfcQuernData.load(new CompoundTag());
        UUID worker = UUID.randomUUID(), rival = UUID.randomUUID();
        var job = new KingdomTfcQuernData.Job(worker, "minecraft:overworld", 12345L,
            "tfc:food/barley", "tfc:food/barley_grain");
        data.assign(job);
        assertEquals(job, data.job(worker));
        assertFalse(data.reservedByOther(worker,"minecraft:overworld",12345L));
        assertTrue(data.reservedByOther(rival,"minecraft:overworld",12345L));
        assertFalse(data.reservedByOther(rival,"minecraft:the_nether",12345L));
        assertFalse(data.reservedByOther(rival,"minecraft:overworld",12346L));
        data.release(worker);
        assertNull(data.job(worker));
        assertFalse(data.reservedByOther(rival,"minecraft:overworld",12345L));
    }

    @Test void restartedServerPreservesBatchIdentity() {
        var state = KingdomTfcQuernData.load(new CompoundTag());
        var worker=UUID.randomUUID();
        var expected=new KingdomTfcQuernData.Job(worker,"minecraft:overworld",987L,
            "tfc:food/rye","tfc:food/rye_grain");
        state.assign(expected);
        var loaded=KingdomTfcQuernData.load(state.save(new CompoundTag()));
        assertEquals(expected,loaded.job(worker));
        assertTrue(loaded.reservedByOther(UUID.randomUUID(),"minecraft:overworld",987L));
    }
}
