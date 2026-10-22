package ru.projectkingdom.npc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;
import java.util.List;

/**
 * Safe, optional MineColonies compatibility discovery layer.
 * Read-only: we never convert, hire, freeze or replace an upstream citizen.
 * This does not depend on MineColonies classes, so Kingdom NPC remains standalone.
 */
public final class MineColoniesBridge {
    private MineColoniesBridge() {}
    public static boolean installed() { return ModList.get().isLoaded("minecolonies"); }
    public static boolean isCitizen(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id != null && "minecolonies".equals(id.getNamespace()) &&
               ("citizen".equals(id.getPath()) || id.getPath().endsWith("_citizen"));
    }
    public static int nearbyCitizenCount(ServerPlayer player, double radius) {
        return (int) player.serverLevel().getEntities(player,
            player.getBoundingBox().inflate(radius), MineColoniesBridge::isCitizen).size();
    }
    public static void reportNearby(ServerPlayer player) {
        if (!installed()) {
            player.sendSystemMessage(Component.literal("[Королевство] MineColonies не установлен. Текущие Kingdom NPC работают отдельно."));
            return;
        }
        List<Entity> nearby = player.serverLevel().getEntities(player,
            player.getBoundingBox().inflate(24), MineColoniesBridge::isCitizen);
        player.sendSystemMessage(Component.literal("[Королевство] Жители MineColonies в радиусе 24 блоков: " + nearby.size()));
        for (int i=0; i<Math.min(8,nearby.size()); i++) {
            Entity entity=nearby.get(i);
            player.sendSystemMessage(Component.literal("• " + entity.getDisplayName().getString()
                + " [" + entity.blockPosition().toShortString() + "]"));
        }
        player.sendSystemMessage(Component.literal("[Королевство] Интеграция пока только читает данные. Управление работниками MineColonies не изменено."));
    }
}
