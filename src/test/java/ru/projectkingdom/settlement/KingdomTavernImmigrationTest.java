package ru.projectkingdom.settlement;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class KingdomTavernImmigrationTest {
    @Test void persistedTownCooldownDoesNotMultiplyAfterRestart() {
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        KingdomTavernArrivals data=new KingdomTavernArrivals();
        assertTrue(data.ready(first,100));
        assertTrue(data.ready(second,100));
        data.record(first,100);
        assertFalse(data.ready(first,101));
        assertFalse(data.ready(first,24100-1));
        assertTrue(data.ready(first,24100));
        assertTrue(data.ready(second,101));
        var reloaded=KingdomTavernArrivals.load(data.save(new CompoundTag()));
        assertEquals(100,reloaded.last(first));
        assertFalse(reloaded.ready(first,120));
        assertTrue(reloaded.ready(first,24100));
        assertEquals(-1,reloaded.last(second));
        assertFalse(reloaded.ready(null,100));
    }
    @Test void arrivalsRequireActualTavernBedsNpcAndMunicipalMembership() throws Exception {
        String s=Files.readString(Path.of("src/main/java/ru/projectkingdom/settlement/KingdomTavernImmigration.java"));
        assertTrue(s.contains("EnterpriseKind.TAVERN"));
        assertTrue(s.contains("BedBlock.PART"));
        assertTrue(s.contains("level.hasChunkAt("));
        assertTrue(s.contains("EntityType.VILLAGER.create(level)"));
        assertTrue(s.contains("level.addFreshEntity(villager)"));
        assertTrue(s.contains("npcDb.add(villager.getUUID()"));
        assertTrue(s.contains("created.settlement=town.id().toString()"));
        assertTrue(s.contains("civics.joinTown(villager.getUUID(),town.id())"));
        assertTrue(s.contains("arrivals.record(town.id(),tick)"));
        assertTrue(s.contains("witness==null"));
        assertFalse(s.contains("deposit("));
        assertFalse(s.contains("KingdomCurrency"));
    }
    @Test void immigrationIsIntegratedIntoServerAndShownAtActualTavernDesk() throws Exception {
        String server=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomNpcMod.java"));
        String desk=Files.readString(Path.of("src/main/java/ru/projectkingdom/enterprise/KingdomHospitalityMenu.java"));
        assertTrue(server.contains("KingdomTavernImmigration.tick(server)"));
        assertTrue(desk.contains("KingdomTavernImmigration.inspect("));
        assertTrue(desk.contains("arrivals.residents()"));
        assertTrue(desk.contains("arrivals.beds()"));
    }
}
