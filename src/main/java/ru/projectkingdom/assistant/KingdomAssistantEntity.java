package ru.projectkingdom.assistant;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/** Visible, persistent assistant character. It is deliberately not a Kingdom worker NPC. */
public final class KingdomAssistantEntity extends Villager {
    private KingdomAssistantOwnership ownership = new KingdomAssistantOwnership();

    public KingdomAssistantEntity(EntityType<? extends Villager> type, Level level) {
        super(type, level);
        setCustomName(Component.translatable("entity.kingdomnpc.kingdom_assistant"));
        setCustomNameVisible(true);
        setPersistenceRequired();
    }

    public Optional<UUID> ownerId() {
        return ownership.ownerId();
    }

    public boolean setOwnerId(UUID ownerId) {
        return ownership.assign(ownerId);
    }

    public boolean isOwnedBy(ServerPlayer player) {
        return player != null
                && player.hasPermissions(4)
                && ownership.ownerId().filter(player.getUUID()::equals).isPresent();
    }

    public void setAssistantMode(KingdomAssistantData.Mode mode) {
        ownership.setMode(mode);
    }

    public KingdomAssistantData.Mode assistantMode() {
        return ownership.mode();
    }

    /** Keeps the assistant near its owner without loading any destination chunk. */
    public void applyMovement(ServerPlayer owner) {
        if (owner == null || ownership.mode() == KingdomAssistantData.Mode.STAY) {
            getNavigation().stop();
            return;
        }
        if (owner.level() != level()) {
            getNavigation().stop();
            return;
        }
        double distanceSquared = distanceToSqr(owner);
        if (distanceSquared <= 144.0D) {
            getNavigation().stop();
            return;
        }
        if (distanceSquared <= 2304.0D) {
            getNavigation().moveTo(owner, 1.05D);
            return;
        }
        getNavigation().stop();
        BlockPos destination = safeLoadedDestination(owner);
        if (destination != null) {
            teleportTo(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D);
        }
    }

    private BlockPos safeLoadedDestination(ServerPlayer owner) {
        BlockPos origin = owner.blockPosition();
        int[][] offsets = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (int[] offset : offsets) {
            BlockPos candidate = origin.offset(offset[0], 0, offset[1]);
            if (!owner.serverLevel().hasChunkAt(candidate)) continue;
            boolean feetClear = owner.serverLevel().getBlockState(candidate)
                    .getCollisionShape(owner.serverLevel(), candidate).isEmpty();
            BlockPos head = candidate.above();
            boolean headClear = owner.serverLevel().getBlockState(head)
                    .getCollisionShape(owner.serverLevel(), head).isEmpty();
            BlockPos floor = candidate.below();
            boolean floorSolid = owner.serverLevel().getBlockState(floor)
                    .isFaceSturdy(owner.serverLevel(), floor, Direction.UP);
            if (feetClear && headClear && floorSolid) return candidate;
        }
        return null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ownership.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownership = KingdomAssistantOwnership.load(tag);
        setCustomName(Component.translatable("entity.kingdomnpc.kingdom_assistant"));
        setCustomNameVisible(true);
        setPersistenceRequired();
    }

    /** The assistant has no vanilla villager trading screen. */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
