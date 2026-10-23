package ru.projectkingdom.core;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Keeps Project Kingdom text on Minecraft's proven Unicode-capable fallback font. */
public final class KingdomFonts {
    private KingdomFonts() {}
    public static MutableComponent readable(String text) {
        return Component.literal(text);
    }
    public static MutableComponent readable(Component component) {
        return component.copy();
    }
}
