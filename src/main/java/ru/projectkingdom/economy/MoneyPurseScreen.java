package ru.projectkingdom.economy;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import ru.projectkingdom.npc.KingdomCurrency;
import ru.projectkingdom.core.KingdomFonts;

/** Compact parchment purse screen with server-authoritative actions. */
public final class MoneyPurseScreen extends AbstractContainerScreen<MoneyPurseMenu> {
    private static final int INK = 0xFF302015;
    private static final int MUTED = 0xFF6D5236;
    private static final int PARCHMENT = 0xFFF0D59A;
    private static final int PARCHMENT_DARK = 0xFFB98A4D;

    public MoneyPurseScreen(MoneyPurseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, KingdomFonts.readable(title));
        imageWidth = 248;
        imageHeight = 176;
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(button("Собрать монеты", leftPos + 24, topPos + 126, 98, MoneyPurseMenu.COLLECT));
        addRenderableWidget(button("Медь", leftPos + 130, topPos + 88, 88, MoneyPurseMenu.WITHDRAW_COPPER));
        addRenderableWidget(button("Серебро", leftPos + 130, topPos + 112, 88, MoneyPurseMenu.WITHDRAW_SILVER));
        addRenderableWidget(button("Золото", leftPos + 130, topPos + 136, 88, MoneyPurseMenu.WITHDRAW_GOLD));
    }

    private Button button(String label, int x, int y, int width, int action) {
        return Button.builder(KingdomFonts.readable(label), ignored -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
            }
        }).bounds(x, y, width, 20).build();
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + imageHeight - 4, PARCHMENT_DARK);
        graphics.fill(leftPos + 9, topPos + 9, leftPos + imageWidth - 9, topPos + imageHeight - 9, PARCHMENT);
        graphics.fill(leftPos + 18, topPos + 38, leftPos + imageWidth - 18, topPos + 39, PARCHMENT_DARK);
        graphics.fill(leftPos + 124, topPos + 50, leftPos + 125, topPos + imageHeight - 18, PARCHMENT_DARK);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        MoneyPurse purse = menu.purse();
        graphics.drawCenteredString(font, KingdomFonts.readable("Денежный мешок"), imageWidth / 2, 18, INK);
        graphics.drawString(font, KingdomFonts.readable("Заполнено: " + purse.coinCount()
            + " / " + MoneyPurse.MAX_COINS), 24, 48, INK, false);

        // Real physical coin icons instead of a cramped accounting-only screen.
        graphics.renderItem(new ItemStack(KingdomCurrency.COPPER.get()), 24, 61);
        graphics.renderItem(new ItemStack(KingdomCurrency.SILVER.get()), 24, 79);
        graphics.renderItem(new ItemStack(KingdomCurrency.GOLD.get()), 24, 97);
        graphics.drawString(font, KingdomFonts.readable("Медь: " + purse.copper()), 45, 65, MUTED, false);
        graphics.drawString(font, KingdomFonts.readable("Серебро: " + purse.silver()), 45, 83, MUTED, false);
        graphics.drawString(font, KingdomFonts.readable("Золото: " + purse.gold()), 45, 101, MUTED, false);
        graphics.drawString(font, KingdomFonts.readable("Стоимость"), 140, 52, MUTED, false);
        graphics.drawString(font, KingdomFonts.readable(format(purse.valueCopper()) + " мед."), 140, 68, INK, false);
        if (purse.legacyOverflow()) {
            graphics.drawString(font, KingdomFonts.readable("Старый переполненный мешок"), 24, 155, 0xFF8B2F2F, false);
        }
    }

    private static String format(long value) {
        return String.format(java.util.Locale.ROOT, "%,d", value).replace(',', ' ');
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
