package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class KingdomExpansionCivicsCommitTest {
    @SuppressWarnings("unchecked")
    private static void fundTown(KingdomCivicsData civics, KingdomCivicsData.Town town, long amount)
            throws Exception {
        Field field = KingdomCivicsData.class.getDeclaredField("towns");
        field.setAccessible(true);
        var cities = (Map<UUID, KingdomCivicsData.Town>) field.get(civics);
        cities.put(town.id(), new KingdomCivicsData.Town(town.id(), town.name(),
                town.mayor(), town.nation(), amount));
    }

    private static KingdomCivicsData reload(CompoundTag nbt) throws Exception {
        Method method = KingdomCivicsData.class.getDeclaredMethod("load", CompoundTag.class);
        method.setAccessible(true);
        return (KingdomCivicsData) method.invoke(null, nbt);
    }

    @Test void cityBalanceAndAllClaimsRoundTripWithOneReceipt() throws Exception {
        var civics = new KingdomCivicsData();
        UUID founder = UUID.randomUUID();
        var city = civics.foundTown(founder, "Test city");
        assertNotNull(city);
        assertTrue(civics.claim(founder, "minecraft:overworld", 0, 0));
        fundTown(civics, city, 1000);
        UUID transaction = UUID.randomUUID();
        var cells = List.of(
            new KingdomExpansionPlans.Cell("minecraft:overworld", 1, 0),
            new KingdomExpansionPlans.Cell("minecraft:overworld", 2, 0));
        assertTrue(civics.commitPaidExpansion(transaction, city.id(), "minecraft:overworld", cells, 200));
        assertEquals(800, civics.townById(city.id()).treasury());
        assertFalse(civics.commitPaidExpansion(transaction, city.id(), "minecraft:overworld", cells, 200));

        var restored = reload(civics.save(new CompoundTag()));
        assertEquals(800, restored.townById(city.id()).treasury());
        assertNotNull(restored.expansionReceipt(transaction));
        assertEquals(200, restored.expansionReceipt(transaction).copper());
        assertEquals(3, restored.mapPlots("minecraft:overworld").size());
    }

    @Test void failedBatchNeverDebitsBalanceOrClaimsPartialArea() throws Exception {
        var civics = new KingdomCivicsData();
        UUID founder = UUID.randomUUID();
        var city = civics.foundTown(founder, "Test city");
        civics.claim(founder, "minecraft:overworld", 0, 0);
        fundTown(civics, city, 300);
        UUID transaction = UUID.randomUUID();
        var disconnected = List.of(
            new KingdomExpansionPlans.Cell("minecraft:overworld", 1, 0),
            new KingdomExpansionPlans.Cell("minecraft:overworld", 20, 20));
        assertFalse(civics.commitPaidExpansion(transaction, city.id(), "minecraft:overworld", disconnected, 200));
        assertEquals(300, civics.townById(city.id()).treasury());
        assertEquals(1, civics.mapPlots("minecraft:overworld").size());
        assertNull(civics.expansionReceipt(transaction));
    }
}
