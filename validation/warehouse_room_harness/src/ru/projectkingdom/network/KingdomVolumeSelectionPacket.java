package ru.projectkingdom.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;

/** The isolated selection harness has no Forge networking runtime.
 * A visual-only packet is inert in the world/permission simulation.
 * This stub tracks calls to verify packet sending. */
public final class KingdomVolumeSelectionPacket {
    public static final int WAREHOUSE=1;
    public static final int ENTERPRISE=2;
    public static final int COPY=3;
    public static final int MINE=4;
    
    public static record Call(int mode, BlockPos first, boolean active, int depth) {}
    
    private static final List<Call> calls = new ArrayList<>();
    
    private KingdomVolumeSelectionPacket(){}
    
    public static void send(ServerPlayer viewer,int mode,BlockPos corner,boolean active){
        calls.add(new Call(mode, corner, active, 15));
    }
    
    public static void send(ServerPlayer viewer,int mode,BlockPos corner,boolean active,int depth){
        calls.add(new Call(mode, corner, active, depth));
    }
    
    public static List<Call> getCalls() { return new ArrayList<>(calls); }
    public static void clearCalls() { calls.clear(); }
}