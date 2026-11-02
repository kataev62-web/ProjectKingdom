package ru.projectkingdom.assistant;

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

/** Owner-only control panel opened from the Kingdom book. */
public final class KingdomAssistantMenu extends ChestMenu {
    public enum Action { SUMMON, FOLLOW, STAY, STATUS, CANCEL }

    private final ServerPlayer viewer;
    private final SimpleContainer content;

    private KingdomAssistantMenu(int id, Inventory inventory, ServerPlayer viewer) {
        this(id, inventory, viewer, new SimpleContainer(27));
    }

    private KingdomAssistantMenu(int id, Inventory inventory, ServerPlayer viewer, SimpleContainer content) {
        super(MenuType.GENERIC_9x3, id, inventory, content, 3);
        this.viewer = viewer;
        this.content = content;
        draw();
    }

    public static void open(ServerPlayer owner) {
        if (owner == null || !owner.hasPermissions(4)) return;
        owner.openMenu(new SimpleMenuProvider(
                (id, inventory, player) -> new KingdomAssistantMenu(id, inventory, owner),
                Component.literal("Архитектор Авалона")));
    }

    private void draw() {
        icon(10, Items.AMETHYST_SHARD, "Призвать помощника");
        icon(11, Items.COMPASS, "Следовать за мной");
        icon(12, Items.ARMOR_STAND, "Остаться здесь");
        icon(13, Items.PAPER, "Показать статус");
        icon(14, Items.BARRIER, "Отменить текущую задачу");
    }

    private void icon(int slot, Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.setHoverName(Component.literal(name));
        content.setItem(slot, stack);
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (player != viewer || viewer.level().isClientSide || !viewer.hasPermissions(4)
                || type != ClickType.PICKUP || button != 0) return;
        Action action = switch (slot) {
            case 10 -> Action.SUMMON;
            case 11 -> Action.FOLLOW;
            case 12 -> Action.STAY;
            case 13 -> Action.STATUS;
            case 14 -> Action.CANCEL;
            default -> null;
        };
        if (action == null) return;
        if (action == Action.SUMMON) {
            var result = KingdomAssistantRegistry.spawnFor(viewer);
            viewer.sendSystemMessage(Component.literal("[Архитектор Авалона] " + switch (result) {
                case CREATED -> "Помощник призван.";
                case ALREADY_EXISTS -> "Помощник уже существует; дубликат не создан.";
                case NOT_ADMIN -> "Недостаточно прав.";
            }));
        } else {
            AssistantIntent intent = switch (action) {
                case FOLLOW -> AssistantIntent.FOLLOW;
                case STAY -> AssistantIntent.STAY;
                case STATUS -> AssistantIntent.STATUS;
                case CANCEL -> AssistantIntent.CANCEL;
                case SUMMON -> throw new IllegalStateException("handled above");
            };
            KingdomAssistantTasks.submit(viewer, new AssistantCommand(intent, ""));
        }
        draw();
    }

    @Override public boolean stillValid(Player player) {
        return player == viewer && viewer.hasPermissions(4);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
