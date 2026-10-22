package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class AdministrativeFinanceIntegrationTest {
    @Test void separatePhysicalReservesRoundTrip() {
        UUID town=UUID.randomUUID();var data=new KingdomAdministrationData();
        assertTrue(data.depositBankReserve(town,75));assertTrue(data.depositTreasuryReserve(town,40));
        assertTrue(data.ensureHistoricalTreasury(town,500));
        var loaded=KingdomAdministrationData.fromTag(data.save(new CompoundTag()));
        assertEquals(75,loaded.bankReserve(town));assertEquals(40,loaded.treasuryReserve(town));
        assertEquals(500,loaded.historicalTreasuryLiability(town));
    }
    @Test void bankAndCivicsUseAdministrativePhysicalBacking() throws Exception {
        String bank=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomBankData.java"));
        String civics=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomCivicsData.java"));
        String administration=Files.readString(Path.of("src/main/java/ru/projectkingdom/administration/KingdomAdministrationData.java"));
        assertTrue(bank.contains("bankTown(player)"));
        assertTrue(bank.contains("withdrawBankReserve"));
        assertTrue(civics.contains("withdrawTreasuryReserve"));
        assertTrue(administration.contains("ensureHistoricalTreasury"));
    }
}
