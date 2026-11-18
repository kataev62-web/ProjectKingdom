package net.minecraftforge.event.entity.player;

public class PlayerEvent {
    public static class PlayerLoggedOutEvent extends PlayerEvent {
        private final net.minecraft.world.entity.player.Player entity;
        public PlayerLoggedOutEvent(net.minecraft.world.entity.player.Player entity) { this.entity = entity; }
        public net.minecraft.world.entity.player.Player getEntity() { return entity; }
    }
}