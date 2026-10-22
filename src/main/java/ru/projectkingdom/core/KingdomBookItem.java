package ru.projectkingdom.core;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

public final class KingdomBookItem extends Item {
    public KingdomBookItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            KingdomBookGuardian.bind(stack, serverPlayer.getUUID());
            BookModel.Finance finance = Stage1FinanceAdapter.snapshot(serverPlayer);
            MenuProvider provider = new SimpleMenuProvider(
                    (containerId, inventory, ignored) -> new KingdomBookMenu(containerId, inventory,
                            finance.bankBalance(), finance.totalCapital()),
                    Component.translatable("screen.kingdomnpc.player_book"));
            NetworkHooks.openScreen(serverPlayer, provider, buffer -> {
                buffer.writeLong(finance.bankBalance());
                buffer.writeLong(finance.totalCapital());
            });
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override public boolean isFoil(ItemStack stack) { return true; }
}
