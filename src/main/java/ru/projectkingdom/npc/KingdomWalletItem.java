package ru.projectkingdom.npc;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Portable physical wallet. NBT balance is stored on this ItemStack. */
public final class KingdomWalletItem extends Item {
    public KingdomWalletItem(Properties properties) {super(properties);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if(!level.isClientSide && player instanceof ServerPlayer serverPlayer)KingdomMainMenu.openWallet(serverPlayer);
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
}
