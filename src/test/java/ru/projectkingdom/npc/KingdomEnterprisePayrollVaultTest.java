package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Regression guards for the physically backed enterprise payroll vault. */
final class KingdomEnterprisePayrollVaultTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test void salaryVaultIsASeparateStorageRole() throws Exception {
        String roles=read("src/main/java/ru/projectkingdom/enterprise/EnterpriseBindingRole.java");
        String access=read("src/main/java/ru/projectkingdom/enterprise/EnterpriseAccessService.java");
        String payroll=read("src/main/java/ru/projectkingdom/npc/KingdomEnterprisePayrollData.java");
        assertTrue(roles.contains("SALARY_STORAGE"));
        assertTrue(access.contains("role==EnterpriseBindingRole.CASHBOX"));
        assertTrue(payroll.contains("EnterpriseBindingRole.SALARY_STORAGE"));
        assertTrue(payroll.contains("KingdomPhysicalCash.deposit(owner,access.container(),amount)"));
    }

    @Test void npcPayrollUsesRealCoinsAndRequiresPhysicalProximity() throws Exception {
        String payroll=read("src/main/java/ru/projectkingdom/npc/KingdomEnterprisePayrollData.java");
        int start=payroll.indexOf("public boolean claimNpc");
        int end=payroll.indexOf("private static java.util.List",start);
        assertTrue(start>=0&&end>start);
        String claim=payroll.substring(start,end);
        assertTrue(claim.contains("KingdomPhysicalCash.value(salary.container())"));
        assertTrue(claim.contains("KingdomPhysicalCash.takeForNpc(salary.container(),amount)"));
        assertTrue(claim.contains("npc.distanceToSqr"));
        assertFalse(claim.contains("firm.cash()"));
        assertTrue(payroll.contains("public boolean payNpcNow"));
        assertTrue(payroll.contains("public boolean depositSalary"));
    }

    @Test void employerCanManuallyPayNpcFromTheSalaryVault() throws Exception {
        String menu=read("src/main/java/ru/projectkingdom/npc/KingdomNpcMenu.java");
        String screen=read("src/main/java/ru/projectkingdom/enterprise/EnterpriseCoreScreen.java");
        String core=read("src/main/java/ru/projectkingdom/enterprise/EnterpriseCoreMenu.java");
        assertTrue(menu.contains("payNpcNow(viewer,w)"));
        assertTrue(menu.contains("slotId==13"));
        assertTrue(core.contains("BIND_SALARY"));
        assertTrue(core.contains("DEPOSIT_SALARY_100"));
        assertTrue(screen.contains("Привязать зарплатный сейф"));
        assertTrue(screen.contains("Зарплатный резерв"));
    }

    @Test void suspendedAndActiveEmployeesCanNavigateToTheSalaryVault() throws Exception {
        String movement=read("src/main/java/ru/projectkingdom/npc/KingdomNpcMod.java");
        int start=movement.indexOf("private static boolean seekBackpay");
        int end=movement.indexOf("private static void controlLoadedResidents",start);
        assertTrue(start>=0&&end>start);
        String route=movement.substring(start,end);
        assertTrue(route.contains("EnterpriseBindingRole.SALARY_STORAGE"));
        assertTrue(route.contains("KingdomPhysicalCash.value(vault.container())"));
        assertFalse(route.contains("firm.cash()"));
        assertTrue(route.contains("npc.getNavigation().moveTo"));
    }

    @Test void legacyEnterpriseTillIsNotThePayrollVault() throws Exception {
        String payroll=read("src/main/java/ru/projectkingdom/npc/KingdomEnterprisePayrollData.java");
        int start=payroll.indexOf("public boolean claimNpc");
        int end=payroll.indexOf("private static java.util.List",start);
        String claim=payroll.substring(start,end);
        assertTrue(claim.contains("EnterpriseBindingRole.SALARY_STORAGE"));
        assertFalse(claim.contains("EnterpriseBindingRole.CASHBOX"));
    }
    @Test void playerAndNpcEmployeesUseTheSameDedicatedSalaryVault() throws Exception {
        String payroll=read("src/main/java/ru/projectkingdom/npc/KingdomEnterprisePayrollData.java");
        int start=payroll.indexOf("public boolean claim(MinecraftServer server,ServerPlayer worker,UUID enterprise)");
        int end=payroll.indexOf("public boolean claimAny",start);
        assertTrue(start>=0&&end>start);
        String claim=payroll.substring(start,end);
        assertTrue(claim.contains("EnterpriseBindingRole.SALARY_STORAGE"));
        assertTrue(claim.contains("KingdomPhysicalCash.withdraw(worker,access.container(),amount)"));
        assertFalse(claim.contains("payVerifiedWage"));
    }

}
