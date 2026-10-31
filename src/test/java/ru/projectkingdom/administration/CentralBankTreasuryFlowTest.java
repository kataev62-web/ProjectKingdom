package ru.projectkingdom.administration;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectkingdom.npc.KingdomTaxData;

final class CentralBankTreasuryFlowTest {
    private static String source(String subpath) throws Exception {
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/"+subpath));
    }
    @Test void treasuryIsPublicForPaymentsButGovernanceRemainsPrivate(){
        assertTrue(AdministrativePermissionService.mayUsePublic(AdministrativeKind.TREASURY));
        assertFalse(AdministrativePermissionService.mayManage(UUID.randomUUID(),
                UUID.randomUUID(),UUID.randomUUID(),Set.of(),AdministrativeKind.TREASURY));
        assertFalse(AdministrativePermissionService.mayUsePublic(AdministrativeKind.LAND_OFFICE));
    }
    @Test void townHallHasNoTillAndTreasuryPresentsExactDebtAndTwoPayments() throws Exception {
        String ui=source("administration/AdministrativeCoreScreen.java");
        String packet=source("administration/AdministrativeTextPacket.java");
        assertFalse(ui.contains("Привязать кассу города"));
        assertFalse(ui.contains("Привязать хранилище"));
        assertTrue(ui.contains("Оплатить весь долг"));
        assertTrue(ui.contains("Оплатить часть"));
        assertTrue(ui.contains("menu.taxDebt()"));
        assertTrue(packet.contains("field.equals(\"pay_taxes\")"));
        assertTrue(packet.contains("taxes.payAtTreasury(sender,packet.townId,amount)"));
        assertTrue(packet.contains("menu.stillValid(sender)"));
        String guarded=source("administration/AdministrativeActionPacket.java");
        assertTrue(guarded.contains("packet.action==BIND_HALL_CASHBOX"));
        assertTrue(guarded.contains("packet.action==BIND_TREASURY"));
    }
    @Test void cashTaxesAndMunicipalLandGoToBankNotTownHallTill() throws Exception {
        String tax=source("npc/KingdomTaxData.java");
        String reservation=source("npc/KingdomLandReservations.java");
        String expansion=source("npc/KingdomCityExpansionPurchase.java");
        for(String target:new String[]{tax,reservation,expansion}) {
            assertTrue(target.contains("AdministrativeBindingRole.BANK_VAULT"));
            assertFalse(target.contains("AdministrativeBindingRole.TREASURY_VAULT"));
        }
        assertTrue(tax.contains("KingdomPhysicalCash.deposit(player,vault,requested)"));
        assertTrue(tax.contains("civics.depositVerifiedTax(townId,requested)"));
        assertTrue(tax.contains("previous.amount()-requested"));
    }
    @Test void bankMessagesExplainWalletAndPhysicalBankDeficits() throws Exception {
        String bank=source("npc/KingdomBankData.java");
        assertTrue(bank.contains("admin.heldExpansionProceeds(town)"));
        assertTrue(bank.contains("freePhysicalCopper("));
        assertTrue(bank.contains("Старый баланс"));
        assertTrue(bank.contains("кошельке"));
        for(var result:KingdomTaxData.PaymentResult.values()) {
            String message=KingdomTaxData.paymentMessage(result);
            assertNotNull(message);
            assertTrue(message.matches("(?s).*[\\p{IsCyrillic}].*"),result.name());
        }
    }
}
