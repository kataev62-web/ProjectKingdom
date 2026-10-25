package ru.projectkingdom.npc;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.BooleanSupplier;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;

/** Minimal vanilla GUI. Only slot 11 accepts a player offer; slot 15 confirms.
 * The cashbox and warehouse are existing physical containers, never virtual balances.
 * Call open() only after validating access to the enterprise and its containers.
 * This menu does NOT provide crash-atomic multi-container settlement.
 */
public final class KingdomSimpleTradeMenu extends ChestMenu {
    private final ServerPlayer viewer;
    private final SimpleContainer slots;
    private final Container warehouse, cashbox;
    private final Item wanted, coin;
    private final int unitPrice;
    private final IntConsumer onSold;
    private final BooleanSupplier canTrade;
    private final java.util.function.IntSupplier remaining;
    private boolean processing;
    private boolean settlementUncertain;
    // Shared across all open menus in this JVM: a warehouse/cashbox cannot settle twice concurrently.
    private static final Set<Container> SETTLING = Collections.newSetFromMap(new IdentityHashMap<>());
    private static synchronized boolean reserve(Container a, Container b) {
        if (SETTLING.contains(a) || SETTLING.contains(b)) return false;
        SETTLING.add(a); SETTLING.add(b); return true;
    }
    private static synchronized void release(Container a, Container b) {
        SETTLING.remove(a); SETTLING.remove(b);
    }

    private KingdomSimpleTradeMenu(int id, Inventory inventory, ServerPlayer viewer,
            Container warehouse, Container cashbox, Item wanted, Item coin, int unitPrice,
            IntConsumer onSold, BooleanSupplier canTrade, java.util.function.IntSupplier remaining, SimpleContainer slots) {
        super(MenuType.GENERIC_9x3, id, inventory, slots, 3);
        this.viewer = viewer; this.slots = slots;
        this.warehouse = warehouse; this.cashbox = cashbox;
        this.wanted = wanted; this.coin = coin; this.unitPrice = unitPrice;
        this.onSold = onSold; this.canTrade = canTrade; this.remaining = remaining;
        draw();
    }

    public static void open(ServerPlayer player, Container warehouse, Container cashbox,
            Item wanted, Item coin, int unitPrice, IntConsumer onSold, BooleanSupplier canTrade) {
        if (warehouse == cashbox || unitPrice <= 0 || warehouse == null || cashbox == null
                || wanted == null || coin == null || wanted == coin || onSold == null || canTrade == null)
            throw new IllegalArgumentException("invalid physical trade configuration");
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new KingdomSimpleTradeMenu(
                id, inv, player, warehouse, cashbox, wanted, coin, unitPrice,
                onSold, canTrade, () -> Integer.MAX_VALUE, new SimpleContainer(27)), Component.literal("Продажа товаров")));
    }

    /** Open a local BUY notice. The board is informational: only the shop menu trades.
     * All parameters are resolved server-side; never trust an item or price from the client.
     */
    public static boolean openForNotice(ServerPlayer player, DurableSimpleTradeNoticeBoard board,
            UUID noticeId, UUID enterpriseId, Container warehouse, Container cashbox,
            Item copperCoin, BooleanSupplier canTrade) {
        if (player == null || board == null || noticeId == null || enterpriseId == null
                || warehouse == null || cashbox == null || copperCoin == null || canTrade == null) return false;
        var notice = board.notice(noticeId);
        if (notice == null || notice.side() != SimpleTradeNoticeBoard.Side.BUY
                || !notice.owner().equals(enterpriseId) || notice.priceCopper() < 1
                || notice.priceCopper() > Integer.MAX_VALUE || notice.quantity() < 1) return false;
        var key = net.minecraft.resources.ResourceLocation.tryParse(notice.itemId());
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return false;
        Item wantedItem = BuiltInRegistries.ITEM.get(key);
        if (wantedItem == Items.AIR || wantedItem == copperCoin) return false;
        java.util.function.IntSupplier remaining = () -> {
            var current = board.notice(noticeId);
            if (current == null || current.side() != SimpleTradeNoticeBoard.Side.BUY
                    || !current.owner().equals(enterpriseId)
                    || !current.itemId().equals(notice.itemId())
                    || current.priceCopper() != notice.priceCopper()) return 0;
            return current.quantity();
        };
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new KingdomSimpleTradeMenu(
                id, inv, player, warehouse, cashbox, wantedItem, copperCoin,
                (int) notice.priceCopper(), quantity -> {
                    long paid=(long)quantity*notice.priceCopper();
                    if(!KingdomEnterpriseData.get(player.getServer()).recordPhysicalNoticePayment(
                            player.serverLevel(),enterpriseId,cashbox,paid))
                        throw new IllegalStateException("Physical till and enterprise ledger disagree");
                    try {
                        if (!board.confirmedTrade(noticeId, notice.itemId(), quantity))
                            throw new IllegalStateException("Notice no longer valid");
                    } catch (java.io.IOException e) { throw new IllegalStateException("Cannot save notice", e); }
                }, canTrade, remaining, new SimpleContainer(27)), Component.literal("Продажа по заявке")));
        return true;
    }

    private void draw() {
        ItemStack info = new ItemStack(wanted);
        info.setHoverName(Component.literal("Товар: " + wanted.getDescription().getString()
                + " | " + unitPrice + " мед./шт. | положите в слот 12"));
        slots.setItem(4, info);
        ItemStack button = new ItemStack(Items.EMERALD_BLOCK);
        button.setHoverName(Component.literal("ПРОДАТЬ — нажмите сюда"));
        slots.setItem(15, button);
    }

    private static int count(Container container, Item item) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++)
            if (container.getItem(i).is(item) && !container.getItem(i).hasTag()) total += container.getItem(i).getCount();
        return total;
    }

    private static int capacity(Container container, Item item) {
        int total = 0;
        ItemStack prototype = new ItemStack(item);
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (!container.canPlaceItem(i, prototype)) continue;
            int max = Math.min(container.getMaxStackSize(), prototype.getMaxStackSize());
            if (s.isEmpty()) total += max;
            else if (ItemStack.isSameItemSameTags(s, prototype)) total += Math.max(0, max - s.getCount());
        }
        return total;
    }

    private static void moveOut(Container from, Item item, int amount) {
        for (int i = 0; i < from.getContainerSize() && amount > 0; i++) {
            ItemStack s = from.getItem(i);
            if (!s.is(item) || s.hasTag()) continue;
            int take = Math.min(amount, s.getCount());
            ItemStack replacement = s.copy(); replacement.shrink(take);
            from.setItem(i, replacement); amount -= take;
        }
        if (amount != 0) throw new IllegalStateException("source changed during trade");
        from.setChanged();
    }

    private static void moveIn(Container to, Item item, int amount) {
        ItemStack sample = new ItemStack(item);
        for (int i = 0; i < to.getContainerSize() && amount > 0; i++) {
            ItemStack s = to.getItem(i);
            if (!to.canPlaceItem(i, sample) || (!s.isEmpty() && !ItemStack.isSameItemSameTags(s, sample))) continue;
            int max = Math.min(to.getMaxStackSize(), sample.getMaxStackSize());
            int put = Math.min(amount, max - s.getCount());
            if (put <= 0) continue;
            ItemStack next = s.isEmpty() ? sample.copy() : s.copy();
            next.setCount(s.getCount() + put); to.setItem(i, next); amount -= put;
        }
        if (amount != 0) throw new IllegalStateException("destination changed during trade");
        to.setChanged();
    }

    private static ItemStack[] snapshot(Container container) {
        ItemStack[] copy = new ItemStack[container.getContainerSize()];
        for (int i = 0; i < copy.length; i++) copy[i] = container.getItem(i).copy();
        return copy;
    }

    private static void restore(Container container, ItemStack[] copy) {
        for (int i = 0; i < copy.length; i++) container.setItem(i, copy[i].copy());
        container.setChanged();
    }

    private void sell() {
        if (settlementUncertain) { message("Сделка требует проверки администрацией."); return; }
        if (!canTrade.getAsBoolean()) { message("Подойдите к предприятию."); return; }
        if (!reserve(warehouse, cashbox)) { message("Другая сделка выполняется, повторите попытку."); return; }
        processing = true;
        try {
            ItemStack offered = slots.getItem(11);
            if (!offered.is(wanted) || offered.hasTag()) {message("Положите нужный товар без дополнительных данных."); return;}
            int quantity = offered.getCount();
            if (quantity > remaining.getAsInt()) { message("Заявка уже выполнена или изменена."); return; }
            long total = (long) quantity * unitPrice;
            if (quantity <= 0 || total > Integer.MAX_VALUE) {message("Неверное количество."); return;}
            int price = (int) total;
            if (capacity(warehouse, wanted) < quantity) {message("Склад заполнен."); return;}
            if (count(cashbox, coin) < price) {message("В кассе недостаточно монет."); return;}
            if (capacity(viewer.getInventory(), coin) < price) {message("Освободите место для монет."); return;}
            // The player must still be at the trading counter; never settle a remote, stale menu.
            if (!canTrade.getAsBoolean() || !viewer.isAlive() || viewer.isRemoved()) {message("Торговля недоступна."); return;}

            // A failure inside the same tick can be rolled back in memory. This is NOT
            // crash-atomic: a JVM/power failure still needs a persistent transaction journal.
            ItemStack[] oldCash = snapshot(cashbox);
            ItemStack[] oldStock = snapshot(warehouse);
            ItemStack[] oldPlayer = snapshot(viewer.getInventory());
            ItemStack oldOffer = offered.copy();
            try {
                moveOut(cashbox, coin, price);
                moveIn(viewer.getInventory(), coin, price);
                moveIn(warehouse, wanted, quantity);
                slots.setItem(11, ItemStack.EMPTY);
            } catch (RuntimeException failure) {
                restore(cashbox, oldCash);
                restore(warehouse, oldStock);
                restore(viewer.getInventory(), oldPlayer);
                slots.setItem(11, oldOffer);
                message("Сделка отменена: ошибка перемещения предметов.");
                return;
            }
            try { onSold.accept(quantity); }
            catch (RuntimeException failure) {
                // Never silently retry: a callback may have partially updated a demand listing.
                settlementUncertain = true;
                message("Товар обменян, но учёт заявки не обновлён. Сообщите администрации.");
                return;
            }
            message("Продано " + quantity + " шт. за " + price + " монет.");
        } finally {
            processing = false;
            release(warehouse, cashbox);
        }
    }

    private void message(String text) { viewer.sendSystemMessage(Component.literal("[Торговля] " + text)); }

    @Override public void clicked(int slotId, int button, ClickType type, Player player) {
        if (player != viewer || viewer.level().isClientSide || processing) return;
        if (slotId == 15 && type == ClickType.PICKUP) { sell(); return; }
        // Prevent taking the decorative icons and bypassing the single offer slot.
        if (slotId >= 0 && slotId < 27 && slotId != 11) return;
        if (slotId == 11 && type != ClickType.PICKUP) return;
        if (slotId == 11 && !getCarried().isEmpty()
                && (!getCarried().is(wanted) || getCarried().hasTag())) return;
        super.clicked(slotId, button, type, player);
    }

    @Override public boolean stillValid(Player player) {
        return player == viewer && viewer.isAlive() && !viewer.isRemoved() && canTrade.getAsBoolean();
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override public void removed(Player player) {
        ItemStack leftover = slots.getItem(11).copy();
        slots.setItem(11, ItemStack.EMPTY);
        super.removed(player);
        if (!leftover.isEmpty() && player instanceof ServerPlayer server) {
            server.getInventory().add(leftover);
            if (!leftover.isEmpty()) server.drop(leftover, false);
        }
    }
}
