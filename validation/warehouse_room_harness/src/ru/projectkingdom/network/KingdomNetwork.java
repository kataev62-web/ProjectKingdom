package ru.projectkingdom.network;

import net.minecraftforge.network.NetworkChannel;
import net.minecraftforge.network.PacketDistributor;

public class KingdomNetwork {
    public static final NetworkChannel CHANNEL = new NetworkChannel() {
        public <MSG> void send(PacketDistributor.PacketTarget target, MSG message) {
            // No-op in test
        }
    };
    
    public static void register() {}
}