package ru.projectkingdom.economy;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;
import ru.projectkingdom.npc.KingdomCurrency;

/** Server-authoritative menu bound to one inventory slot, never item equality. */
public final class MoneyPurseMenu extends AbstractContainerMenu {
    public static final int COLLECT = 0;
    public static final int WITHDRAW_COPPER = 1;
    public static final int WITHDRAW_SILVER = 2;
    public static final int WITHDRAW_GOLD = 3;

    private final Inventory inventory;
    private final int purseSlot;

    public MoneyPurseMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readInt());
    }

    public MoneyPurseMenu(int containerId, Inventory inventory, int purseSlot) {
        super(KingdomEconomyRegistry.PURSE_MENU.get(), containerId);
        this.inventory = inventory;
        this.purseSlot = purseSlot;
    }

    public static void open(ServerPlayer player, int slot) {
        NetworkHooks.openScreen(player, new MenuProvider() {
            @Override public Component getDisplayName() { return Component.translatable("screen.kingdomnpc.money_purse"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player ignored) {
                return new MoneyPurseMenu(id, inventory, slot);
            }
        }, buffer -> buffer.writeInt(slot));
    }

    public int purseSlot() { return purseSlot; }
    public MoneyPurse purse() {
        return stillValid(inventory.player) ? MoneyPurseNbt.read(inventory.getItem(purseSlot)) : MoneyPurse.empty();
    }

    @Override public boolean clickMenuButton(Player player, int action) {
        if (!(player instanceof ServerPlayer server) || !stillValid(player)) return false;
        return switch (action) {
            case COLLECT -> { KingdomCurrency.collect(server, purseSlot); yield true; }
            case WITHDRAW_COPPER -> KingdomCurrency.withdraw(server, purseSlot, MoneyDenomination.COPPER);
            case WITHDRAW_SILVER -> KingdomCurrency.withdraw(server, purseSlot, MoneyDenomination.SILVER);
            case WITHDRAW_GOLD -> KingdomCurrency.withdraw(server, purseSlot, MoneyDenomination.GOLD);
            default -> false;
        };
    }

    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) {
        return purseSlot >= 0 && purseSlot < inventory.getContainerSize()
                && inventory.getItem(purseSlot).is(KingdomCurrency.WALLET.get());
    }
}
