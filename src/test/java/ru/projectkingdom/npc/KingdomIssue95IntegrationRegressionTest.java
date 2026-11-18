package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.projectkingdom.administration.AdministrativeKind;
import ru.projectkingdom.administration.AdministrativePermissionService;
import ru.projectkingdom.administration.KingdomAdministrationData;
import ru.projectkingdom.administration.MunicipalRole;

/**
 * Cross-PR #85/#88/#89 integration regressions, without editing any module
 * implementation. These are NBT and durable-file round trips, not an in-world
 * Forge/TFC restart and not a proof of crash atomicity across Minecraft files.
 */
final class KingdomIssue95IntegrationRegressionTest {
    @TempDir Path temp;
    private static final String ORIGINAL = "0".repeat(64);
    private static final String CHANGED = "a".repeat(64);

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void formerMayorLosesHallPermissionAcrossNbtRestartWithoutLosingOtherOffice() {
        UUID town = UUID.randomUUID(), founder = UUID.randomUUID();
        UUID former = UUID.randomUUID(), stale = UUID.randomUUID(), current = UUID.randomUUID();
        var admin = new KingdomAdministrationData();
        assertTrue(admin.ensureFounder(town, founder));
        assertTrue(admin.appoint(town, founder, former, former, MunicipalRole.MAYOR));
        assertTrue(admin.appoint(town, founder, former, former, MunicipalRole.TREASURER));
        assertTrue(admin.appoint(town, founder, former, stale, MunicipalRole.MAYOR));
        admin.adminChangeMayor(town, former, current);
        var restored = KingdomAdministrationData.fromTag(admin.save(new CompoundTag()));
        // Exercise the idempotent migration also after reloading the saved data.
        restored.adminChangeMayor(town, null, current);
        restored.adminChangeMayor(town, null, current);
        assertEquals(Set.of(MunicipalRole.TREASURER), restored.roles(town, former));
        assertTrue(restored.roles(town, stale).isEmpty());
        assertEquals(Set.of(MunicipalRole.MAYOR), restored.roles(town, current));
        assertFalse(AdministrativePermissionService.mayManage(former, founder, current,
                restored.roles(town, former), AdministrativeKind.TOWN_HALL));
        assertFalse(AdministrativePermissionService.mayManage(stale, founder, current,
                restored.roles(town, stale), AdministrativeKind.TOWN_HALL));
        assertTrue(AdministrativePermissionService.mayManage(former, founder, current,
                restored.roles(town, former), AdministrativeKind.BANK));
        assertEquals(founder, restored.founder(town));
    }

    @Test void farmerAllThirtySixTaggedStacksSurviveNbtAndThirtySeventhIsRejected() {
        var data = new KingdomNpcData();
        var worker = data.add(UUID.randomUUID(), "Farmer", "minecraft:overworld", 0);
        for (int i=0; i<36; i++) {
            ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
            stack.getOrCreateTag().putInt("field_batch", i);
            worker.fieldInventory.add(stack);
        }
        ItemStack extra = new ItemStack(Items.DIAMOND_SWORD);
        extra.getOrCreateTag().putInt("field_batch", 36);
        assertFalse(NpcFieldWork.canFit(worker, List.of(extra)));
        var reloaded = KingdomNpcData.load(data.save(new CompoundTag()));
        var saved = reloaded.all().iterator().next();
        assertEquals(36, saved.fieldInventory.size());
        for (int i=0; i<36; i++) {
            assertEquals(1, saved.fieldInventory.get(i).getCount());
            assertEquals(i, saved.fieldInventory.get(i).getTag().getInt("field_batch"));
        }
    }

    @Test void rejectedWarehouseWriteNeverDebitsFarmerCargoAndCopyBackedWritesWork() {
        var rejected = new SimpleContainer(1) {
            @Override public void setItem(int slot, ItemStack stack) { /* reject write */ }
            @Override public ItemStack getItem(int slot) { return ItemStack.EMPTY; }
        };
        ItemStack held = new ItemStack(Items.WHEAT, 7);
        held.getOrCreateTag().putString("batch", "tfc-field");
        assertEquals(0, NpcFieldWork.transferToWarehouse(rejected, held));
        assertEquals(7, held.getCount());
        assertEquals("tfc-field", held.getTag().getString("batch"));

        var backed = new SimpleContainer(1) {
            @Override public ItemStack getItem(int slot) { return super.getItem(slot).copy(); }
        };
        ItemStack stock = held.copy();
        stock.setCount(60);
        backed.setItem(0, stock);
        assertEquals(4, NpcFieldWork.transferToWarehouse(backed, held));
        assertEquals(64, backed.getItem(0).getCount());
        assertEquals(3, held.getCount());
        assertEquals("tfc-field", backed.getItem(0).getTag().getString("batch"));
    }

    private BankOperationLedger.Snapshot before() {
        return new BankOperationLedger.Snapshot(
            500, 2_000, 200, 200, 250, 30, 30, 0, ORIGINAL, ORIGINAL);
    }
    private BankOperationLedger.Snapshot withdrawn() {
        return new BankOperationLedger.Snapshot(
            540, 1_960, 160, 160, 210, 30, 30, 0, CHANGED, CHANGED);
    }

    @Test void preparedBankIntentAfterRestartIsQuarantinedAndCannotReplay() throws Exception {
        UUID town=UUID.randomUUID(), actor=UUID.randomUUID();
        Path file=temp.resolve("prepared/bank.log");
        var journal=BankOperationLedger.open(file);
        var tx=journal.prepare(town, actor, actor,
                BankOperationLedger.Kind.PERSONAL_WITHDRAW, 40, before());
        assertNotNull(tx);
        var restart=BankOperationLedger.open(file);
        assertTrue(restart.blocked(town));
        assertNull(restart.prepare(town, actor, actor,
                BankOperationLedger.Kind.PERSONAL_WITHDRAW, 40, before()));
        assertFalse(restart.reconcile(tx.id(), withdrawn()));
        assertTrue(restart.blocked(town));
        assertTrue(restart.reconcile(tx.id(), before()), "unchanged physical/account witness");
        assertFalse(BankOperationLedger.open(file).blocked(town));
    }

    @Test void receiptRequiresExactPhysicalAndLedgerWitnessAfterRestart() throws Exception {
        UUID town=UUID.randomUUID(), actor=UUID.randomUUID();
        Path file=temp.resolve("receipt/bank.log");
        var journal=BankOperationLedger.open(file);
        var tx=journal.prepare(town, actor, actor,
                BankOperationLedger.Kind.PERSONAL_WITHDRAW, 40, before());
        assertNotNull(tx);
        assertTrue(journal.receipt(tx, withdrawn()));
        var restart=BankOperationLedger.open(file);
        assertTrue(restart.blocked(town));
        assertFalse(restart.reconcile(tx.id(), before()));
        assertFalse(restart.reconcile(tx.id(), new BankOperationLedger.Snapshot(
            540, 1_960, 160, 160, 210, 30, 30, 0, ORIGINAL, CHANGED)));
        assertTrue(restart.reconcile(tx.id(), withdrawn()));
        assertFalse(BankOperationLedger.open(file).blocked(town));
        assertFalse(BankOperationLedger.open(file).reconcile(tx.id(), withdrawn()),
                "idempotent acknowledgment cannot replay coin mutations");
    }

    @Test void corruptJournalFailsClosedRatherThanAutomaticallyRecovering() throws Exception {
        UUID town=UUID.randomUUID(), actor=UUID.randomUUID();
        Path file=temp.resolve("corrupt/bank.log");
        var journal=BankOperationLedger.open(file);
        assertNotNull(journal.prepare(town,actor,actor,
                BankOperationLedger.Kind.PERSONAL_WITHDRAW, 40, before()));
        Files.writeString(file, "C|truncated", StandardOpenOption.APPEND);
        assertThrows(IOException.class, () -> BankOperationLedger.open(file));
    }

    @Test void npcSavedDataIsUnaffectedByBankQuarantineAndMayorChanges() throws Exception {
        UUID town=UUID.randomUUID(), founder=UUID.randomUUID(), prior=UUID.randomUUID();
        var administration=new KingdomAdministrationData();
        assertTrue(administration.ensureFounder(town,founder));
        assertTrue(administration.appoint(town,founder,prior,prior,MunicipalRole.MAYOR));
        administration.adminChangeMayor(town,prior,founder);
        var savedAdmin=KingdomAdministrationData.fromTag(administration.save(new CompoundTag()));
        var data=new KingdomNpcData();
        var farmer=data.add(UUID.randomUUID(),"Crop collector","minecraft:overworld",0);
        ItemStack harvest=new ItemStack(Items.CARROT, 13);
        harvest.getOrCreateTag().putString("source","field-a");
        farmer.fieldInventory.add(harvest);
        var savedNpc=KingdomNpcData.load(data.save(new CompoundTag()));

        var bank=BankOperationLedger.open(temp.resolve("independent/bank.log"));
        assertNotNull(bank.prepare(town,founder,founder,
                BankOperationLedger.Kind.PERSONAL_WITHDRAW, 40, before()));
        assertTrue(BankOperationLedger.open(temp.resolve("independent/bank.log")).blocked(town));
        assertEquals(Set.of(MunicipalRole.MAYOR),savedAdmin.roles(town,founder));
        assertTrue(savedAdmin.roles(town,prior).isEmpty());
        ItemStack restored=savedNpc.all().iterator().next().fieldInventory.get(0);
        assertEquals(13, restored.getCount());
        assertEquals("field-a", restored.getTag().getString("source"));
    }
}
