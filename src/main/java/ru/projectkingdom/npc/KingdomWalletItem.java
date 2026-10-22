package ru.projectkingdom.npc;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ru.projectkingdom.economy.MoneyPurseInventory;
import ru.projectkingdom.economy.MoneyPurseMenu;
import ru.projectkingdom.economy.MoneyPurseNbt;

/** Portable physical wallet. NBT balance is stored on this ItemStack. */
public final class KingdomWalletItem extends Item {
    public KingdomWalletItem(Properties properties) {super(properties);}
    @Override public int getMaxStackSize(ItemStack stack) {
        return MoneyPurseInventory.maxStackSize(MoneyPurseNbt.read(stack));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if(!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            int slot=KingdomCurrency.separateForUse(serverPlayer,stack);
            if(slot<0)serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.kingdomnpc.purse_no_slot"));
            else MoneyPurseMenu.open(serverPlayer,slot);
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
}
