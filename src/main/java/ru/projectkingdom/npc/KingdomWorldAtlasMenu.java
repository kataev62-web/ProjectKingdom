package ru.projectkingdom.npc;

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

/**
 * Read-only Kingdom atlas: nations, town boundaries and actually placed
 * enterprise cores. Uses vanilla inventory synchronization (only changed
 * slot contents are sent) instead of broadcasting full-world map data.
 */
public final class KingdomWorldAtlasMenu extends ChestMenu {
    private enum Layer { LAND, POLITICAL, ENTERPRISES }
    private static final int[] SCALES = {1, 4, 16, 64};
    private final ServerPlayer viewer;
    private final SimpleContainer cells;
    private int centerX, centerZ, scaleIndex;
    private Layer layer = Layer.LAND;
    private KingdomWorldAtlasSnapshot.View view;
    private KingdomWorldAtlasSnapshot.Tile selected;
    private long lastRefresh;

    private KingdomWorldAtlasMenu(int id, Inventory inventory, ServerPlayer player, SimpleContainer container) {
        super(MenuType.GENERIC_9x6, id, inventory, container, 6);
        viewer = player;
        cells = container;
        centerX = player.chunkPosition().x;
        centerZ = player.chunkPosition().z;
        redraw();
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
            new KingdomWorldAtlasMenu(id, inventory, player, new SimpleContainer(54)),
            Component.literal("Kingdom | Атлас мира")));
    }

    private void put(int slot, Item item, String label) {
        var stack = new ItemStack(item);
        stack.setHoverName(Component.literal(label));
        cells.setItem(slot, stack);
    }
    private static String brief(KingdomWorldAtlasSnapshot.Tile tile) {
        String value = tile.town().isEmpty() ? "Свободно" : tile.town();
        if (!tile.nation().isEmpty()) value += " | " + tile.nation();
        if (tile.enterprises() > 0) value += " | " + tile.enterpriseName()
                + (tile.enterprises() > 1 ? " (+" + (tile.enterprises() - 1) + ")" : "");
        return value;
    }
    private Item icon(KingdomWorldAtlasSnapshot.Tile tile) {
        if (layer == Layer.ENTERPRISES) {
            if (tile.enterprises() > 0) return Items.EMERALD;
            return tile.occupied() > 0 ? Items.LIGHT_GRAY_STAINED_GLASS_PANE
                    : Items.GRAY_STAINED_GLASS_PANE;
        }
        if (layer == Layer.POLITICAL) {
            if (tile.ownTown()) return Items.BLUE_STAINED_GLASS_PANE;
            if (!tile.nation().isEmpty()) return Items.PURPLE_STAINED_GLASS_PANE;
            if (tile.occupied() > 0) return Items.ORANGE_STAINED_GLASS_PANE;
            return Items.LIME_STAINED_GLASS_PANE;
        }
        if (tile.ownTown() && tile.foreignTown()) return Items.YELLOW_STAINED_GLASS_PANE;
        if (tile.ownTown()) return Items.BLUE_STAINED_GLASS_PANE;
        if (tile.foreignTown()) return Items.RED_STAINED_GLASS_PANE;
        return Items.LIME_STAINED_GLASS_PANE;
    }
    private void redraw() {
        lastRefresh = viewer.serverLevel().getGameTime();
        view = KingdomWorldAtlasSnapshot.forPlayer(viewer, centerX, centerZ, SCALES[scaleIndex]);
        cells.clearContent();
        for (int row = 0; row < KingdomWorldAtlasSnapshot.ROWS; row++) {
            for (int col = 0; col < KingdomWorldAtlasSnapshot.COLUMNS; col++) {
                var tile = view.at(col, row);
                int slot = row * 9 + col + 1;
                String label = "Чанки " + tile.chunkX() + ", " + tile.chunkZ();
                if (tile.scale() > 1) label += " | область " + tile.scale() + "×" + tile.scale();
                label += " | " + brief(tile);
                put(slot, icon(tile), label);
            }
        }
        put(36, Items.BOOK, "Слои: земля / государства / предприятия");
        put(37, Items.MAP, "Масштаб: " + SCALES[scaleIndex] + " чанков на клетку");
        put(38, Items.COMPASS, "Центр: " + centerX + ", " + centerZ);
        put(39, Items.WRITABLE_BOOK, selected == null
            ? "Нажми на участок карты для просмотра сведений"
            : "Выбран: " + brief(selected));
        put(40, Items.SHIELD, "Города и государства: границы из реестра Kingdom");
        put(41, Items.SMITHING_TABLE, "Предприятия: только реально установленные действующие ядра");
        put(42, Items.GOLD_NUGGET, "Карта информационная: покупки и расширение через ратушу");
        put(43, Items.PAPER, "Месторождения не отображаются до геологической разведки");
        put(44, Items.FILLED_MAP, "Изменения собственности обновляются автоматически");
        put(45, Items.ARROW, "← Запад");
        put(46, Items.COMPASS, "↑ Север");
        put(47, Items.COMPASS, "↓ Юг");
        put(48, Items.ARROW, "→ Восток");
        put(49, Items.PAINTING, "Сменить слой: " + switch(layer) {
            case LAND -> "Земельный";
            case POLITICAL -> "Политический";
            case ENTERPRISES -> "Предприятия";
        });
        put(50, Items.MAP, "Изменить масштаб");
        put(51, Items.COMPASS, "Моё местоположение");
        put(52, Items.BOOK, "Участок: ЛКМ по клетке, чтобы посмотреть название города/предприятия");
        put(53, Items.BARRIER, "Закрыть атлас");
        super.broadcastChanges();
    }

    @Override public void broadcastChanges() {
        if (viewer != null && !viewer.level().isClientSide
                && viewer.serverLevel().getGameTime() - lastRefresh >= 40) {
            // Refresh active viewers after city purchases or new business cores.
            // The chest protocol only transmits the changed slots.
            redraw();
            return;
        }
        super.broadcastChanges();
    }

    @Override public void clicked(int slot, int button, ClickType click, Player player) {
        if (player != viewer || viewer.level().isClientSide || click != ClickType.PICKUP
                || button != 0 || slot < 0 || slot >= 54) return;
        int row = slot / 9, col = slot % 9;
        if (row < 4 && col >= 1 && col <= 7) {
            selected = view.at(col - 1, row);
            viewer.sendSystemMessage(Component.literal("[Kingdom Атлас] " + brief(selected)
                    + " | от " + selected.chunkX() + ", " + selected.chunkZ()
                    + " | занято чанков: " + selected.occupied()
                    + " | предприятий: " + selected.enterprises()));
        } else {
            switch (slot) {
                case 45 -> centerX -= 3 * SCALES[scaleIndex];
                case 46 -> centerZ -= 2 * SCALES[scaleIndex];
                case 47 -> centerZ += 2 * SCALES[scaleIndex];
                case 48 -> centerX += 3 * SCALES[scaleIndex];
                case 49 -> layer = Layer.values()[(layer.ordinal() + 1) % Layer.values().length];
                case 50 -> scaleIndex = (scaleIndex + 1) % SCALES.length;
                case 51 -> {
                    centerX = viewer.chunkPosition().x;
                    centerZ = viewer.chunkPosition().z;
                }
                case 53 -> { viewer.closeContainer(); return; }
                default -> { return; }
            }
        }
        redraw();
    }
    @Override public boolean stillValid(Player player) {
        return player == viewer && player.isAlive();
    }
    @Override public void removed(Player player) {
        super.removed(player);
        cells.clearContent();
    }
}
