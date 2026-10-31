package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class KingdomEnterprisePayrollArrearsTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/"+name));
    }
    @Test void capIsExactlyThreeScheduledDays(){
        assertEquals(3,KingdomEnterprisePayrollData.MAX_ARREARS_DAYS);
        assertEquals(6,KingdomEnterprisePayrollData.arrearsLimit(2));
        assertEquals(30000,KingdomEnterprisePayrollData.arrearsLimit(10000));
        assertThrows(IllegalArgumentException.class,
                ()->KingdomEnterprisePayrollData.arrearsLimit(0));
    }
    @Test void unpaidDebtMustNotBeCancelledByHiringOrSync() throws Exception {
        String payroll=source("KingdomEnterprisePayrollData.java");
        assertTrue(payroll.contains("Math.min(capacity,elapsed"));
        assertTrue(payroll.contains("total<cap"));
        assertTrue(payroll.contains("old!=null&&!old.active()&&owed>0"));
        assertTrue(payroll.contains("w.contractPaused)continue"));
        assertTrue(payroll.contains("latest.owed()>0"));
        assertTrue(payroll.contains("worker.contractPaused=false"));
    }
    @Test void workerNavigatesToRealCashboxRatherThanReceivingFreeWages() throws Exception {
        String mod=source("KingdomNpcMod.java");
        assertTrue(mod.contains("seekBackpay(server,level,npc,w)"));
        assertTrue(mod.contains("level.hasChunkAt(dest)"));
        assertTrue(mod.contains("firm.cash()<=0"));
        String enterprise=source("KingdomEnterpriseData.java");
        assertTrue(enterprise.contains("KingdomPhysicalCash.takeForNpc"));
    }
}
