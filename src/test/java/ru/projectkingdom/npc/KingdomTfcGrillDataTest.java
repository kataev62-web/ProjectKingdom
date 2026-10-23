package ru.projectkingdom.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class KingdomTfcGrillDataTest {
    @Test void realBatchCustodySurvivesWorldSave() {
        UUID worker = UUID.fromString("00000000-0000-0000-0000-000000000012");
        KingdomTfcGrillData data = new KingdomTfcGrillData();
        var batch = new KingdomTfcGrillData.Job(worker, "minecraft:overworld", 123L, 4,
            "tfc:food/raw_beef", "tfc:food/cooked_beef");
        data.put(batch);
        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000055");
        assertTrue(data.reservedByOther(other, "minecraft:overworld", 123L, 4));
        assertFalse(data.reservedByOther(worker, "minecraft:overworld", 123L, 4));
        assertFalse(data.reservedByOther(other, "minecraft:overworld", 123L, 5));
        CompoundTag saved = data.save(new CompoundTag());
        KingdomTfcGrillData loaded = KingdomTfcGrillData.load(saved);
        assertEquals(batch, loaded.get(worker));
        loaded.remove(worker);
        assertNull(loaded.get(worker));
    }

    @Test void unknownAndInvalidJobsAreRejectedOnLoad() {
        UUID worker = UUID.fromString("00000000-0000-0000-0000-000000000034");
        CompoundTag bad = new CompoundTag();
        bad.putUUID("Worker", worker);
        bad.putString("Dimension", "minecraft:overworld");
        bad.putString("Input", "tfc:food/raw_beef");
        bad.putString("Output", "tfc:food/cooked_beef");
        bad.putInt("Slot", 999);
        var entries = new net.minecraft.nbt.ListTag();
        entries.add(bad);
        var root = new CompoundTag();
        root.put("Jobs", entries);
        assertNull(KingdomTfcGrillData.load(root).get(worker));
    }
}
