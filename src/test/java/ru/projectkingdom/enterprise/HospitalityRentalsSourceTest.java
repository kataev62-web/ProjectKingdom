package ru.projectkingdom.enterprise;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Stage 6 must settle room rent at a real enterprise, NEVER at the bank. */
class HospitalityRentalsSourceTest {
    private String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+relative));
    }
    @Test void roomPaymentComesOnlyFromPhysicalHotelOrTavernCashbox() throws Exception {
        String economy=source("npc/KingdomEnterpriseData.java");
        String bank=source("npc/KingdomBankData.java");
        String menu=source("enterprise/KingdomHospitalityMenu.java");
        String main=source("npc/KingdomMainMenu.java");
        assertTrue(economy.contains("public boolean payHospitalityRoom("));
        assertTrue(economy.contains("KingdomPhysicalCash.deposit(payer,cash.container(),price)"));
        assertTrue(economy.contains("EnterpriseBindingRole.CASHBOX"));
        assertTrue(economy.contains("rentals.receptionForRoom(roomId)"));
        assertTrue(economy.contains("firms.put(enterprise,new Enterprise("));
        assertTrue(menu.contains(".payHospitalityRoom("));
        assertFalse(bank.contains("payRoomLeaseCash("));
        assertFalse(main.contains("payRoomLeaseCash("));
    }
    @Test void hotelCoreHasGenuineReceptionAndPublicAccessOnlyForTrustedCore() throws Exception {
        String block=source("enterprise/EnterpriseCoreBlock.java");
        String claim=source("npc/KingdomClaimProtection.java");
        String reception=source("enterprise/KingdomHospitalityMenu.java");
        assertTrue(block.contains("KingdomHospitalityMenu.open(server,core)"));
        assertTrue(claim.contains("publicReception(ServerPlayer player, BlockPos pos)"));
        assertTrue(claim.contains("binding.packedPos()!=pos.asLong()"));
        assertTrue(claim.contains("rentals.atRoom("));
        assertTrue(reception.contains("KingdomMainMenu.openRentals(viewer)"));
        assertTrue(reception.contains("EnterpriseCoreMenu.open(viewer,core)"));
        assertTrue(reception.contains("corePos.distSqr(player.blockPosition())>64"));
        assertFalse(reception.contains("KingdomBankData"));
    }
}
