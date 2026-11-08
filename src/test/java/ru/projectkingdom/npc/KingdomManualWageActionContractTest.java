package ru.projectkingdom.npc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source contract to keep the manual payroll path wired to the physical money system.
 * Live Minecraft tests must additionally verify item conservation and worker resumption.
 */
final class KingdomManualWageActionContractTest {
    @Test void salaryIconInvokesRealWalletTransferAndDoesNotDepositIntoLegacyReserve() throws Exception {
        String menu=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomNpcMenu.java"));
        String payroll=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomEnterprisePayrollData.java"));
        assertTrue(menu.contains("slotId==13"));
        assertTrue(menu.contains("case 13 ->"));
        assertTrue(menu.contains("wages.payNpcFromWallet(viewer,w)"));
        assertTrue(payroll.contains("KingdomCurrency.balance(owner)"));
        assertTrue(payroll.contains("coinsWithExisting(worker.wagePurse,amount)"));
        assertTrue(payroll.contains("KingdomCurrency.debit(owner,amount)"));
        assertTrue(payroll.contains("worker.contractPaused=false"));
        assertTrue(payroll.contains("worker.unpaidDays=0"));
        assertTrue(payroll.contains("worker.wagePurse.addAll(received)"));
        assertTrue(payroll.contains("firm.owner().equals(owner.getUUID())"));
    }
}
