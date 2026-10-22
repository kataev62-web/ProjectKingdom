package ru.projectkingdom.core;

import net.minecraft.server.level.ServerPlayer;
import ru.projectkingdom.npc.KingdomBankData;
import ru.projectkingdom.npc.KingdomCurrency;

/** Read-only bridge from the existing Stage 1 economy into the new Book. */
public final class Stage1FinanceAdapter {
    private Stage1FinanceAdapter() {}

    public static BookModel.Finance snapshot(ServerPlayer player) {
        long bank = Math.max(0, KingdomBankData.get(player.getServer()).balance(player.getUUID()));
        long cash = Math.max(0, KingdomCurrency.balance(player));
        long total;
        try { total = Math.addExact(bank, cash); }
        catch (ArithmeticException overflow) { total = Long.MAX_VALUE; }
        return new BookModel.Finance(bank, total);
    }
}
