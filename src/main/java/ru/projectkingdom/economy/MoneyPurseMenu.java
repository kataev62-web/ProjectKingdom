package ru.projectkingdom.economy;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
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
    private final DataSlot copperLowData = DataSlot.standalone();
    private final DataSlot copperHighData = DataSlot.standalone();
    private final DataSlot silverLowData = DataSlot.standalone();
    private final DataSlot silverHighData = DataSlot.standalone();
    private final DataSlot goldLowData = DataSlot.standalone();
    private final DataSlot goldHighData = DataSlot.standalone();
    private final DataSlot overflowData = DataSlot.standalone();

    public MoneyPurseMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readInt());
    }

    public MoneyPurseMenu(int containerId, Inventory inventory, int purseSlot) {
        super(KingdomEconomyRegistry.PURSE_MENU.get(), containerId);
        this.inventory = inventory;
        this.purseSlot = purseSlot;
        addDataSlot(copperLowData);
        addDataSlot(copperHighData);
        addDataSlot(silverLowData);
        addDataSlot(silverHighData);
        addDataSlot(goldLowData);
        addDataSlot(goldHighData);
        addDataSlot(overflowData);
        if (!inventory.player.level().isClientSide) syncPurseData();
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
        if (!stillValid(inventory.player)) return MoneyPurse.empty();
        if (!inventory.player.level().isClientSide) return MoneyPurseNbt.read(inventory.getItem(purseSlot));
        return new MoneyPurse(joinLong(copperLowData, copperHighData),
                joinLong(silverLowData, silverHighData),
                joinLong(goldLowData, goldHighData), overflowData.get() != 0);
    }

    @Override public boolean clickMenuButton(Player player, int action) {
        if (!(player instanceof ServerPlayer server) || !stillValid(player)) return false;
        boolean handled = switch (action) {
            case COLLECT -> { KingdomCurrency.collect(server, purseSlot); yield true; }
            case WITHDRAW_COPPER -> KingdomCurrency.withdraw(server, purseSlot, MoneyDenomination.COPPER);
            case WITHDRAW_SILVER -> KingdomCurrency.withdraw(server, purseSlot, MoneyDenomination.SILVER);
            case WITHDRAW_GOLD -> KingdomCurrency.withdraw(server, purseSlot, MoneyDenomination.GOLD);
            default -> false;
        };
        if (handled) {
            broadcastChanges();
            server.inventoryMenu.broadcastChanges();
        }
        return handled;
    }

    @Override public void broadcastChanges() {
        if (!inventory.player.level().isClientSide) syncPurseData();
        super.broadcastChanges();
    }

    private void syncPurseData() {
        MoneyPurse purse = stillValid(inventory.player)
                ? MoneyPurseNbt.read(inventory.getItem(purseSlot)) : MoneyPurse.empty();
        setLong(copperLowData, copperHighData, purse.copper());
        setLong(silverLowData, silverHighData, purse.silver());
        setLong(goldLowData, goldHighData, purse.gold());
        overflowData.set(purse.legacyOverflow() ? 1 : 0);
    }

    private static void setLong(DataSlot low, DataSlot high, long value) {
        low.set((int) value);
        high.set((int) (value >>> 32));
    }

    private static long joinLong(DataSlot low, DataSlot high) {
        return ((long) high.get() << 32) | (low.get() & 0xFFFF_FFFFL);
    }

    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) {
        return purseSlot >= 0 && purseSlot < inventory.getContainerSize()
                && inventory.getItem(purseSlot).is(KingdomCurrency.WALLET.get());
    }
}
