package ru.projectkingdom.npc;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/** Owner-bound physical inventory. No automation is exposed before binding. */
public final class KingdomManagedStorageEntity extends BlockEntity implements Container, MenuProvider {
    private UUID owner;
    private final ItemStackHandler items = new ItemStackHandler(27) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final IItemHandler exposed = new IItemHandler() {
        @Override public int getSlots() { return items.getSlots(); }
        @Override public ItemStack getStackInSlot(int slot) { return items.getStackInSlot(slot).copy(); }
        @Override public int getSlotLimit(int slot) { return items.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return canAutomate() && items.isItemValid(slot, stack); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            synchronized (KingdomStorageRuntime.LOCKS) {
                if (!canAutomate()) return stack.copy();
                return items.insertItem(slot, stack.copy(), simulate);
            }
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            synchronized (KingdomStorageRuntime.LOCKS) {
                if (!canAutomate()) return ItemStack.EMPTY;
                return items.extractItem(slot, amount, simulate).copy();
            }
        }
    };
    private LazyOptional<IItemHandler> capability = LazyOptional.of(() -> exposed);
    private TradeProtectedLocations.Location registeredLocation;

    public KingdomManagedStorageEntity(BlockPos pos, BlockState state) {
        super(KingdomManagedStorage.STORAGE_ENTITY.get(), pos, state);
    }
    public UUID owner() { return owner; }
    /** Initial binding only; transferring ownership requires an explicit administrative procedure. */
    public boolean bind(UUID account) {
        if (account == null || owner != null) return false;
        owner = account;
        enrollIfReady();
        setChanged();
        return true;
    }
    private TradeProtectedLocations.Location locationKey() {
        if (level == null || level.isClientSide) return null;
        return new TradeProtectedLocations.Location(level.dimension().location().toString(), worldPosition.asLong());
    }
    private void enrollIfReady() {
        var key = locationKey();
        if (key == null || owner == null) return;
        if (KingdomStorageRuntime.ENROLLMENT.restore(key, owner)) registeredLocation = key;
    }
    private boolean canAutomate() {
        var key = locationKey();
        // A valid owner is necessary, but not sufficient: this also checks the active trade lock.
        return key != null && owner != null && registeredLocation != null
                && registeredLocation.equals(key)
                && KingdomStorageRuntime.ENROLLMENT.mayAutomate(key, owner);
    }

    public boolean canOpen(Player player) {
        return player != null && owner != null && owner.equals(player.getUUID()) && stillValid(player);
    }

    @Override public Component getDisplayName() {
        return Component.translatable("block.kingdomnpc.managed_storage");
    }

    @Override public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return canOpen(player) ? ChestMenu.threeRows(containerId, inventory, this) : null;
    }

    @Override public int getContainerSize() { return items.getSlots(); }

    @Override public boolean isEmpty() {
        for (int slot = 0; slot < items.getSlots(); slot++) {
            if (!items.getStackInSlot(slot).isEmpty()) return false;
        }
        return true;
    }

    @Override public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.getSlots() ? items.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    @Override public ItemStack removeItem(int slot, int amount) {
        if (!canAutomate() || slot < 0 || slot >= items.getSlots() || amount <= 0) return ItemStack.EMPTY;
        return items.extractItem(slot, amount, false);
    }

    @Override public ItemStack removeItemNoUpdate(int slot) {
        if (!canAutomate() || slot < 0 || slot >= items.getSlots()) return ItemStack.EMPTY;
        return items.extractItem(slot, Integer.MAX_VALUE, false);
    }

    @Override public void setItem(int slot, ItemStack stack) {
        if (!canAutomate() || slot < 0 || slot >= items.getSlots()) return;
        ItemStack stored = stack.copy();
        stored.setCount(Math.min(stored.getCount(), getMaxStackSize()));
        items.setStackInSlot(slot, stored);
    }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return canAutomate() && slot >= 0 && slot < items.getSlots() && items.isItemValid(slot, stack);
    }

    @Override public boolean stillValid(Player player) {
        return level != null && owner != null && owner.equals(player.getUUID()) && canAutomate()
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override public void clearContent() {
        if (!canAutomate()) return;
        for (int slot = 0; slot < items.getSlots(); slot++) items.setStackInSlot(slot, ItemStack.EMPTY);
    }
    @Override public void onLoad() {
        super.onLoad();
        enrollIfReady();
    }
    @Override public void setRemoved() {
        if (registeredLocation != null) KingdomStorageRuntime.ENROLLMENT.unload(registeredLocation, owner);
        registeredLocation = null;
        super.setRemoved();
    }

    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return capability.cast();
        return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); capability.invalidate(); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) tag.putUUID("Enterprise", owner);
        tag.put("Inventory", items.serializeNBT());
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("Enterprise") ? tag.getUUID("Enterprise") : null;
        if (tag.contains("Inventory", 10)) items.deserializeNBT(tag.getCompound("Inventory"));
        enrollIfReady();
    }
}
