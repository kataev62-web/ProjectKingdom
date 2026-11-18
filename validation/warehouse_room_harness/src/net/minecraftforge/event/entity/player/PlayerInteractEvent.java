package net.minecraftforge.event.entity.player;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

public class PlayerInteractEvent {
    public static class RightClickBlock extends PlayerInteractEvent {
        private final BlockHitResult hitResult;
        private final int sequence;
        private final InteractionHand hand;
        private Player entity;
        private boolean canceled = false;
        private InteractionResult cancellationResult = InteractionResult.PASS;

        public RightClickBlock(Player player, InteractionHand hand, BlockHitResult hitResult, int sequence) {
            this.entity = player;
            this.hand = hand;
            this.hitResult = hitResult;
            this.sequence = sequence;
        }

        public InteractionHand getHand() { return hand; }
        public BlockHitResult getHitResult() { return hitResult; }
        public BlockPos getPos() { return hitResult.getBlockPos(); }
        public Player getEntity() { return entity; }
        public void setEntity(Player entity) { this.entity = entity; }
        public ItemStack getItemStack() { return entity.getItemInHand(hand); }
        public boolean isCanceled() { return canceled; }
        public void setCanceled(boolean canceled) { this.canceled = canceled; }
        public InteractionResult getCancellationResult() { return cancellationResult; }
        public void setCancellationResult(InteractionResult result) { this.cancellationResult = result; }
    }

    public static class RightClickItem extends PlayerInteractEvent {
        private final InteractionHand hand;
        private Player entity;

        public RightClickItem(Player player, InteractionHand hand) {
            this.entity = player;
            this.hand = hand;
        }

        public InteractionHand getHand() { return hand; }
        public Player getEntity() { return entity; }
    }

    public static class LeftClickBlock extends PlayerInteractEvent {
        private final BlockHitResult hitResult;
        private final Player entity;

        public LeftClickBlock(Player player, BlockHitResult hitResult) {
            this.entity = player;
            this.hitResult = hitResult;
        }

        public BlockHitResult getHitResult() { return hitResult; }
        public BlockPos getPos() { return hitResult.getBlockPos(); }
        public Player getEntity() { return entity; }
    }

    public static class EntityInteract extends PlayerInteractEvent {
        private final Player entity;
        private final net.minecraft.world.entity.Entity target;

        public EntityInteract(Player player, net.minecraft.world.entity.Entity target) {
            this.entity = player;
            this.target = target;
        }

        public Player getEntity() { return entity; }
        public net.minecraft.world.entity.Entity getTarget() { return target; }
    }

    protected PlayerInteractEvent() {}
}