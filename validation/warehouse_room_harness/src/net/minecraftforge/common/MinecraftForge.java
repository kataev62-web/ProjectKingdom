package net.minecraftforge.common;

import net.minecraftforge.eventbus.api.IEventBus;

public class MinecraftForge {
    public static final IEventBus EVENT_BUS = new IEventBus() {
        public void register(Object target) {}
        public void unregister(Object target) {}
        public void post(Object event) {}
    };
    
    public interface IEventBus {
        void register(Object target);
        void unregister(Object target);
        void post(Object event);
    }
}