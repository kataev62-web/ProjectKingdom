package ru.projectkingdom.core;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/** Font styling scoped to Project Kingdom components only. */
public final class KingdomFonts {
    public static final ResourceLocation READABLE = new ResourceLocation("kingdomnpc", "readable");
    private KingdomFonts() {}
    public static MutableComponent readable(String text) {
        return Component.literal(text).withStyle(style -> style.withFont(READABLE));
    }
    public static MutableComponent readable(Component component) {
        return component.copy().withStyle(style -> style.withFont(READABLE));
    }
}
