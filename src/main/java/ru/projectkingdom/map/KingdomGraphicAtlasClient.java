package ru.projectkingdom.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Client-only atlas lifecycle, isolated from dedicated server packet classes. */
@OnlyIn(Dist.CLIENT)
public final class KingdomGraphicAtlasClient {
    private KingdomGraphicAtlasClient() {}
    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        // Properly close any server container before switching to a client Screen.
        if (minecraft.screen instanceof AbstractContainerScreen<?>) minecraft.player.closeContainer();
        minecraft.setScreen(new KingdomGraphicAtlasScreen());
    }
    public static void accept(KingdomGraphicAtlasData.Snapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof KingdomGraphicAtlasScreen atlas) atlas.accept(snapshot);
    }
}
