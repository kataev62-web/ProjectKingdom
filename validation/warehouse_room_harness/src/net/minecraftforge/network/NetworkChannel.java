package net.minecraftforge.network;

public class NetworkChannel {
    public <MSG> void send(PacketDistributor.PacketTarget target, MSG message) {}
}