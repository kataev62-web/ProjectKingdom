package ru.projectkingdom.core;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class KingdomBookMenu extends AbstractContainerMenu {
    private final long bankBalance;
    private final long totalCapital;
    private final ru.projectkingdom.social.KingdomSocialBookSnapshot social;

    public KingdomBookMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, Math.max(0, buffer.readLong()), Math.max(0, buffer.readLong()),
                ru.projectkingdom.social.KingdomSocialBookSnapshot.read(buffer));
    }

    public KingdomBookMenu(int containerId, Inventory inventory, long bankBalance, long totalCapital) {
        this(containerId, inventory, bankBalance, totalCapital,
                ru.projectkingdom.social.KingdomSocialBookSnapshot.empty());
    }
    public KingdomBookMenu(int containerId, Inventory inventory, long bankBalance, long totalCapital,
                           ru.projectkingdom.social.KingdomSocialBookSnapshot social) {
        super(KingdomBookRegistry.PLAYER_BOOK_MENU.get(), containerId);
        this.bankBalance = Math.max(0, bankBalance);
        this.totalCapital = Math.max(this.bankBalance, totalCapital);
        this.social = social == null ? ru.projectkingdom.social.KingdomSocialBookSnapshot.empty() : social;
    }

    public long bankBalance() { return bankBalance; }
    public long totalCapital() { return totalCapital; }
    public ru.projectkingdom.social.KingdomSocialBookSnapshot social() { return social; }

    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return true; }
}
