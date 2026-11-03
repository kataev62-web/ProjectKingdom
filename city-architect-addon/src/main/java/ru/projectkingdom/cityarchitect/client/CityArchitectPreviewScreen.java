package ru.projectkingdom.cityarchitect.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.projectkingdom.cityarchitect.network.CityArchitectDecisionPacket;
import ru.projectkingdom.cityarchitect.network.CityArchitectNetwork;
import ru.projectkingdom.cityarchitect.network.CityArchitectPreviewPacket;

public final class CityArchitectPreviewScreen extends Screen {
    private final CityArchitectPreviewPacket preview;

    public CityArchitectPreviewScreen(CityArchitectPreviewPacket preview) {
        super(Component.literal("План города"));
        this.preview = preview;
    }

    public static void accept(CityArchitectPreviewPacket preview) {
        Minecraft.getInstance().setScreen(new CityArchitectPreviewScreen(preview));
    }

    @Override
    protected void init() {
        int y = height / 2 + 36;
        addRenderableWidget(Button.builder(Component.literal("✓ Подтвердить"), button -> decide(true))
                .bounds(width / 2 - 104, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("✕ Отменить"), button -> decide(false))
                .bounds(width / 2 + 4, y, 100, 20).build());
    }

    private void decide(boolean confirm) {
        CityArchitectNetwork.sendDecision(new CityArchitectDecisionPacket(preview.token(), confirm));
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, preview.title(), width / 2, height / 2 - 42, 0xFFFFFFFF);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(preview.explanation(), 300),
                width / 2, height / 2 - 20, 0xFFE6D6B8);
        graphics.drawCenteredString(font, "Размечено точек: " + preview.markers().size(),
                width / 2, height / 2 + 2, 0xFFB7D7E8);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
