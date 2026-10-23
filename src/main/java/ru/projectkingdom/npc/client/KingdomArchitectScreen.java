package ru.projectkingdom.npc.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * Small, right-aligned translucent architect placement controls. All movement
 * acts on the client ghost; only the explicit green check sends a site request.
 * Screen space is measured in Minecraft GUI pixels, not raw window pixels.
 */
public final class KingdomArchitectScreen extends Screen {
    private static final int PANEL_BG = 0xA522241F;
    private static final int PANEL_EDGE = 0x888F8166;
    private static final int PANEL_HEAD = 0x8B3C3024;
    private static final int INK = 0xFFF5E7CA;
    private static final int SOFT = 0xFFDAC6A1;

    private String error = "";
    private int left, top, panelWidth, panelHeight;
    private boolean didHide;

    private enum Icon {
        FORWARD, BACK, LEFT, RIGHT, UP, DOWN, ROTATE, EYE, CONFIRM, CANCEL
    }

    public KingdomArchitectScreen() {
        super(Component.literal("Архитектор Project Kingdom"));
    }

    private void button(int x, int y, int width, int height, Icon icon,
                        String tooltip, Runnable action) {
        var widget = new ArchitectButton(left + x, top + y, width, height, icon,
                Component.literal(tooltip), action);
        widget.setTooltip(Tooltip.create(Component.literal(tooltip)));
        addRenderableWidget(widget);
    }

    @Override protected void init() {
        super.init();
        double guiScale = minecraft == null ? 3 : minecraft.getWindow().getGuiScale();
        if (!Double.isFinite(guiScale) || guiScale < 1) guiScale = 3;

        // On a 1039x686 screenshot at GUI scale 3, this is approximately
        // 192x378 physical pixels. At other GUI scales it remains usable.
        panelWidth = Math.min(Math.max(64, (int) Math.round(176.0 / guiScale)),
                Math.max(54, width - 8));
        panelHeight = Math.min(Math.max(126, (int) Math.round(364.0 / guiScale)),
                Math.max(120, height - 8));
        left = Math.max(2, width - panelWidth - Math.max(4, (int) Math.round(12.0 / guiScale)));
        top = Math.max(2, (height - panelHeight) / 2);

        // Ghost is controlled relative to the direction the PLAYER faces.
        // Shift + an arrow moves 10 blocks for fast positioning.
        final int size = Math.max(13, Math.min(20, (panelWidth - 12) / 3));
        final int gap = 2;
        final int center = panelWidth / 2;
        final int startY = 44 + Math.max(0, (panelHeight - 126) / 2);
        int iconHeight = 14;
        button(center - size / 2, startY, size, iconHeight, Icon.FORWARD,
                "Вперёд (Shift: 10 блоков)", () -> moveFacing(1, 0));
        button(center - size - gap - size / 2, startY + 15, size, iconHeight, Icon.LEFT,
                "Влево (Shift: 10 блоков)", () -> moveFacing(0, -1));
        button(center - size / 2, startY + 15, size, iconHeight, Icon.ROTATE,
                "Повернуть здание на 90 градусов", () -> {
                    var v = KingdomArchitectClient.current();
                    if (v != null) v.rotate();
                });
        button(center + size / 2 + gap, startY + 15, size, iconHeight, Icon.RIGHT,
                "Вправо (Shift: 10 блоков)", () -> moveFacing(0, 1));
        button(center - size / 2, startY + 30, size, iconHeight, Icon.BACK,
                "Назад (Shift: 10 блоков)", () -> moveFacing(-1, 0));

        final int elevationY = startY + 46;
        button(center - size - gap / 2, elevationY, size, iconHeight, Icon.UP,
                "Поднять модель на 1 блок (Shift: 10)", () -> moveVertical(1));
        button(center + gap / 2, elevationY, size, iconHeight, Icon.DOWN,
                "Опустить модель на 1 блок (Shift: 10)", () -> moveVertical(-1));

        // Three color-coded actions: show world, accept, cancel.
        final int footY = panelHeight - 20;
        final int actionHeight = 16;
        button(center - size - gap - size / 2, footY, size, actionHeight, Icon.EYE,
                "Осмотреть модель в мире; F8 возвращает кнопки", () -> {
                    didHide = true;
                    minecraft.setScreen(null);
                });
        button(center - size / 2, footY, size, actionHeight, Icon.CONFIRM,
                "Подтвердить расположение и создать строительный проект",
                () -> KingdomArchitectClient.decide(false));
        button(center + size / 2 + gap, footY, size, actionHeight, Icon.CANCEL,
                "Отменить без установки блоков", () -> KingdomArchitectClient.decide(true));
    }

    private void moveFacing(int forward, int right) {
        var view = KingdomArchitectClient.current();
        if (view == null || minecraft == null || minecraft.player == null) return;
        Direction facing = minecraft.player.getDirection();
        Direction rightDirection = facing.getClockWise();
        int count = Screen.hasShiftDown() ? 10 : 1;
        view.move(count * (forward * facing.getStepX() + right * rightDirection.getStepX()),
                  0,
                  count * (forward * facing.getStepZ() + right * rightDirection.getStepZ()));
    }

    private void moveVertical(int sign) {
        var view = KingdomArchitectClient.current();
        if (view != null) view.move(0, sign * (Screen.hasShiftDown() ? 10 : 1), 0);
    }

    public void error(String message) { error = message == null ? "" : message; }

    private void centered(GuiGraphics gui, String value, int y, int color) {
        gui.drawCenteredString(font, font.plainSubstrByWidth(value, panelWidth - 6),
                left + panelWidth / 2, top + y, color);
    }

    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        // Intentionally do not renderBackground(): the ghost stays visible.
        gui.fill(left, top, left + panelWidth, top + panelHeight, PANEL_BG);
        gui.fill(left, top, left + panelWidth, top + 23, PANEL_HEAD);
        gui.fill(left, top, left + panelWidth, top + 1, PANEL_EDGE);
        gui.fill(left, top, left + 1, top + panelHeight, PANEL_EDGE);
        gui.fill(left + panelWidth - 1, top, left + panelWidth,
                top + panelHeight, PANEL_EDGE);

        var view = KingdomArchitectClient.current();
        centered(gui, "АРХИТЕКТОР", 4, INK);
        if (view != null) {
            centered(gui, view.packet.title(), 14, SOFT);
            centered(gui, "X " + view.origin.getX() + "  Y " + view.origin.getY(), 25, INK);
            centered(gui, "Z " + view.origin.getZ() + "   " + view.rotation * 90 + "°", 34, SOFT);
            if (view.pending) centered(gui, "Проверка...", panelHeight - 31, 0xFFFFD18A);
        }
        if (!error.isBlank()) {
            // Compact first line; the full reason is also in the server chat.
            centered(gui, error, panelHeight - 31, 0xFFFF9999);
        }
        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void onClose() {
        // ESC hides controls but does not cancel the ghost or the server session.
        didHide = true;
        minecraft.setScreen(null);
    }

    private static final class ArchitectButton extends AbstractWidget {
        private final Icon symbol;
        private final Runnable press;

        ArchitectButton(int x, int y, int width, int height, Icon symbol,
                        Component text, Runnable press) {
            super(x, y, width, height, text);
            this.symbol = symbol;
            this.press = press;
        }

        @Override public void onClick(double mouseX, double mouseY) {
            var view = KingdomArchitectClient.current();
            if (view != null && !view.pending && this.active) press.run();
        }

        @Override protected void renderWidget(GuiGraphics gui, int mouseX,
                                              int mouseY, float partialTick) {
            var v = KingdomArchitectClient.current();
            active = v != null && !v.pending;
            int bg = switch (symbol) {
                case CONFIRM -> active ? 0xB7269849 : 0x77486A54;
                case CANCEL -> active ? 0xB7B63232 : 0x776C4848;
                default -> isHoveredOrFocused() ? 0xBB778B88 : 0x7953625D;
            };
            gui.fill(getX(), getY(), getX() + width, getY() + height, bg);
            int edge = isHoveredOrFocused() ? 0xFFEFE0B6 : 0x77D7D5C9;
            gui.fill(getX(), getY(), getX() + width, getY() + 1, edge);
            gui.fill(getX(), getY() + height - 1, getX() + width, getY() + height, edge);
            gui.fill(getX(), getY(), getX() + 1, getY() + height, edge);
            gui.fill(getX() + width - 1, getY(), getX() + width, getY() + height, edge);
            drawIcon(gui, getX() + width / 2, getY() + height / 2,
                    active ? 0xFFFFFFFF : 0xFFBCBDBA);
        }

        private void drawIcon(GuiGraphics gui, int x, int y, int color) {
            switch (symbol) {
                case FORWARD -> arrow(gui, x, y, 0, color);
                case RIGHT -> arrow(gui, x, y, 1, color);
                case BACK -> arrow(gui, x, y, 2, color);
                case LEFT -> arrow(gui, x, y, 3, color);
                case UP -> {
                    arrow(gui, x, y - 1, 0, color);
                    pixelLine(gui, x - 5, y + 5, x + 5, y + 5, color);
                }
                case DOWN -> {
                    arrow(gui, x, y + 1, 2, color);
                    pixelLine(gui, x - 5, y - 5, x + 5, y - 5, color);
                }
                case ROTATE -> {
                    pixelLine(gui, x - 4, y - 3, x + 2, y - 4, color);
                    pixelLine(gui, x - 4, y - 3, x - 5, y + 3, color);
                    pixelLine(gui, x - 5, y + 3, x + 3, y + 4, color);
                    pixelLine(gui, x + 3, y + 4, x + 5, y + 1, color);
                    pixelLine(gui, x + 2, y + 1, x + 5, y + 1, color);
                    pixelLine(gui, x + 5, y + 1, x + 5, y + 5, color);
                }
                case EYE -> {
                    pixelLine(gui, x - 5, y, x - 2, y - 3, color);
                    pixelLine(gui, x - 2, y - 3, x + 2, y - 3, color);
                    pixelLine(gui, x + 2, y - 3, x + 5, y, color);
                    pixelLine(gui, x + 5, y, x + 2, y + 3, color);
                    pixelLine(gui, x + 2, y + 3, x - 2, y + 3, color);
                    pixelLine(gui, x - 2, y + 3, x - 5, y, color);
                    gui.fill(x - 1, y - 1, x + 2, y + 2, color);
                }
                case CONFIRM -> {
                    pixelLine(gui, x - 5, y, x - 1, y + 4, color);
                    pixelLine(gui, x - 1, y + 4, x + 6, y - 4, color);
                    pixelLine(gui, x - 5, y - 1, x - 1, y + 3, color);
                }
                case CANCEL -> {
                    pixelLine(gui, x - 4, y - 4, x + 4, y + 4, color);
                    pixelLine(gui, x - 4, y + 4, x + 4, y - 4, color);
                }
            }
        }

        private static void arrow(GuiGraphics g, int x, int y, int dir, int color) {
            int[] dx = {0, 1, 0, -1};
            int[] dy = {-1, 0, 1, 0};
            int ux = dx[dir], uy = dy[dir];
            int vx = -uy, vy = ux;
            pixelLine(g, x - 3 * ux, y - 3 * uy, x + 5 * ux, y + 5 * uy, color);
            pixelLine(g, x + 5 * ux, y + 5 * uy,
                    x + 1 * ux + 3 * vx, y + 1 * uy + 3 * vy, color);
            pixelLine(g, x + 5 * ux, y + 5 * uy,
                    x + 1 * ux - 3 * vx, y + 1 * uy - 3 * vy, color);
        }

        private static void pixelLine(GuiGraphics g, int x0, int y0,
                                      int x1, int y1, int color) {
            int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
            int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
            int err = dx + dy;
            while (true) {
                g.fill(x0, y0, x0 + 1, y0 + 1, color);
                if (x0 == x1 && y0 == y1) break;
                int twice = 2 * err;
                if (twice >= dy) { err += dy; x0 += sx; }
                if (twice <= dx) { err += dx; y0 += sy; }
            }
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
