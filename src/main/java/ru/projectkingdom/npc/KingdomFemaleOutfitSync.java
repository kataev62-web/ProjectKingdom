package ru.projectkingdom.npc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import ru.projectkingdom.network.KingdomFemaleOutfitPacket;
import ru.projectkingdom.network.KingdomNetwork;

/**
 * Keeps the vanilla Villager and its AI. Only registered adult female workers
 * receive a tiny client-side clothing overlay. No extra entities or block ticks.
 */
public final class KingdomFemaleOutfitSync {
    private final Map<UUID, Boolean> lastBroadcast = new HashMap<>();
    private long ticks;

    public KingdomFemaleOutfitSync() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    public static boolean eligible(KingdomNpcData.Worker worker) {
        return "female".equals(worker.gender) && worker.ageYears >= 18;
    }

    @SubscribeEvent
    public void onTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof Villager villager)) {
            return;
        }
        for (var worker : KingdomNpcData.get(player.getServer()).all()) {
            if (worker.entityId.equals(villager.getUUID())) {
                KingdomNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new KingdomFemaleOutfitPacket(worker.entityId, eligible(worker)));
                return;
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % 80 != 0) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        var workers = KingdomNpcData.get(server);
        lastBroadcast.keySet().removeIf(uuid ->
                workers.all().stream().noneMatch(worker -> worker.entityId.equals(uuid)));
        for (var worker : workers.all()) {
            var dimensionId = net.minecraft.resources.ResourceLocation.tryParse(worker.dimension);
            if (dimensionId == null) continue;
            var level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, dimensionId));
            if (level == null || !(level.getEntity(worker.entityId) instanceof Villager villager)) {
                lastBroadcast.remove(worker.entityId);
                continue;
            }
            boolean enabled = eligible(worker);
            if (enabled == lastBroadcast.getOrDefault(worker.entityId, false)
                    && lastBroadcast.containsKey(worker.entityId)) continue;
            lastBroadcast.put(worker.entityId, enabled);
            KingdomNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> villager),
                    new KingdomFemaleOutfitPacket(worker.entityId, enabled));
        }
    }
}
