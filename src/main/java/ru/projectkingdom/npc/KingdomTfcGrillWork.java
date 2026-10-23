package ru.projectkingdom.npc;

import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import ru.projectkingdom.enterprise.EnterpriseKind;

/**
 * Native TFC grill operator. The GRILL (not Kingdom) heats and cooks items using
 * actual TFC heating recipes, its original fuel, duration and food traits.
 * Unsupported random-yield, fluid-only or same-item recipes fail closed.
 */
public final class KingdomTfcGrillWork {
    private KingdomTfcGrillWork() {}
    private static boolean initialized, compatible;
    private static Method recipeFor, outputFluid, chance, light;
    private static final int FUEL = 3, FIRST = 4, LAST = 8;

    public static boolean isGrill(ServerLevel level, KingdomNpcData.Worker w) {
        if (w == null || !w.hasWorkstation
                || !("baker".equals(w.job) || "cook".equals(w.job) || "butcher".equals(w.job))
                || !ModList.get().isLoaded("tfc")) return false;
        BlockPos pos = new BlockPos(w.workX, w.workY, w.workZ);
        return level.hasChunkAt(pos) && "tfc:grill".equals(
            BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString());
    }

    private static boolean initialize() {
        if (initialized) return compatible;
        initialized = true;
        try {
            Class<?> heating = Class.forName("net.dries007.tfc.common.recipes.HeatingRecipe");
            Class<?> pit = Class.forName("net.dries007.tfc.common.blockentities.AbstractFirepitBlockEntity");
            recipeFor = heating.getMethod("getRecipe", ItemStack.class);
            outputFluid = heating.getMethod("getDisplayOutputFluid");
            chance = heating.getMethod("getChance");
            light = pit.getMethod("light", net.minecraft.world.level.block.state.BlockState.class);
            compatible = true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            System.err.println("[Kingdom] Native TFC grill methods not available: " + ex);
        }
        return compatible;
    }

    /** Only deterministic recipes with an explicit NEW item and no liquid output. */
    private static ItemStack output(ServerLevel level, ItemStack input) {
        if (!initialize() || input.isEmpty()) return ItemStack.EMPTY;
        try {
            Object found = recipeFor.invoke(null, input.copy());
            if (!(found instanceof Recipe<?> recipe)
                    || ((Number)chance.invoke(found)).floatValue() < 1f
                    || !(outputFluid.invoke(found) instanceof FluidStack fluid)
                    || !fluid.isEmpty()) return ItemStack.EMPTY;
            ItemStack out = recipe.getResultItem(level.registryAccess());
            if (out == null || out.isEmpty() || out.getCount() != 1) return ItemStack.EMPTY;
            // A cook must not smelt metal or fire ceramics by using the food grill.
            if (!input.getItem().isEdible() || !out.getItem().isEdible()) return ItemStack.EMPTY;
            if (id(out).equals(id(input)) || KingdomCurrency.value(out) > 0
                    || out.is(KingdomCurrency.WALLET.get())) return ItemStack.EMPTY;
            return out;
        } catch (ReflectiveOperationException | ClassCastException ex) {
            compatible = false; // Version mismatch disables adapter rather than mutating inventory.
            System.err.println("[Kingdom] Native TFC heating recipe unavailable: " + ex);
            return ItemStack.EMPTY;
        }
    }

    private static String id(ItemStack item) {
        return BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
    }

    private static boolean bakery(ServerLevel level, KingdomNpcData.Worker worker) {
        if (worker.employer == null || !KingdomNpcWorkday.employed(level.getServer(), worker))
            return false;
        var firms = KingdomEnterpriseData.get(level.getServer());
        for (var contract : KingdomEnterprisePayrollData.get(level.getServer()).all()) {
            if (!contract.active() || !contract.worker().equals(worker.entityId)
                    || !KingdomProfessionWorkRules.grill(worker.job, firms.kind(contract.enterprise()))
                    || firms.kind(contract.enterprise()) != EnterpriseKind.BAKERY
                    || (!worker.workplaceId.isBlank()
                        && !worker.workplaceId.equals(contract.enterprise().toString()))) continue;
            var firm = firms.find(contract.enterprise());
            var core = firms.core(contract.enterprise());
            if (firm != null && firm.owner().equals(worker.employer)
                    && core != null && core.active() && core.kind() == EnterpriseKind.BAKERY)
                return true;
        }
        return false;
    }

    /** Stage a complete real transfer, including NBT, without editing the chest. */
    private static ItemStack[] plannedDeposit(Container dest, ItemStack item) {
        if (item.isEmpty()) return null;
        ItemStack[] planned = new ItemStack[dest.getContainerSize()];
        for (int s = 0; s < planned.length; s++) planned[s] = dest.getItem(s).copy();
        ItemStack left = item.copy();
        for (int i=0;i<planned.length && !left.isEmpty();i++) {
            var slot=planned[i];
            if (slot.isEmpty() || !ItemStack.isSameItemSameTags(slot,left)
                    || !dest.canPlaceItem(i,left)) continue;
            int count=Math.min(left.getCount(), Math.max(0,
                Math.min(slot.getMaxStackSize(),dest.getMaxStackSize())-slot.getCount()));
            if (count>0) {slot.grow(count); left.shrink(count);}
        }
        for (int i=0;i<planned.length && !left.isEmpty();i++) {
            if (!planned[i].isEmpty() || !dest.canPlaceItem(i,left)) continue;
            int count=Math.min(left.getCount(), Math.min(left.getMaxStackSize(),dest.getMaxStackSize()));
            if(count<1)continue;
            ItemStack stack=left.copy(); stack.setCount(count);
            planned[i]=stack; left.shrink(count);
        }
        return left.isEmpty()?planned:null;
    }

    private static boolean transferToMachine(Container raw, IItemHandler machine, int target,
                                             int rawSlot, ItemStack single) {
        if (!machine.insertItem(target,single.copy(),true).isEmpty()) return false;
        ItemStack extracted=raw.removeItem(rawSlot,1);
        if (!ItemStack.matches(single,extracted)) return false;
        ItemStack remainder=machine.insertItem(target,extracted,false);
        if (!remainder.isEmpty()) {
            // Server-thread transfer; recover from unexpected rejection without minting any item.
            ItemStack old=raw.getItem(rawSlot);
            if(old.isEmpty()) raw.setItem(rawSlot,remainder);
            else if(ItemStack.isSameItemSameTags(old,remainder)
                    && old.getCount()<=old.getMaxStackSize()-remainder.getCount())
                old.grow(remainder.getCount());
            else return false; // Fail closed; never duplicate a failed insertion.
            raw.setChanged();
            return false;
        }
        raw.setChanged();
        return true;
    }

    private static boolean addFuel(Container raw, IItemHandler machine) {
        if (!machine.getStackInSlot(FUEL).isEmpty()) return false;
        for (int i=0;i<raw.getContainerSize();i++) {
            ItemStack available=raw.getItem(i);
            if (available.isEmpty() || !raw.canTakeItem(raw,i,available)
                || KingdomCurrency.value(available)>0
                || available.is(KingdomCurrency.WALLET.get())) continue;
            ItemStack one=available.copy();one.setCount(1);
            if (transferToMachine(raw,machine,FUEL,i,one)) return true;
        }
        return false;
    }

    private static boolean lit(ServerLevel level, BlockPos pos) {
        var state=level.getBlockState(pos);
        for (var property:state.getProperties())
            if ("lit".equals(property.getName()) && property instanceof
                net.minecraft.world.level.block.state.properties.BooleanProperty bool)
                return state.getValue(bool);
        return false;
    }

    /**
     * Called from Kingdom's bounded loaded-NPC worker tick; all chest and
     * station mutations happen on server thread and under territory/contract gates.
     */
    public static boolean tick(ServerLevel level, Villager npc, KingdomNpcData.Worker w) {
        if (!isGrill(level,w) || !initialize() || w.employer==null || !w.hasSource
            || !w.hasDestination || !KingdomNpcDuty.autonomousOrder(w.order)
            || !NpcLife.ableToWork(w.ageYears,w.hunger,w.energy,w.contractPaused)
            || !"CONTINUE_ASSIGNED_JOB".equals(w.autonomousIntent)
            || !w.dimension.equals(level.dimension().location().toString())
            || !bakery(level,w)) return false;

        BlockPos pos=new BlockPos(w.workX,w.workY,w.workZ),
            source=new BlockPos(w.sourceX,w.sourceY,w.sourceZ),
            destination=new BlockPos(w.destX,w.destY,w.destZ);
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(source) || !level.hasChunkAt(destination)
                || pos.distSqr(source)>36 || pos.distSqr(destination)>36) return false;
        var land=KingdomTerritories.get(level.getServer());
        if (!land.usable(level,pos,w.employer,"workstation")
                || !land.usable(level,source,w.employer,"raw")
                || !land.usable(level,destination,w.employer,"finished")) return false;
        if (!(level.getBlockEntity(source) instanceof Container raw)
                || !(level.getBlockEntity(destination) instanceof Container finished)
                || raw==finished) return false;
        BlockEntity grill=level.getBlockEntity(pos);
        if (grill==null) return false;
        if (npc.distanceToSqr(pos.getX()+.5,pos.getY(),pos.getZ()+.5)>6.25) {
            npc.getNavigation().moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,.75);
            return false;
        }
        IItemHandler machine=grill.getCapability(ForgeCapabilities.ITEM_HANDLER,null).orElse(null);
        if (machine==null || machine.getSlots()<9) return false;
        var records=KingdomTfcGrillData.get(level.getServer());
        var job=records.get(w.entityId);
        if (job!=null && (!job.dimension().equals(w.dimension) || job.position()!=pos.asLong())) {
            // Never claim another workstation's goods.
            records.remove(w.entityId);
            return false;
        }
        if (job!=null) {
            ItemStack current=machine.getStackInSlot(job.slot());
            if (current.isEmpty() || (!id(current).equals(job.input())
                    && !id(current).equals(job.output()))) {
                records.remove(w.entityId); // Someone else changed it; leave their item alone.
                return false;
            }
            if (!id(current).equals(job.output())) {
                // Use TFC's actual recipe and heat ticks, not a simulated countdown.
                addFuel(raw,machine);
                if (!lit(level,pos)) {
                    try { light.invoke(grill,level.getBlockState(pos)); }
                    catch (ReflectiveOperationException | IllegalArgumentException ex) {
                        compatible=false;
                    }
                }
                return false;
            }
            ItemStack[] deposit=plannedDeposit(finished,current);
            if (deposit==null || !ItemStack.matches(
                    machine.extractItem(job.slot(),current.getCount(),true),current)) return false;
            ItemStack taken=machine.extractItem(job.slot(),current.getCount(),false);
            if (!ItemStack.matches(taken,current)) return false;
            for (int i=0;i<deposit.length;i++) finished.setItem(i,deposit[i]);
            finished.setChanged();
            records.remove(w.entityId);
            w.workUnits++;
            w.produced+=taken.getCount();
            w.skillXp++; w.energy=Math.max(0,w.energy-1);
            KingdomNpcData.get(level.getServer()).setDirty();
            return true;
        }

        // Only start from a vacant slot, so an NPC never takes a player's unrelated items.
        int slot=-1;
        for (int s=FIRST;s<=LAST;s++)
            if(machine.getStackInSlot(s).isEmpty()
                    && !records.reservedByOther(w.entityId,w.dimension,pos.asLong(),s)){
                slot=s;break;
            }
        if(slot<0) return false;
        for(int i=0;i<raw.getContainerSize();i++) {
            ItemStack available=raw.getItem(i);
            if(available.isEmpty() || !raw.canTakeItem(raw,i,available)) continue;
            ItemStack one=available.copy();one.setCount(1);
            ItemStack expected=output(level,one);
            if(expected.isEmpty() || plannedDeposit(finished,expected)==null
                    || !machine.insertItem(slot,one.copy(),true).isEmpty()) continue;
            if(!transferToMachine(raw,machine,slot,i,one)) continue;
            records.put(new KingdomTfcGrillData.Job(w.entityId,w.dimension,pos.asLong(),
                slot,id(one),id(expected)));
            addFuel(raw,machine);
            if(!lit(level,pos)) {
                try {light.invoke(grill,level.getBlockState(pos));}
                catch(ReflectiveOperationException | IllegalArgumentException ex){compatible=false;}
            }
            return true;
        }
        return false;
    }
}
