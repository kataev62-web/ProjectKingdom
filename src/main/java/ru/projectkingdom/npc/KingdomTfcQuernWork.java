package ru.projectkingdom.npc;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import ru.projectkingdom.enterprise.EnterpriseKind;

/**
 * Bakery miller's TFC quern adapter. Uses the ACTUAL TFC quern's inventory, recipes,
 * native 90-tick grinding, and handstone durability. No shadow recipe, fake output,
 * chunk loading, virtual stock, or external mod class in our compile classpath.
 *
 * The class names and methods are from TFC 1.20.x. On incompatible versions the
 * adapter stops (fails closed); other Kingdom professions still work.
 */
public final class KingdomTfcQuernWork {
    private KingdomTfcQuernWork() {}

    private static final int HANDSTONE = 0;
    private static final int INPUT = 1;
    private static final int OUTPUT = 2;
    private static boolean initialized;
    private static boolean compatible;
    private static Constructor<?> inventoryCtor;
    private static Method findRecipe;
    private static Method startGrinding;
    private static Method isGrinding;

    /** This deliberately does not activate on a vanilla stonecutter or a fake quern block. */
    public static boolean isQuern(ServerLevel level, KingdomNpcData.Worker worker) {
        if (!"baker".equals(worker.job) || !worker.hasWorkstation
                || !ModList.get().isLoaded("tfc")) return false;
        BlockPos pos = new BlockPos(worker.workX, worker.workY, worker.workZ);
        return level.hasChunkAt(pos) && "tfc:quern".equals(
            BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString());
    }

    private static boolean init() {
        if (initialized) return compatible;
        initialized = true;
        try {
            Class<?> inv = Class.forName("net.dries007.tfc.common.recipes.inventory.ItemStackInventory");
            Class<?> quernRecipe = Class.forName("net.dries007.tfc.common.recipes.QuernRecipe");
            Class<?> quern = Class.forName("net.dries007.tfc.common.blockentities.QuernBlockEntity");
            inventoryCtor = inv.getConstructor(ItemStack.class);
            findRecipe = quernRecipe.getMethod("getRecipe", Level.class, inv);
            startGrinding = quern.getMethod("startGrinding");
            isGrinding = quern.getMethod("isGrinding");
            compatible = true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            System.err.println("[Kingdom] TFC quern adapter unavailable: " + ex);
        }
        return compatible;
    }

    /** Let TFC itself match datapack recipes and assemble provider-dependent items. */
    @SuppressWarnings("unchecked")
    private static ItemStack recipeOutput(ServerLevel level, ItemStack input) {
        if (!init() || input.isEmpty()) return ItemStack.EMPTY;
        try {
            Object wrapped = inventoryCtor.newInstance(input.copy());
            Object found = findRecipe.invoke(null, level, wrapped);
            if (!(found instanceof Recipe<?> recipe)) return ItemStack.EMPTY;
            Recipe<Container> actual = (Recipe<Container>) recipe;
            Container inventory = (Container) wrapped;
            if (!actual.matches(inventory, level)) return ItemStack.EMPTY;
            ItemStack made = actual.assemble(inventory, level.registryAccess());
            return made == null ? ItemStack.EMPTY : made;
        } catch (ReflectiveOperationException | ClassCastException ex) {
            System.err.println("[Kingdom] TFC quern recipe invocation disabled: " + ex);
            compatible = false; // Do not risk running with an incompatible TFC API.
            return ItemStack.EMPTY;
        }
    }

    private static boolean grinding(BlockEntity entity) {
        if (!init()) return false;
        try { return Boolean.TRUE.equals(isGrinding.invoke(entity)); }
        catch (ReflectiveOperationException | IllegalArgumentException ex) {
            compatible = false;
            return false;
        }
    }

    private static boolean start(BlockEntity entity) {
        if (!init()) return false;
        try { return Boolean.TRUE.equals(startGrinding.invoke(entity)); }
        catch (ReflectiveOperationException | IllegalArgumentException ex) {
            compatible = false;
            return false;
        }
    }

    /** Check a full destination mutation before taking anything from the quern. */
    private static ItemStack[] depositPlan(Container to, ItemStack output) {
        if (output.isEmpty()) return null;
        ItemStack[] planned = new ItemStack[to.getContainerSize()];
        for (int i = 0; i < planned.length; i++) planned[i] = to.getItem(i).copy();
        ItemStack remainder = output.copy();
        for (int i = 0; i < planned.length && !remainder.isEmpty(); i++) {
            ItemStack slot = planned[i];
            if (slot.isEmpty() || !ItemStack.isSameItemSameTags(slot, remainder)
                    || !to.canPlaceItem(i, remainder)) continue;
            int moved = Math.min(remainder.getCount(),
                Math.max(0, Math.min(slot.getMaxStackSize(), to.getMaxStackSize()) - slot.getCount()));
            slot.grow(moved);
            remainder.shrink(moved);
        }
        for (int i = 0; i < planned.length && !remainder.isEmpty(); i++) {
            if (!planned[i].isEmpty() || !to.canPlaceItem(i, remainder)) continue;
            int moved = Math.min(remainder.getCount(),
                Math.min(remainder.getMaxStackSize(), to.getMaxStackSize()));
            if (moved < 1) continue;
            ItemStack slot = remainder.copy();
            slot.setCount(moved);
            planned[i] = slot;
            remainder.shrink(moved);
        }
        return remainder.isEmpty() ? planned : null;
    }

    private static boolean mergeable(ItemStack current, ItemStack product, int max) {
        return current.isEmpty() ? product.getCount() <= max
            : ItemStack.isSameItemSameTags(current, product)
                && current.getCount() <= Math.min(current.getMaxStackSize(), max) - product.getCount();
    }

    /**
     * Runs only for a loaded, nearby, contracted baker at an active bakery.
     * Finished flour is collected before starting the next grind. An interrupted
     * operation remains inside TFC's own persisted block entity.
     */
    public static boolean tick(ServerLevel level, Villager npc, KingdomNpcData.Worker worker) {
        if (!isQuern(level, worker) || !init() || !KingdomNpcDuty.autonomousOrder(worker.order)
                || !NpcLife.ableToWork(worker.ageYears, worker.hunger, worker.energy, worker.contractPaused)
                || !worker.hasSource || !worker.hasDestination || worker.employer == null
                || !KingdomNpcWorkday.employed(level.getServer(), worker)
                || !worker.dimension.equals(level.dimension().location().toString())) return false;

        var enterprises = KingdomEnterpriseData.get(level.getServer());
        boolean assigned = false;
        for (var contract : KingdomEnterprisePayrollData.get(level.getServer()).all()) {
            if (!contract.active() || !contract.worker().equals(worker.entityId)
                    || !KingdomProfessionWorkRules.quern(worker.job, enterprises.kind(contract.enterprise()))
                    || ( !worker.workplaceId.isBlank()
                        && !worker.workplaceId.equals(contract.enterprise().toString()))) continue;
            var firm = enterprises.find(contract.enterprise());
            var core = enterprises.core(contract.enterprise());
            if (firm != null && firm.owner().equals(worker.employer)
                    && core != null && core.active() && core.kind() == EnterpriseKind.BAKERY) {
                assigned = true; break;
            }
        }
        if (!assigned) return false;

        BlockPos station = new BlockPos(worker.workX, worker.workY, worker.workZ);
        BlockPos rawPos = new BlockPos(worker.sourceX, worker.sourceY, worker.sourceZ);
        BlockPos finishedPos = new BlockPos(worker.destX, worker.destY, worker.destZ);
        if (station.distSqr(rawPos) > 36 || station.distSqr(finishedPos) > 36
                || !level.hasChunkAt(rawPos) || !level.hasChunkAt(finishedPos)
                || !level.hasChunkAt(station)) return false;

        var land = KingdomTerritories.get(level.getServer());
        if (!land.usable(level, station, worker.employer, "workstation")
                || !land.usable(level, rawPos, worker.employer, "raw")
                || !land.usable(level, finishedPos, worker.employer, "finished")) return false;
        BlockEntity sourceEntity = level.getBlockEntity(rawPos);
        BlockEntity destinationEntity = level.getBlockEntity(finishedPos);
        BlockEntity quernEntity = level.getBlockEntity(station);
        if (!(sourceEntity instanceof Container raw)
                || !(destinationEntity instanceof Container finished)
                || quernEntity == null || raw == finished) return false;

        if (npc.distanceToSqr(station.getX() + .5, station.getY(), station.getZ() + .5) > 6.25) {
            npc.getNavigation().moveTo(station.getX() + .5, station.getY(), station.getZ() + .5, .75);
            return false;
        }
        IItemHandler machine = quernEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        if (machine == null || machine.getSlots() < 3) return false;
        if (machine.getStackInSlot(HANDSTONE).isEmpty()) return false; // Never invent handstones.

        ItemStack produced = machine.getStackInSlot(OUTPUT).copy();
        if (!produced.isEmpty()) {
            ItemStack[] planned = depositPlan(finished, produced);
            if (planned == null || !ItemStack.matches(
                    machine.extractItem(OUTPUT, produced.getCount(), true), produced)) return false;
            ItemStack taken = machine.extractItem(OUTPUT, produced.getCount(), false);
            if (!ItemStack.matches(taken, produced)) return false;
            for (int i = 0; i < planned.length; i++) finished.setItem(i, planned[i]);
            finished.setChanged();
            worker.workUnits++;
            worker.produced += taken.getCount();
            worker.skillXp++;
            worker.energy = Math.max(0, worker.energy - 1);
            KingdomNpcData.get(level.getServer()).setDirty();
            return true;
        }

        if (grinding(quernEntity)) return false;
        ItemStack machineInput = machine.getStackInSlot(INPUT).copy();
        if (!machineInput.isEmpty()) {
            ItemStack expected = recipeOutput(level, machineInput);
            if (expected.isEmpty() || KingdomCurrency.value(expected) > 0
                    || expected.is(KingdomCurrency.WALLET.get())
                    || !mergeable(machine.getStackInSlot(OUTPUT), expected, machine.getSlotLimit(OUTPUT))
                    || depositPlan(finished, expected) == null) return false;
            return start(quernEntity); // TFC itself consumes the input after its work cycle.
        }

        for (int slot = 0; slot < raw.getContainerSize(); slot++) {
            ItemStack available = raw.getItem(slot);
            if (available.isEmpty() || !raw.canTakeItem(raw, slot, available)
                    || KingdomCurrency.value(available) > 0
                    || available.is(KingdomCurrency.WALLET.get())) continue;
            ItemStack one = available.copy(); one.setCount(1);
            ItemStack expected = recipeOutput(level, one);
            if (expected.isEmpty() || KingdomCurrency.value(expected) > 0
                    || expected.is(KingdomCurrency.WALLET.get())
                    || expected.getCount() < 1
                    || !mergeable(machine.getStackInSlot(OUTPUT), expected, machine.getSlotLimit(OUTPUT))
                    || depositPlan(finished, expected) == null
                    || !machine.insertItem(INPUT, one.copy(), true).isEmpty()) continue;
            ItemStack removed = raw.removeItem(slot, 1);
            if (!ItemStack.matches(removed, one)) return false;
            ItemStack leftover = machine.insertItem(INPUT, removed, false);
            if (!leftover.isEmpty()) {
                // Insertion was pre-validated on the server thread; restore the source on failure.
                ItemStack restored = raw.getItem(slot);
                if (restored.isEmpty()) raw.setItem(slot, leftover);
                else if (ItemStack.isSameItemSameTags(restored, leftover)) restored.grow(leftover.getCount());
                raw.setChanged();
                return false;
            }
            raw.setChanged();
            return start(quernEntity);
        }
        return false;
    }
}
