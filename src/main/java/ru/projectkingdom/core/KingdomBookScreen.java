package ru.projectkingdom.core;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.projectkingdom.network.KingdomNetwork;
import ru.projectkingdom.network.OpenLandMapFromBookPacket;

public final class KingdomBookScreen extends AbstractContainerScreen<KingdomBookMenu> {
    private static final ResourceLocation BOOK_TEXTURE =
            new ResourceLocation("kingdomnpc", "textures/gui/player_book.png");
    private static final int INK = 0xFF352315;
    private static final int INK_MUTED = 0xFF735B37;
    private static final int LINE = 0xFFAA8A52;
    private static final int GOLD_DARK = 0xFF8C682E;
    private static final int GOLD = 0xFFD5B464;
    private static final int BURGUNDY_DARK = 0xFF43161A;
    private static final int BURGUNDY = 0xFF76262C;
    private static final int BURGUNDY_LIGHT = 0xFF984047;
    private static final int SUCCESS = 0xFF476A35;

    private BookModel.Section selected = BookModel.Section.PLAYER;
    private AbstractButton landMapButton;

    public KingdomBookScreen(KingdomBookMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = KingdomBookLayout.WIDTH;
        imageHeight = KingdomBookLayout.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        var rightPage = KingdomBookLayout.rightPage();
        landMapButton = new AbstractButton(leftPos + rightPage.x() + 12,
                topPos + rightPage.y() + 113, rightPage.width() - 24, 20,
                Component.literal("Открыть карту земель")) {
            @Override public void onPress() {
                KingdomNetwork.CHANNEL.sendToServer(new OpenLandMapFromBookPacket());
            }
            @Override protected void updateWidgetNarration(NarrationElementOutput narration) {
                defaultButtonNarrationText(narration);
            }
        };
        landMapButton.visible = selected == BookModel.Section.WORLD || selected == BookModel.Section.STATE;
        addRenderableWidget(landMapButton);
        var bounds = KingdomBookLayout.tabs();
        var sections = BookModel.Section.values();
        for (int index = 0; index < sections.length; index++) {
            var section = sections[index];
            var rect = bounds.get(index);
            var tab = new SectionTab(
                    leftPos + rect.x(), topPos + rect.y(), rect.width(), rect.height(), section);
            tab.setTooltip(Tooltip.create(Component.literal(section.title())));
            addRenderableWidget(tab);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BOOK_TEXTURE, leftPos, topPos, 0, 0,
                imageWidth, imageHeight, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        var left = KingdomBookLayout.leftPage();
        var right = KingdomBookLayout.rightPage();
        drawPageHeading(graphics, left, selected.title());
        drawPageHeading(graphics, right, rightHeading());
        renderLeftPage(graphics, left);
        renderRightPage(graphics, right);
        drawPageNumber(graphics, left, "I");
        drawPageNumber(graphics, right, "II");
    }

    private void renderLeftPage(GuiGraphics graphics, KingdomBookLayout.Rect page) {
        int center = page.x() + page.width() / 2;
        int y = page.y() + 31;
        graphics.renderItem(iconFor(selected), center - 8, y);

        switch (selected) {
            case PLAYER -> {
                centerText(graphics, playerName(), center, y + 23, INK);
                centerText(graphics, "● ПЕРСОНАЖ АКТИВЕН", center, y + 37, SUCCESS);
                divider(graphics, page, y + 54);
                wrapped(graphics, "Эта книга навсегда связана с вашим персонажем.", page, y + 64);
            }
            case BANK -> {
                centerText(graphics, "ЛИЧНЫЙ СЧЁТ", center, y + 24, INK);
                centerText(graphics, "БАНК КОРОЛЕВСТВА", center, y + 38, INK_MUTED);
                divider(graphics, page, y + 54);
                wrapped(graphics, "Средства игрока, которые хранятся в банке.", page, y + 64);
            }
            case CAPITAL -> {
                centerText(graphics, "СВОДНЫЙ КАПИТАЛ", center, y + 24, INK);
                centerText(graphics, "ИМУЩЕСТВО И СРЕДСТВА", center, y + 38, INK_MUTED);
                divider(graphics, page, y + 54);
                wrapped(graphics, "Общая стоимость накоплений и владений.", page, y + 64);
            }
            case STATE -> placeholderLeft(graphics, page, center, y, "УПРАВЛЕНИЕ ДЕРЖАВОЙ");
            case DYNASTY -> placeholderLeft(graphics, page, center, y, "РОД И НАСЛЕДНИКИ");
            case WORLD -> placeholderLeft(graphics, page, center, y, "ЛЕТОПИСЬ AVALON");
        }
    }

    private void renderRightPage(GuiGraphics graphics, KingdomBookLayout.Rect page) {
        int x = page.x() + 10;
        int center = page.x() + page.width() / 2;
        int y = page.y() + 34;

        switch (selected) {
            case PLAYER -> {
                label(graphics, "ВЛАДЕЛЕЦ", x, y);
                centerText(graphics, playerName(), center, y + 16, INK);
                divider(graphics, page, y + 31);
                label(graphics, "СТАТУС КНИГИ", x, y + 43);
                graphics.drawString(font, KingdomFonts.readable("Выдана"), x, y + 58, SUCCESS, false);
                label(graphics, "СЛЕДУЮЩИЕ ГЛАВЫ", x, y + 82);
                wrapped(graphics, "Профессия, титул и репутация будут подключены позже.", page, y + 96);
            }
            case BANK -> renderAmount(graphics, page, y, "БАЛАНС", menu.bankBalance(),
                    "Пополнение через лаунчер будет подключено после выбора провайдера.");
            case CAPITAL -> renderAmount(graphics, page, y, "ВСЕГО", menu.totalCapital(),
                    "Банк, кошелёк и имущество персонажа.");
            case STATE -> {
                label(graphics, "ТЕРРИТОРИИ И ГРАНИЦЫ", x, y);
                wrapped(graphics, "Карта городов, участков и свободной земли.", page, y + 19);
                divider(graphics, page, y + 61);
                wrapped(graphics, "Сделки оформляются только в ратуше.", page, y + 70);
            }
            case DYNASTY -> placeholderRight(graphics, page, center, y, "История рода и наследники");
            case WORLD -> {
                label(graphics, "ЗЕМЕЛЬНАЯ КАРТА", x, y);
                wrapped(graphics, "Посмотрите занятые и свободные участки рядом с вами.", page, y + 19);
                divider(graphics, page, y + 61);
                wrapped(graphics, "Границы синхронизируются с сервером.", page, y + 70);
            }
        }
    }

    private void renderAmount(GuiGraphics graphics, KingdomBookLayout.Rect page, int y,
                              String caption, long amount, String description) {
        int center = page.x() + page.width() / 2;
        label(graphics, caption, page.x() + 10, y);
        centerText(graphics, formatAmount(amount), center, y + 21, BURGUNDY_DARK);
        centerText(graphics, "МОНЕТ", center, y + 36, INK_MUTED);
        divider(graphics, page, y + 54);
        wrapped(graphics, description, page, y + 65);
    }

    private void placeholderLeft(GuiGraphics graphics, KingdomBookLayout.Rect page,
                                 int center, int y, String caption) {
        centerText(graphics, caption, center, y + 25, INK);
        divider(graphics, page, y + 45);
        centerText(graphics, "ГЛАВА ЗАПЕЧАТАНА", center, y + 59, BURGUNDY);
    }

    private void placeholderRight(GuiGraphics graphics, KingdomBookLayout.Rect page,
                                  int center, int y, String description) {
        centerText(graphics, "СКОРО", center, y + 12, BURGUNDY_DARK);
        divider(graphics, page, y + 30);
        wrapped(graphics, description, page, y + 43);
        wrapped(graphics, "Функция будет открыта на соответствующем этапе разработки.", page, y + 78);
    }

    private void drawPageHeading(GuiGraphics graphics, KingdomBookLayout.Rect page, String text) {
        int center = page.x() + page.width() / 2;
        centerText(graphics, text, center, page.y() + 5, BURGUNDY_DARK);
        graphics.fill(page.x() + 9, page.y() + 18, page.right() - 8, page.y() + 19, LINE);
        graphics.fill(center - 14, page.y() + 17, center + 14, page.y() + 19, BURGUNDY);
    }

    private void drawPageNumber(GuiGraphics graphics, KingdomBookLayout.Rect page, String number) {
        centerText(graphics, number, page.x() + page.width() / 2, page.bottom() - 11, INK_MUTED);
    }

    private void divider(GuiGraphics graphics, KingdomBookLayout.Rect page, int y) {
        graphics.fill(page.x() + 12, y, page.right() - 11, y + 1, LINE);
    }

    private void label(GuiGraphics graphics, String text, int x, int y) {
        graphics.drawString(font, KingdomFonts.readable(text), x, y, INK_MUTED, false);
    }

    private void centerText(GuiGraphics graphics, String text, int centerX, int y, int color) {
        graphics.drawCenteredString(font, KingdomFonts.readable(text), centerX, y, color);
    }

    private void wrapped(GuiGraphics graphics, String text, KingdomBookLayout.Rect page, int y) {
        graphics.drawWordWrap(font, KingdomFonts.readable(text), page.x() + 10, y, page.width() - 20, INK_MUTED);
    }

    private String rightHeading() {
        return switch (selected) {
            case PLAYER -> "Сведения";
            case BANK -> "Состояние счёта";
            case CAPITAL -> "Итог владений";
            case STATE -> "Государство";
            case DYNASTY -> "Династия";
            case WORLD -> "Мир";
        };
    }

    private String playerName() {
        return minecraft != null && minecraft.player != null
                ? minecraft.player.getName().getString()
                : "Игрок";
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
            if (landMapButton != null) landMapButton.visible = section == BookModel.Section.WORLD || section == BookModel.Section.STATE;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {
            defaultButtonNarrationText(narration);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean current = selected == section;
            int border = current ? GOLD : GOLD_DARK;
            int fill = current ? BURGUNDY_LIGHT : (isHoveredOrFocused() ? BURGUNDY : BURGUNDY_DARK);
            int x = getX();
            int y = getY();
            graphics.fill(x + 4, y, x + width - 3, y + height, border);
            graphics.fill(x + 1, y + 4, x + width, y + height - 4, border);
            graphics.fill(x + 5, y + 2, x + width - 4, y + height - 2, fill);
            graphics.fill(x + 3, y + 5, x + width - 2, y + height - 5, fill);
            graphics.fill(x + width - 2, y + 8, x + width + 5, y + height - 8,
                    current ? GOLD : BURGUNDY_DARK);
            graphics.renderItem(iconFor(section), x + 5, y + 4);
        }
    }
}
