package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/** Reproductions for issue #94: copying getters, refused/partial writes and crash-replay safety. */
final class KingdomConstructionMaterialSafetyTest {
    private static class CopyingDepot extends SimpleContainer {
        CopyingDepot() { super(1); }
        @Override public ItemStack getItem(int slot) { return super.getItem(slot).copy(); }
    }
    private static final class RejectingDepot extends CopyingDepot {
        boolean reject;
        @Override public void setItem(int slot, ItemStack stack) {
            if (reject && stack.getCount() < super.getItem(slot).getCount()) return;
            super.setItem(slot, stack);
        }
    }
    private static final class PartialDepot extends CopyingDepot {
        boolean corrupt;
        @Override public void setItem(int slot, ItemStack stack) {
            if (corrupt && stack.getCount() == 3) {
                super.setItem(slot, new ItemStack(Items.STONE, 2));
            } else if (!corrupt) {
                super.setItem(slot, stack);
            } // restoration rejected once the write went wrong
        }
    }

    @Test void defensiveGetterDebitsExactlyOneAndPersistsTheSetter() {
        var depot = new CopyingDepot();
        depot.setItem(0, new ItemStack(Items.STONE, 5));
        var before = depot.getItem(0);
        var prepared = KingdomConstructionMaterials.prepare(depot, 0, Items.STONE);
        assertNotNull(prepared);
        assertEquals(KingdomConstructionMaterials.Debit.PAID,
            KingdomConstructionMaterials.debit(depot, prepared));
        assertEquals(4, depot.getItem(0).getCount());
        assertEquals(5, before.getCount(), "A defensive getter returns an immutable snapshot");
        assertTrue(depot.getItem(0).is(Items.STONE));
        assertEquals(0, depot.getItem(0).getTag() == null ? 0 : 1);
    }

    @Test void lastItemAndPartiallyFilledSourceSlotAreHandledExactly() {
        var depot = new CopyingDepot();
        depot.setItem(0, new ItemStack(Items.STONE, 1));
        var one = KingdomConstructionMaterials.prepare(depot, 0, Items.STONE);
        assertNotNull(one);
        assertEquals(KingdomConstructionMaterials.Debit.PAID,
            KingdomConstructionMaterials.debit(depot, one));
        assertTrue(depot.getItem(0).isEmpty());
        depot.setItem(0, new ItemStack(Items.STONE, 63));
        var partial = KingdomConstructionMaterials.prepare(depot, 0, Items.STONE);
        assertNotNull(partial);
        assertEquals(KingdomConstructionMaterials.Debit.PAID,
            KingdomConstructionMaterials.debit(depot, partial));
        assertEquals(62, depot.getItem(0).getCount());
    }

    @Test void declinedWriteDoesNotLoseMaterialOrPretendToHaveDebited() {
        var depot = new RejectingDepot();
        depot.setItem(0, new ItemStack(Items.STONE, 3));
        var prepared = KingdomConstructionMaterials.prepare(depot, 0, Items.STONE);
        assertNotNull(prepared);
        depot.reject = true;
        assertEquals(KingdomConstructionMaterials.Debit.REJECTED,
            KingdomConstructionMaterials.debit(depot, prepared));
        assertEquals(3, depot.getItem(0).getCount());
        assertTrue(ItemStack.matches(prepared.original(), depot.getItem(0)));
    }

    @Test void partialUnrestorableWriteIsQuarantinedNotCredited() {
        var depot = new PartialDepot();
        depot.setItem(0, new ItemStack(Items.STONE, 4));
        var prepared = KingdomConstructionMaterials.prepare(depot, 0, Items.STONE);
        assertNotNull(prepared);
        depot.corrupt = true;
        assertEquals(KingdomConstructionMaterials.Debit.UNCERTAIN,
            KingdomConstructionMaterials.debit(depot, prepared));
        assertEquals(2, depot.getItem(0).getCount());
    }

    @Test void taggedMaterialAndWrongItemRemainPhysicallyIntact() {
        var depot = new CopyingDepot();
        var tagged = new ItemStack(Items.STONE, 3);
        CompoundTag tag = new CompoundTag();
        tag.putString("custom", "tfc/state");
        tagged.setTag(tag);
        depot.setItem(0, tagged.copy());
        assertNull(KingdomConstructionMaterials.prepare(depot, 0, Items.STONE),
            "Raw setBlock must not discard a custom BlockEntityTag or other NBT");
        assertTrue(ItemStack.matches(tagged, depot.getItem(0)));
        assertNull(KingdomConstructionMaterials.prepare(depot, 0, Items.DIRT));
        assertTrue(ItemStack.matches(tagged, depot.getItem(0)));
    }

    @Test void stateChangeBetweenPreflightAndDebitCannotConsumeMaterial() {
        var depot = new CopyingDepot();
        depot.setItem(0, new ItemStack(Items.STONE, 5));
        var prepared = KingdomConstructionMaterials.prepare(depot, 0, Items.STONE);
        assertNotNull(prepared);
        depot.setItem(0, new ItemStack(Items.STONE, 2)); // another legal operation intervened
        assertEquals(KingdomConstructionMaterials.Debit.REJECTED,
            KingdomConstructionMaterials.debit(depot, prepared));
        assertEquals(2, depot.getItem(0).getCount());
    }

    @Test void pendingConstructionStepSurvivesRestartAndCannotReplay() {
        UUID owner = UUID.randomUUID();
        var db = new KingdomConstructionData();
        var project = db.add(owner, "minecraft:overworld", "HOUSE", 4, 70, 5);
        BlockPos target = new BlockPos(4, 71, 5);
        assertTrue(db.beginPendingStep(project.id(), target, "minecraft:stone"));
        assertFalse(db.beginPendingStep(project.id(), target, "minecraft:stone"));
        var reload = KingdomConstructionData.load(db.save(new CompoundTag()));
        assertTrue(reload.hasPendingStep(project.id()));
        assertEquals(target, reload.pendingStep(project.id()).target());
        assertEquals("minecraft:stone", reload.pendingStep(project.id()).material());
        assertEquals(0, reload.progress(project.id()));
        reload.clearPendingStep(project.id());
        assertTrue(reload.beginPendingStep(project.id(), target, "minecraft:stone"));
        reload.finishPendingStep(project.id(), 3);
        var after = KingdomConstructionData.load(reload.save(new CompoundTag()));
        assertFalse(after.hasPendingStep(project.id()));
        assertEquals(1, after.progress(project.id()));
        assertEquals(KingdomConstructionData.Status.BUILDING, after.find(project.id()).status());
    }

    @Test void rejectedPlacementOrProtectedSourceNeverDemolishesOrDebits() throws Exception {
        String work=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomConstructionWork.java"));
        String data=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomConstructionData.java"));
        String site=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomSiteBuilder.java"));
        assertFalse(work.contains("level.destroyBlock("));
        assertFalse(work.contains("restoreMaterial("));
        assertFalse(work.contains("depot.removeItem("));
        assertTrue(work.indexOf("level.setBlock(target, desired, 3)")
                < work.indexOf("KingdomConstructionMaterials.debit(depot, material)"));
        assertTrue(work.contains("if (!placed || !level.getBlockState(target).equals(desired))"));
        assertTrue(work.contains("if (!occupying.isAir())"));
        assertTrue(work.contains("KingdomConstructionAccess.canBuild(server, level, target, project.owner())"));
        assertTrue(work.contains("KingdomConstructionAccess.canBuild(server, level, adjacent, owner)"));
        assertTrue(work.contains("KingdomContainerAccess.samePhysicalStorage"));
        assertTrue(work.contains("KingdomContainerAccess.resolve(level, adjacent)"));
        assertTrue(work.contains("sites.hasPendingStep(project.id())"));
        assertTrue(data.contains("PendingTarget"));
        assertTrue(site.contains("Убери существующий блок вручную"));
    }
}
