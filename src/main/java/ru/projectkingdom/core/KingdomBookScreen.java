package ru.projectkingdom.core;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class KingdomBookScreen extends AbstractContainerScreen<KingdomBookMenu> {
    private static final int OUTLINE = 0xFF1A100A;
    private static final int WOOD_DARK = 0xFF3A2417;
    private static final int WOOD = 0xFF654126;
    private static final int WOOD_LIGHT = 0xFF8C6137;
    private static final int GOLD_DARK = 0xFF8D6D32;
    private static final int GOLD = 0xFFD2B064;
    private static final int PARCHMENT_DARK = 0xFFC8AE76;
    private static final int PARCHMENT = 0xFFE8D9AD;
    private static final int PARCHMENT_LIGHT = 0xFFF2E6C4;
    private static final int INK = 0xFF2D1B0E;
    private static final int INK_MUTED = 0xFF6A5436;
    private static final int BURGUNDY_DARK = 0xFF4B1719;
    private static final int BURGUNDY = 0xFF7D2529;
    private static final int SUCCESS = 0xFF416B35;

    private BookModel.Section selected = BookModel.Section.PLAYER;

    public KingdomBookScreen(KingdomBookMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = KingdomBookLayout.WIDTH;
        imageHeight = KingdomBookLayout.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        var bounds = KingdomBookLayout.tabs();
        var sections = BookModel.Section.values();
        for (int index = 0; index < sections.length; index++) {
            var section = sections[index];
            var rect = bounds.get(index);
            addRenderableWidget(new SectionTab(
                    leftPos + rect.x(), topPos + rect.y(), rect.width(), rect.height(), section));
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        graphics.fill(x + 4, y + 5, x + imageWidth + 4, y + imageHeight + 5, 0x88000000);
        borderedPanel(graphics, x, y, imageWidth, imageHeight, OUTLINE, GOLD_DARK, WOOD);
        graphics.fill(x + 5, y + 5, x + imageWidth - 5, y + 35, WOOD_DARK);
        graphics.fill(x + 7, y + 7, x + imageWidth - 7, y + 9, WOOD_LIGHT);
        graphics.fill(x + 5, y + 34, x + imageWidth - 5, y + 36, GOLD_DARK);

        graphics.renderItem(new ItemStack(Items.WRITABLE_BOOK), x + 13, y + 12);
        graphics.drawString(font, "PROJECT KINGDOM", x + 36, y + 11, GOLD, false);
        graphics.drawString(font, "КНИГА ПЕРСОНАЖА", x + 36, y + 22, 0xFFE1C98F, false);

        borderedPanel(graphics, x + 9, y + 38, 115, 171, OUTLINE, GOLD_DARK, WOOD_DARK);
        drawWoodGrain(graphics, x + 11, y + 40, 111, 167);

        var content = KingdomBookLayout.contentPanel();
        borderedPanel(graphics, x + content.x(), y + content.y(), content.width(), content.height(),
                OUTLINE, PARCHMENT_DARK, PARCHMENT);
        graphics.fill(x + content.x() + 4, y + content.y() + 4,
                x + content.right() - 3, y + content.bottom() - 3, PARCHMENT_LIGHT);
        drawParchmentMarks(graphics, x + content.x() + 5, y + content.y() + 5,
                content.width() - 10, content.height() - 10);

        drawRivet(graphics, x + 4, y + 4);
        drawRivet(graphics, x + imageWidth - 8, y + 4);
        drawRivet(graphics, x + 4, y + imageHeight - 8);
        drawRivet(graphics, x + imageWidth - 8, y + imageHeight - 8);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        var panel = KingdomBookLayout.contentPanel();
        int x = panel.x() + 13;
        int y = panel.y() + 11;

        graphics.renderItem(iconFor(selected), x, y - 4);
        graphics.drawString(font, selected.title().toUpperCase(), x + 22, y, BURGUNDY_DARK, false);
        graphics.fill(panel.x() + 10, y + 16, panel.right() - 9, y + 18, PARCHMENT_DARK);
        graphics.fill(panel.x() + 10, y + 16, panel.x() + 54, y + 17, BURGUNDY);

        switch (selected) {
            case PLAYER -> renderPlayerPage(graphics, x, y + 28);
            case BANK -> renderBalancePage(graphics, x, y + 28, "ЛИЧНЫЙ БАНКОВСКИЙ СЧЁТ", menu.bankBalance(), Items.GOLD_INGOT);
            case CAPITAL -> renderBalancePage(graphics, x, y + 28, "ОБЩИЙ КАПИТАЛ", menu.totalCapital(), Items.CHEST);
            case STATE -> renderPlaceholderPage(graphics, x, y + 28, "Государственное управление");
            case DYNASTY -> renderPlaceholderPage(graphics, x, y + 28, "История рода и наследники");
            case WORLD -> renderPlaceholderPage(graphics, x, y + 28, "Сведения о мире Avalon");
        }
    }

    private void renderPlayerPage(GuiGraphics graphics, int x, int y) {
        drawInfoCard(graphics, x, y, 172, 42);
        graphics.renderItem(new ItemStack(Items.PLAYER_HEAD), x + 7, y + 12);
        graphics.drawString(font, "ПРОФИЛЬ ПЕРСОНАЖА", x + 29, y + 8, INK, false);
        graphics.drawString(font, "● АКТИВЕН", x + 29, y + 23, SUCCESS, false);

        graphics.drawString(font, "Книга навсегда связана", x + 1, y + 55, INK_MUTED, false);
        graphics.drawString(font, "с вашим персонажем.", x + 1, y + 67, INK_MUTED, false);
        graphics.drawString(font, "Здесь появятся профессия,", x + 1, y + 87, INK_MUTED, false);
        graphics.drawString(font, "титул и репутация.", x + 1, y + 99, INK_MUTED, false);
    }

    private void renderBalancePage(GuiGraphics graphics, int x, int y, String heading, long amount, Item icon) {
        graphics.drawString(font, heading, x + 1, y + 2, INK_MUTED, false);
        drawInfoCard(graphics, x, y + 17, 172, 47);
        graphics.renderItem(new ItemStack(icon), x + 8, y + 32);
        graphics.drawString(font, formatAmount(amount), x + 32, y + 29, INK, false);
        graphics.drawString(font, "монет", x + 32, y + 43, INK_MUTED, false);

        String explanation = selected == BookModel.Section.BANK
                ? "Средства, хранящиеся в банке."
                : "Банк, кошелёк и имущество.";
        graphics.drawWordWrap(font, Component.literal(explanation), x + 1, y + 76, 172, INK_MUTED);
    }

    private void renderPlaceholderPage(GuiGraphics graphics, int x, int y, String description) {
        drawInfoCard(graphics, x, y, 172, 43);
        graphics.renderItem(new ItemStack(Items.IRON_BARS), x + 8, y + 13);
        graphics.drawString(font, "РАЗДЕЛ ГОТОВИТСЯ", x + 31, y + 9, INK, false);
        graphics.drawString(font, "СКОРО", x + 31, y + 24, BURGUNDY, false);
        graphics.drawWordWrap(font, Component.literal(description), x + 1, y + 56, 172, INK_MUTED);
        graphics.drawWordWrap(font, Component.literal("Функция будет подключена на соответствующем этапе разработки."),
                x + 1, y + 82, 172, INK_MUTED);
    }

    private static ItemStack iconFor(BookModel.Section section) {
        return new ItemStack(switch (section) {
            case PLAYER -> Items.PLAYER_HEAD;
            case BANK -> Items.GOLD_INGOT;
            case CAPITAL -> Items.CHEST;
            case STATE -> Items.SHIELD;
            case DYNASTY -> Items.NAME_TAG;
            case WORLD -> Items.COMPASS;
        });
    }

    private static String formatAmount(long amount) {
        return String.format(java.util.Locale.ROOT, "%,d", amount).replace(',', ' ');
    }

    private static void borderedPanel(GuiGraphics graphics, int x, int y, int width, int height,
                                      int outer, int trim, int fill) {
        graphics.fill(x, y, x + width, y + height, outer);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, trim);
        graphics.fill(x + 4, y + 4, x + width - 4, y + height - 4, fill);
    }

    private static void drawInfoCard(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PARCHMENT_DARK);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFFD9C696);
        graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, 0xFFEDE0BB);
    }

    private static void drawRivet(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 4, y + 4, OUTLINE);
        graphics.fill(x + 1, y + 1, x + 3, y + 3, GOLD);
    }

    private static void drawWoodGrain(GuiGraphics graphics, int x, int y, int width, int height) {
        for (int offset = 9; offset < height; offset += 18) {
            graphics.fill(x + 3, y + offset, x + width - 4, y + offset + 1, 0x5530180D);
        }
    }

    private static void drawParchmentMarks(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + width - 15, y + 2, x + width - 3, y + 3, 0x22735631);
        graphics.fill(x + 4, y + height - 5, x + 23, y + height - 4, 0x22735631);
        graphics.fill(x + width - 5, y + height / 2, x + width - 4, y + height / 2 + 16, 0x22735631);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private final class SectionTab extends AbstractButton {
        private final BookModel.Section section;

        private SectionTab(int x, int y, int width, int height, BookModel.Section section) {
            super(x, y, width, height, Component.literal(section.title()));
            this.section = section;
        }

        @Override
        public void onPress() {
            selected = section;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {
            defaultButtonNarrationText(narration);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean current = selected == section;
            int outer = current ? GOLD : OUTLINE;
            int fill = current ? BURGUNDY : (isHoveredOrFocused() ? WOOD_LIGHT : WOOD);
            graphics.fill(getX(), getY(), getX() + width, getY() + height, outer);
            graphics.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, fill);
            if (current) {
                graphics.fill(getX() + width - 4, getY() + 2, getX() + width, getY() + height - 2, GOLD);
            }
            graphics.renderItem(iconFor(section), getX() + 5, getY() + 3);
            int color = current ? 0xFFFFE4A3 : 0xFFF0DFC1;
            graphics.drawString(font, getMessage(), getX() + 26, getY() + 8, color, false);
        }
    }
}
