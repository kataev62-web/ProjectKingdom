package net.minecraftforge.network;

import java.util.function.Supplier;

public class NetworkEvent {
    private final Supplier<Context> contextSupplier;
    
    public NetworkEvent(Supplier<Context> contextSupplier) {
        this.contextSupplier = contextSupplier;
    }
    
    public Context get() { return contextSupplier.get(); }
    
    public static class Context {
        private final NetworkDirection direction;
        private final net.minecraft.server.level.ServerPlayer sender;
        
        public Context(NetworkDirection direction, net.minecraft.server.level.ServerPlayer sender) {
            this.direction = direction;
            this.sender = sender;
        }
        
        public NetworkDirection getDirection() { return direction; }
        public net.minecraft.server.level.ServerPlayer getSender() { return sender; }
        
        public void enqueueWork(Runnable work) { work.run(); }
    }
}