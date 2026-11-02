package ru.projectkingdom.assistant;

import net.minecraft.nbt.CompoundTag;
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
