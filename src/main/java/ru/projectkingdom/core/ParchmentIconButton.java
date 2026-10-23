package ru.projectkingdom.core;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Compact Minecraft-item tab, with readable tooltip and keyboard narration. */
public final class ParchmentIconButton extends AbstractButton {
    private final ItemStack icon;
    private final Runnable press;
    private final BooleanSupplier selected;

    public ParchmentIconButton(int x, int y, int width, int height, Item icon,
                               String label, Runnable press, BooleanSupplier selected) {
        super(x, y, width, height, KingdomFonts.readable(label));
        this.icon = new ItemStack(Objects.requireNonNull(icon, "icon"));
        this.press = Objects.requireNonNull(press, "press");
        this.selected = Objects.requireNonNull(selected, "selected");
        setTooltip(Tooltip.create(KingdomFonts.readable(label)));
    }

    @Override public void onPress() { press.run(); }

    @Override protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int border = selected.getAsBoolean() ? 0xFFB58A49 : 0xFF796042;
        int fill = selected.getAsBoolean() ? 0xFFEFCE8A
            : isHoveredOrFocused() ? 0xFFDBB77E : 0xFFC39D62;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, border);
        graphics.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, fill);
        graphics.renderItem(icon, getX() + (width - 16) / 2, getY() + (height - 16) / 2);
    }
}
