package ru.projectkingdom.npc;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * The Minecraft side of a delivery: the four containers of the world.
 *
 * <p>The resolver builds owner bound ports: the right of the owner is checked
 * before the container is used, a chunk that is not loaded gives no container
 * and the second half of a double chest is only used when the owner may use
 * it too. A container that cannot be used stops the whole delivery, it is
 * never replaced by another one.
 *
 * <p>The state of the offered goods is named by the seller: a perishable item
 * of TerraFirmaCraft has a freshness, and the delivery must move exactly the
 * goods that were offered.
 */
public final class SettlementMinecraftEndpoints implements DemandSettlementDeliveryAdapter.Resolver {
    /** The role of a container in the delivery. */
    public enum Role { SUPPLIER, WAREHOUSE, CASHBOX, WALLET }

    /** A container of the world with the right of its owner. */
    public record Slot(ServerLevel level, BlockPos pos, UUID owner) {
        public Slot {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(pos, "pos");
            Objects.requireNonNull(owner, "owner");
        }
    }

    /** Where the four containers of a delivery are. */
    public interface SlotProvider {
        /**
         * The container of a role.
         *
         * @return null when the container of this role does not exist.
         */
        Slot slot(EnterpriseDemandBoard.Listing listing, Role role) throws IOException;

        /**
         * The state of the offered goods.
         *
         * @return the fingerprint of the offered stack, the empty string for
         *         goods without a tag.
         */
        default String variant(EnterpriseDemandBoard.Listing listing,
                               EnterpriseDemandBoard.Delivery delivery) throws IOException {
            return "";
        }
    }

    private final SlotProvider provider;

    public SettlementMinecraftEndpoints(SlotProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    @Override public DemandSettlementDeliveryAdapter.Endpoints resolve(
            EnterpriseDemandBoard.Listing listing, EnterpriseDemandBoard.Delivery delivery) throws IOException {
        SettlementAuthority.Container supplier = port(listing, Role.SUPPLIER);
        SettlementAuthority.Container warehouse = port(listing, Role.WAREHOUSE);
        SettlementAuthority.Container cashbox = port(listing, Role.CASHBOX);
        SettlementAuthority.Container wallet = port(listing, Role.WALLET);
        if (supplier == null || warehouse == null || cashbox == null || wallet == null) return null;
        String variant = provider.variant(listing, delivery);
        return new DemandSettlementDeliveryAdapter.Endpoints(supplier, warehouse, cashbox, wallet,
                variant == null ? "" : variant);
    }

    /**
     * The owner bound port of a container.
     *
     * @return null when the owner may not use the container, when the chunk is
     *         not loaded or when the block is no container.
     */
    private SettlementAuthority.Container port(EnterpriseDemandBoard.Listing listing, Role role) throws IOException {
        Slot slot = provider.slot(listing, role);
        if (slot == null) return null;
        return SettlementMinecraftPorts.port(slot.level(), slot.pos(), slot.owner());
    }

    /**
     * The fingerprint of a stack of the world.
     *
     * <p>The seller offers one stack of goods. The fingerprint of this stack is
     * the state of the goods, and the delivery moves exactly this state.
     */
    public static String variantOf(net.minecraft.world.item.ItemStack stack) {
        return SettlementMinecraftPorts.fingerprint(stack);
    }

    /** Whether the block is a chest of one half. */
    public static boolean isSingleChest(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                && state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) == ChestType.SINGLE;
    }
}
