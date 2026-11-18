package net.minecraftforge.eventbus.api;

public interface IEventBus {
    void register(Object target);
    void unregister(Object target);
    void post(Object event);
    default void start() {}
}