package ru.projectkingdom.npc;

import java.io.IOException;
import java.util.UUID;
import net.minecraft.world.Container;

/** Harness-only stub. The real Minecraft adapter is compiled by Forge, not javac. */
public final class TradeMinecraftContainerView {
    private TradeMinecraftContainerView() {}
    public static TradeAtomicEconomy.Account snapshot(UUID id, long copper, Container container) throws IOException {
        throw new UnsupportedOperationException("Requires Minecraft Forge runtime");
    }
}
