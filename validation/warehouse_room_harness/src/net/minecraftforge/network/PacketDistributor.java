package net.minecraftforge.network;

import java.util.function.Supplier;

public class PacketDistributor {
    public interface PacketTarget {}
    
    public static class Player implements PacketTarget {
        private final Supplier<net.minecraft.server.level.ServerPlayer> supplier;
        public Player(Supplier<net.minecraft.server.level.ServerPlayer> supplier) { this.supplier = supplier; }
    }
    
    public static Player PLAYER = null; // Not used in tests
}