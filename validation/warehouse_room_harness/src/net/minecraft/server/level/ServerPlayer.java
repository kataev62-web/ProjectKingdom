package net.minecraft.server.level;

import net.minecraft.core.*;
import net.minecraft.server.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import java.util.*;

public class ServerPlayer extends Player {
    public Object containerMenu;
    private final UUID id;
    private final MinecraftServer server;

    public ServerPlayer(MinecraftServer s, UUID id) {
        server = s;
        this.id = id;
    }

    public UUID getUUID() { return id; }
    public MinecraftServer getServer() { return server; }
    public ServerLevel serverLevel() { return server.world; }
    public ServerLevel level() { return server.world; }
    public BlockPos blockPosition() { return pos; }
    public void sendSystemMessage(Component component) {}
}