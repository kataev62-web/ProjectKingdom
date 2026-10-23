package ru.projectkingdom.npc;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
import ru.projectkingdom.administration.KingdomAdministrationData;

/**
 * Founder-facing administrative planning map. Uses CivicsData as the only
 * jurisdiction source of truth; plans do not reserve territory or spend coins.
 */
public final class KingdomCityExpansionMapMenu extends ChestMenu {
    private final ServerPlayer viewer;
    private final SimpleContainer inventory;
    private final Set<Long> planned = new HashSet<>();
    private int centerX, centerZ;
    private java.util.UUID cityId;
    private long lastRefresh;
    private Set<Long> confirmedPlan;

    private KingdomCityExpansionMapMenu(int id, Inventory playerInventory, ServerPlayer player, SimpleContainer grid) {
        super(MenuType.GENERIC_9x6, id, playerInventory, grid, 6);
        viewer = player;
        inventory = grid;
        centerX = player.chunkPosition().x;
        centerZ = player.chunkPosition().z;
        var town = KingdomCivicsData.get(player.getServer()).townOf(player.getUUID());
        cityId = town == null ? null : town.id();
        if (cityId != null && founder()) {
            for (var cell : KingdomExpansionPlans.get(player.getServer()).snapshot(cityId)) {
                if (cell.dimension().equals(dimension())) planned.add(key(cell.x(), cell.z()));
            }
        }
        pruneInvalid();
        refresh();
    }

    public static void open(ServerPlayer player) {
        var data = KingdomCivicsData.get(player.getServer());
        var town = data.townOf(player.getUUID());
        if (town == null) {
            player.sendSystemMessage(Component.literal("[Kingdom] Сначала вступите в город."));
            return;
        }
        player.openMenu(new SimpleMenuProvider(
            (id, inv, ignored) -> new KingdomCityExpansionMapMenu(id, inv, player, new SimpleContainer(54)),
            Component.literal("Kingdom | Границы города")));
    }

    private static long key(int x, int z) {
        return ((long)x << 32) | (z & 0xffffffffL);
    }
    private static int x(long k) { return (int)(k >> 32); }
    private static int z(long k) { return (int)k; }
    private String dimension() { return viewer.serverLevel().dimension().location().toString(); }
    private boolean founder() {
        var data = KingdomCivicsData.get(viewer.getServer());
        var town = data.townOf(viewer.getUUID());
        return town != null && viewer.getUUID().equals(
            KingdomAdministrationData.get(viewer.getServer()).founder(town.id()));
    }
    private boolean contiguous(KingdomCivicsData data, int x, int z) {
        var town = data.townOf(viewer.getUUID());
        if (town == null) return false;
        return hasTownOrPlan(data, town.id(), x + 1, z)
            || hasTownOrPlan(data, town.id(), x - 1, z)
            || hasTownOrPlan(data, town.id(), x, z + 1)
            || hasTownOrPlan(data, town.id(), x, z - 1);
    }
    private boolean hasTownOrPlan(KingdomCivicsData data, java.util.UUID townId, int x, int z) {
        if (founder() && planned.contains(key(x, z))) return true;
        for (var plot : data.mapPlots(dimension())) {
            if (plot.x() == x && plot.z() == z && townId.equals(plot.town())) return true;
        }
        return false;
    }
    private void put(int slot, Item item, String caption) {
        var stack = new ItemStack(item);
        stack.setHoverName(Component.literal(caption));
        inventory.setItem(slot, stack);
    }
    private void savePlan() {
        if (cityId == null || !founder()) return;
        var plans = KingdomExpansionPlans.get(viewer.getServer());
        var merged = new java.util.LinkedHashSet<KingdomExpansionPlans.Cell>();
        for (var old : plans.snapshot(cityId)) if (!old.dimension().equals(dimension())) merged.add(old);
        for (long cell : planned) merged.add(new KingdomExpansionPlans.Cell(dimension(), x(cell), z(cell)));
        plans.replace(cityId, merged);
    }
    private void pruneInvalid() {
        if (cityId == null || !founder()) { planned.clear(); return; }
        var db = KingdomCivicsData.get(viewer.getServer());
        var pending = new HashSet<>(planned);
        planned.clear();
        boolean changed;
        do {
            changed = false;
            for (long cell : Set.copyOf(pending)) {
                boolean occupied = false;
                for (var plot : db.mapPlots(dimension())) {
                    if (plot.x() == x(cell) && plot.z() == z(cell)) { occupied = true; break; }
                }
                if (occupied) { pending.remove(cell); changed = true; continue; }
                if (contiguous(db, x(cell), z(cell))) {
                    planned.add(cell); pending.remove(cell); changed = true;
                }
            }
        } while (changed);
        savePlan();
    }
    private void refresh() {
        lastRefresh = viewer.serverLevel().getGameTime();
        inventory.clearContent();
        var data = KingdomCivicsData.get(viewer.getServer());
        var town = data.townOf(viewer.getUUID());
        if (town == null) return;
        String dim = dimension();
        java.util.Map<Long, KingdomCivicsData.Plot> occupied = new java.util.HashMap<>();
        for (var plot : data.mapPlots(dim)) occupied.put(key(plot.x(), plot.z()), plot);
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 7; column++) {
                int cx = centerX + column - 3, cz = centerZ + row - 1;
                var existing = occupied.get(key(cx, cz));
                boolean ours = existing != null && town.id().equals(existing.town());
                boolean planning = planned.contains(key(cx, cz));
                Item symbol = ours ? Items.BLUE_STAINED_GLASS_PANE
                    : existing != null ? Items.RED_STAINED_GLASS_PANE
                    : planning ? Items.YELLOW_STAINED_GLASS_PANE
                    : Items.LIME_STAINED_GLASS_PANE;
                String status = ours ? "граница города" : existing != null ? "чужой город"
                    : planning ? "в плане" : "свободно";
                put(row * 9 + column + 1, symbol, cx + ", " + cz + " — " + status);
            }
        }
        put(45, Items.ARROW, "На запад");
        put(46, Items.COMPASS, "На север");
        put(47, Items.COMPASS, "На юг");
        put(48, Items.ARROW, "На восток");
        put(49, Items.MAP, "Город: " + town.name() + " | Чанков в плане: " + planned.size());
        put(44, Items.GOLD_NUGGET, "Оценка покупки: " + (100L * planned.size()) + " медных | 100 за чанк");
        put(43, Items.GOLD_BLOCK, confirmedPlan == null
                ? "Купить свободные земли через карту (оплата из обеспеченной казны)"
                : "ПОДТВЕРДИТЬ ВЫКУП: повторное нажатие");
        put(50, Items.WRITABLE_BOOK, "Проверить план и средства городской казны");
        put(51, Items.BARRIER, "Очистить план");
        put(52, Items.COMPASS, "К позиции игрока");
        put(53, Items.OAK_DOOR, "К картам Kingdom");
        super.broadcastChanges();
    }
    @Override public void broadcastChanges() {
        if (viewer != null && !viewer.level().isClientSide
                && viewer.serverLevel().getGameTime() - lastRefresh >= 40) {
            refresh();
            return;
        }
        super.broadcastChanges();
    }
    @Override public void clicked(int slot, int button, ClickType click, Player player) {
        if (player != viewer || viewer.level().isClientSide || click != ClickType.PICKUP
                || button != 0 || slot < 0 || slot >= 54) return;
        if (slot != 43) confirmedPlan = null;
        int row = slot / 9, col = slot % 9;
        if (row < 4 && col >= 1 && col <= 7) {
            if (!founder()) {
                viewer.sendSystemMessage(Component.literal("[Kingdom] Планировать расширение может основатель."));
                return;
            }
            int x = centerX + col - 4, z = centerZ + row - 1;
            long key = key(x, z);
            var data = KingdomCivicsData.get(viewer.getServer());
            if (planned.contains(key)) {
                planned.remove(key);
                // Rebuild valid planned territory after a deletion; don't leave detached proposals.
                Set<Long> pending = new HashSet<>(planned);
                planned.clear();
                boolean changed;
                do {
                    changed = false;
                    for (long next : Set.copyOf(pending)) {
                        if (contiguous(data, x(next), z(next))) {
                            planned.add(next);
                            pending.remove(next);
                            changed = true;
                        }
                    }
                } while (changed);
            } else {
                boolean owned = false;
                for (var claim : data.mapPlots(dimension())) {
                    if (claim.x() == x && claim.z() == z) { owned = true; break; }
                }
                if (!owned && contiguous(data, x, z) && planned.size() < 256) planned.add(key);
                else viewer.sendSystemMessage(Component.literal("[Kingdom] Можно выбирать только свободные соседние чанки."));
            }
        } else {
            switch (slot) {
                case 45 -> centerX -= 7;
                case 46 -> centerZ -= 4;
                case 47 -> centerZ += 4;
                case 48 -> centerX += 7;
                case 50 -> { checkQuote(); return; }
                case 43 -> { purchasePlan(); return; }
                case 51 -> planned.clear();
                case 52 -> { centerX = viewer.chunkPosition().x; centerZ = viewer.chunkPosition().z; }
                case 53 -> { KingdomMainMenu.openMaps(viewer); return; }
                default -> { return; }
            }
        }
        pruneInvalid();
        refresh();
    }
    private void purchasePlan() {
        if (!founder() || cityId == null) return;
        pruneInvalid();
        if (planned.isEmpty()) {
            confirmedPlan = null;
            viewer.sendSystemMessage(Component.literal("[Kingdom] План пуст."));
            refresh();
            return;
        }
        if (confirmedPlan == null || !confirmedPlan.equals(planned)) {
            confirmedPlan = Set.copyOf(planned);
            checkQuote();
            viewer.sendSystemMessage(Component.literal(
                    "[Kingdom] Для покупки выбранных чанков из казны нажми золотой блок ещё раз."));
            refresh();
            return;
        }
        // The transaction rechecks authoritative claims, the world border,
        // town hall, founder and treasury on the server.
        confirmedPlan = null;
        var result = KingdomCityExpansionPurchase.purchase(viewer);
        String status = switch (result) {
            case PURCHASED -> "Территория присоединена, средства списаны из городской казны.";
            case NOT_FOUNDER -> "Покупка доступна только основателю города.";
            case NOT_AT_TOWN_HALL -> "Участок оформляется через карту города.";
            case INVALID_PLAN -> "План пуст, несмежен или больше не актуален.";
            case LAND_CONFLICT -> "Один из участков занят или находится за границей мира.";
            case INSUFFICIENT_FUNDS -> "В казне недостаточно обеспеченных монет.";
            case LEDGER_ERROR -> "Платёж не удалось зарегистрировать.";
            case RECONCILIATION_REQUIRED -> "Обнаружено расхождение реестров. Требуется проверка администрации сервера.";
        };
        viewer.sendSystemMessage(Component.literal("[Kingdom] " + status));
        if (result == KingdomCityExpansionPurchase.Result.PURCHASED) {
            planned.clear();
            for (var cell : KingdomExpansionPlans.get(viewer.getServer()).snapshot(cityId))
                if (cell.dimension().equals(dimension())) planned.add(key(cell.x(),cell.z()));
        }
        pruneInvalid();
        refresh();
    }
    private void checkQuote() {
        if (!founder() || cityId == null) return;
        pruneInvalid();
        var db = KingdomCivicsData.get(viewer.getServer());
        var town = db.townById(cityId);
        if (town == null) return;
        java.util.List<KingdomExpansionPlans.Cell> cells = new java.util.ArrayList<>();
        for (long cell : planned) cells.add(new KingdomExpansionPlans.Cell(dimension(), x(cell), z(cell)));
        var quote = KingdomExpansionQuote.evaluate(cityId, dimension(), cells,
            db.mapPlots(dimension()), town.treasury());
        viewer.sendSystemMessage(Component.literal("[Kingdom] План: " + quote.status()
            + " | чанков: " + quote.chunks() + " | стоимость: " + quote.price()
            + " медных | казна: " + town.treasury()));
        viewer.sendSystemMessage(Component.literal("[Kingdom] Это предварительная проверка, оплата и присоединение не выполнены."));
        refresh();
    }
    @Override public boolean stillValid(Player player) {
        return player == viewer && player.isAlive();
    }
    @Override public void removed(Player player) {
        super.removed(player);
        inventory.clearContent();
    }
}
