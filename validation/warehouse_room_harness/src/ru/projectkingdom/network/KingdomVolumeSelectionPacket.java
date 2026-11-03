package ru.projectkingdom.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** The isolated selection harness has no Forge networking runtime.
 * A visual-only packet is inert in the world/permission simulation. */
public final class KingdomVolumeSelectionPacket {
    public static final int WAREHOUSE=1;
    private KingdomVolumeSelectionPacket(){}
    public static void send(ServerPlayer viewer,int mode,BlockPos corner,boolean active){}
}
