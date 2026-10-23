package ru.projectkingdom.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/** Recovery policy for the unique, non-transferable Book. */
public final class KingdomBookGuardian {
    private static final String OWNER = "ProjectKingdomOwner";

    public static void bind(ItemStack stack, UUID owner) { stack.getOrCreateTag().putUUID(OWNER, owner); }
    public static boolean isBook(ItemStack stack) { return stack.is(KingdomBookRegistry.PLAYER_BOOK.get()); }
    public static boolean belongsTo(ItemStack stack, UUID owner) {
        CompoundTag tag = stack.getTag();
        return isBook(stack) && tag != null && tag.hasUUID(OWNER) && tag.getUUID(OWNER).equals(owner);
    }

    public static ItemStack ensure(ServerPlayer player) {
        ItemStack kept = ItemStack.EMPTY;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!isBook(stack)) continue;
            if (kept.isEmpty()) {
                bind(stack, player.getUUID());
                stack.setCount(1);
                kept = stack;
            } else {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
        if (kept.isEmpty()) {
            kept = new ItemStack(KingdomBookRegistry.PLAYER_BOOK.get());
            bind(kept, player.getUUID());
            if (!player.getInventory().add(kept)) player.getInventory().setItem(0, kept);
            player.sendSystemMessage(Component.translatable("message.kingdomnpc.book_restored"));
        }
        player.getInventory().setChanged();
        return kept;
    }

    private static void restoreBookAndRecipes(ServerPlayer player) {
        ensure(player);
        // Award live server recipes even when the advancement did not re-fire.
        player.awardRecipes(KingdomRecipeUnlock.all(player.getServer().getRecipeManager()));
    }

    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) restoreBookAndRecipes(player);
    }

    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) restoreBookAndRecipes(player);
    }

    @SubscribeEvent public void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) restoreBookAndRecipes(player);
    }

    @SubscribeEvent public void toss(ItemTossEvent event) {
        ItemEntity entity = event.getEntity();
        if (!isBook(entity.getItem()) || !(event.getPlayer() instanceof ServerPlayer player)) return;
        event.setCanceled(true);
        ensure(player);
        player.sendSystemMessage(Component.translatable("message.kingdomnpc.book_bound"));
    }
}
