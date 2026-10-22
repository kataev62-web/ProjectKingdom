package ru.projectkingdom.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import ru.projectkingdom.npc.KingdomBankData;
import ru.projectkingdom.npc.KingdomCurrency;

/** Read-only bridge from the existing Stage 1 economy into the new Book. */
public final class Stage1FinanceAdapter {
    private Stage1FinanceAdapter() {}

    public static BookModel.Finance snapshot(ServerPlayer player) {
        long bank = Math.max(0, KingdomBankData.get(player.getServer()).balance(player.getUUID()));
        ItemStack wallet = KingdomCurrency.wallet(player);
        long cash = wallet.isEmpty() ? 0 : Math.max(0, KingdomCurrency.balance(wallet));
        long total;
        try { total = Math.addExact(bank, cash); }
        catch (ArithmeticException overflow) { total = Long.MAX_VALUE; }
        return new BookModel.Finance(bank, total);
    }
}
