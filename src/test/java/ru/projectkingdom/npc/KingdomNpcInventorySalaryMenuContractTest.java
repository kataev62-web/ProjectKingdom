package ru.projectkingdom.npc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Wiring regressions: client-facing NPC inventory and manual physical salary. */
final class KingdomNpcInventorySalaryMenuContractTest {
    private static String read(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/"+file));
    }
    @Test void managementHasInventoryAndSalaryButtons() throws Exception {
        String menu=read("KingdomNpcMenu.java");
        assertTrue(menu.contains("case 17 ->"));
        assertTrue(menu.contains("KingdomNpcBagMenu.open(viewer,w)"));
        assertTrue(menu.contains("case 13 ->"));
        assertTrue(menu.contains("payNpcLooseCopper(viewer,w)"));
        assertTrue(menu.contains("ВЫПЛАТИТЬ ЗАРПЛАТУ"));
    }
    @Test void bagIsRealAuthorisedAndPersistent() throws Exception {
        String bag=read("KingdomNpcBagMenu.java");
        assertTrue(bag.contains("MenuType.GENERIC_9x4"));
        assertTrue(bag.contains("worker.fieldInventory.add(slot.copy())"));
        assertTrue(bag.contains("worker.employer"));
        assertTrue(bag.contains("viewer.distanceToSqr(entity)"));
        assertTrue(bag.contains("KingdomNpcData.get(server).setDirty()"));
        String mod=read("KingdomNpcMod.java");
        assertTrue(mod.contains("KingdomNpcBagMenu.isOpen(w.entityId)"));
    }
    @Test void manualWagesConsumeOriginalCoinsAndReduceRealArrears() throws Exception {
        String source=read("KingdomEnterprisePayrollData.java");
        assertTrue(source.contains("payNpcLooseCopper(ServerPlayer payer"));
        assertTrue(source.contains("stack.is(KingdomCurrency.COPPER.get())"));
        assertTrue(source.contains("appendPhysicalCopper(proposed,coins)"));
        assertTrue(source.contains("inv.setItem(debit.slot(),next)"));
        assertTrue(source.contains("selected.owed()-paid"));
        assertTrue(source.contains("KingdomNpcData.get(payer.getServer()).setDirty()"));
    }
}
