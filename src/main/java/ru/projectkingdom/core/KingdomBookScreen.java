package ru.projectkingdom.core;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class KingdomBookScreen extends AbstractContainerScreen<KingdomBookMenu> {
    private BookModel.Section selected = BookModel.Section.PLAYER;

    public KingdomBookScreen(KingdomBookMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 292;
        imageHeight = 190;
    }

    @Override protected void init() {
        super.init();
        int x = leftPos + 12;
        int y = topPos + 28;
        int index = 0;
        for (BookModel.Section section : BookModel.Section.values()) {
            int buttonY = y + (index++ * 24);
            addRenderableWidget(Button.builder(Component.literal(section.title()), button -> selected = section)
                    .bounds(x, buttonY, 92, 20).build());
        }
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.enableBlend();
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xEEDCC59A);
        graphics.fill(leftPos + 112, topPos + 24, leftPos + imageWidth - 12, topPos + imageHeight - 16, 0x66FFFFFF);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 12, 8, 0x3A2415, false);
        graphics.drawString(font, selected.title(), 122, 32, 0x3A2415, false);
        String line = switch (selected) {
            case PLAYER -> "Профиль персонажа активен";
            case BANK -> "Баланс банка: " + menu.bankBalance();
            case CAPITAL -> "Общий капитал: " + menu.totalCapital();
            case STATE, DYNASTY, WORLD -> "Раздел подготовлен. Подключение позже.";
        };
        graphics.drawWordWrap(font, Component.literal(line), 122, 52, 150, 0x3A2415);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
