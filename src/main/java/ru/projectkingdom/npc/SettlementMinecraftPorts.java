package ru.projectkingdom.npc;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Server-thread-only adapter between a Minecraft container and the settlement
 * authority.
 *
 * <p>The adapter only moves items. It decides nothing: the authority owns the
 * decision, the durable log and the recovery.
 *
 * <p>An owner bound port is fail-closed. The container is resolved through
 * {@link KingdomContainerAccess}, so a modded container and both halves of a
 * double chest are handled, and the right of the owner is checked again before
 * every write: a right that was revoked in the middle of a delivery stops the
 * delivery instead of moving items out of a foreign chest.
 *
 * <p>The identifier of an owner bound port carries the dimension, the position
 * and the owner, so a server that was restarted can rebuild the very same
 * container from the durable log.
 */
public final class SettlementMinecraftPorts {
    private SettlementMinecraftPorts() {}

    private static final String PREFIX = "mcd";

    /** One physical container of the world. */
    public static final class Port implements SettlementAuthority.Container {
        private final String id;
        private final Container container;
        private final Thread ownerThread;
        private final OwnerBound bound;

        /** The owner bound information of a port, null for a plain port. */
        public record OwnerBound(ServerLevel level, net.minecraft.core.BlockPos pos, UUID owner) {}

        /** A port without an owner check. Only for trusted internal use. */
        public Port(String id, Container container) {
            this(id, container, null);
        }

        private Port(String id, Container container, OwnerBound bound) {
            this.id = Objects.requireNonNull(id, "id");
            this.container = Objects.requireNonNull(container, "container");
            this.ownerThread = Thread.currentThread();
            this.bound = bound;
        }

        public String id() { return id; }

        public long count(String item) {
            return countOf(container, prototype(item));
        }

        public long capacity(String item) {
            return spaceOf(container, prototype(item));
        }

        /** Moves the amount in or out of the container. */
        public void apply(String item, long delta) throws IOException {
            checkThread();
            if (delta == 0) return;
            if (bound != null && !KingdomTerritories.get(bound.level().getServer())
                    .canUse(bound.level(), bound.pos(), bound.owner()))
                throw new IOException("the right of the owner was revoked before the write");
            ItemStack sample = prototype(item);
            if (delta < 0) remove(sample, -delta);
            else insert(sample, delta);
        }

        private void checkThread() throws IOException {
            if (Thread.currentThread() != ownerThread)
                throw new IOException("settlement outside the owner server thread");
        }

        private void remove(ItemStack sample, long amount) throws IOException {
            if (countOf(container, sample) < amount) throw new IOException("insufficient physical items");
            for (int slot = 0; slot < container.getContainerSize() && amount > 0; slot++) {
                ItemStack current = container.getItem(slot);
                if (!matches(current, sample)) continue;
                int take = (int) Math.min(amount, current.getCount());
                ItemStack next = current.copy();
                next.shrink(take);
                container.setItem(slot, next);
                amount -= take;
            }
            if (amount != 0) throw new IOException("incomplete removal");
            container.setChanged();
        }

        private void insert(ItemStack sample, long amount) throws IOException {
            if (spaceOf(container, sample) < amount) throw new IOException("insufficient physical space");
            for (int slot = 0; slot < container.getContainerSize() && amount > 0; slot++) {
                ItemStack current = container.getItem(slot);
                if ((!current.isEmpty() && !matches(current, sample)) || !container.canPlaceItem(slot, sample)) continue;
                int limit = Math.min(container.getMaxStackSize(), sample.getMaxStackSize());
                int free = limit - (current.isEmpty() ? 0 : current.getCount());
                if (free <= 0) continue;
                int add = (int) Math.min(amount, free);
                ItemStack next = current.isEmpty() ? sample.copy() : current.copy();
                next.setCount((current.isEmpty() ? 0 : current.getCount()) + add);
                container.setItem(slot, next);
                amount -= add;
            }
            if (amount != 0) throw new IOException("incomplete insertion");
            container.setChanged();
        }
    }

    /** The identifier of an owner bound container: dimension, position, owner. */
    public static String id(ServerLevel level, net.minecraft.core.BlockPos pos, UUID owner) {
        return PREFIX + ":" + level.dimension().location().toString()
                + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ()
                + ":" + owner;
    }

    /**
     * Builds an owner bound port.
     *
     * @return the port, or null when the container cannot be used by the owner.
     */
    public static Port port(ServerLevel level, net.minecraft.core.BlockPos pos, UUID owner) {
        if (level == null || pos == null || owner == null) return null;
        Container container = resolveOwned(level, pos, owner);
        if (container == null) return null;
        return new Port(id(level, pos, owner), container,
                new Port.OwnerBound(level, pos, owner));
    }

    /**
     * Resolves a container that the owner may really use.
     *
     * <p>The right of the owner is checked on the container itself. A double
     * chest is one physical inventory, so the second half is only used when
     * the owner may use it too: otherwise the worker would read or write a
     * foreign chest through the shared inventory.
     */
    public static Container resolveOwned(ServerLevel level, net.minecraft.core.BlockPos pos, UUID owner) {
        if (level == null || pos == null || owner == null) return null;
        if (!level.hasChunkAt(pos)) return null;
        if (!KingdomTerritories.get(level.getServer()).canUse(level, pos, owner)) return null;
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof Container container) {
            var block = level.getBlockState(pos);
            if (block.getBlock() instanceof ChestBlock
                    && block.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    net.minecraft.core.BlockPos other = pos.relative(side);
                    if (!KingdomContainerAccess.samePhysicalStorage(level, pos, other)) continue;
                    if (!KingdomTerritories.get(level.getServer()).canUse(level, other, owner)) continue;
                    BlockEntity opposite = level.getBlockEntity(other);
                    if (opposite instanceof Container otherContainer)
                        return new CompoundContainer(container, otherContainer);
                }
            }
            return container;
        }
        // A modded container, for example a TerraFirmaCraft chest: one block, no halves.
        return KingdomContainerAccess.resolve(level, pos);
    }

    /**
     * Rebuilds an owner bound port from its identifier.
     *
     * <p>This is what makes the recovery after a restart possible: the durable
     * log stores the identifier of the container, not a reference to a live
     * object.
     *
     * @return the port, or null when the world cannot provide it.
     */
    public static Port resolve(MinecraftServer server, String id) {
        if (server == null || id == null || !id.startsWith(PREFIX + ":")) return null;
        String[] parts = id.split(":");
        if (parts.length != 7) return null;
        String dimension = parts[1] + ":" + parts[2];
        int x;
        int y;
        int z;
        UUID owner;
        try {
            x = Integer.parseInt(parts[3]);
            y = Integer.parseInt(parts[4]);
            z = Integer.parseInt(parts[5]);
            owner = UUID.fromString(parts[6]);
        } catch (RuntimeException ex) {
            return null;
        }
        ServerLevel level;
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                    new net.minecraft.resources.ResourceLocation(dimension));
            level = server.getLevel(key);
        } catch (RuntimeException ex) {
            return null;
        }
        if (level == null) return null;
        return port(level, new net.minecraft.core.BlockPos(x, y, z), owner);
    }

    private static ItemStack prototype(String item) {
        var key = new net.minecraft.resources.ResourceLocation(item);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key))
            throw new IllegalArgumentException("unknown item: " + item);
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(key));
        if (stack.isEmpty()) throw new IllegalArgumentException("empty item: " + item);
        return stack;
    }

    private static boolean matches(ItemStack stack, ItemStack sample) {
        return !stack.isEmpty() && ItemStack.isSameItemSameTags(stack, sample);
    }

    private static long countOf(Container container, ItemStack sample) {
        long total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++)
            if (matches(container.getItem(slot), sample)) total += container.getItem(slot).getCount();
        return total;
    }

    private static long spaceOf(Container container, ItemStack sample) {
        long total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack current = container.getItem(slot);
            if (current.isEmpty() && container.canPlaceItem(slot, sample))
                total += Math.min(container.getMaxStackSize(), sample.getMaxStackSize());
            else if (matches(current, sample) && container.canPlaceItem(slot, sample))
                total += Math.max(0, Math.min(container.getMaxStackSize(), current.getMaxStackSize()) - current.getCount());
        }
        return total;
    }
}
