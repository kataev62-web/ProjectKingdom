package ru.projectkingdom.npc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
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
 * Keep villager entities, saves and AI unchanged while syncing player-model
 * appearance for Kingdom NPCs. No player entities are created.
 */
public final class KingdomFemaleOutfitSync {
    private final Map<UUID, Byte> lastBroadcast = new HashMap<>();
    private long ticks;

    public KingdomFemaleOutfitSync() { MinecraftForge.EVENT_BUS.register(this); }

    public static boolean eligible(KingdomNpcData.Worker worker) {
        return "female".equals(worker.gender) && worker.ageYears >= 18;
    }

    /** 0 = vanilla (not Kingdom), 1 = human male/unspecified, 2 = adult female, 3 = young female. */
    public static byte appearance(KingdomNpcData.Worker worker) {
        if (worker == null) return 0;
        if ("female".equals(worker.gender)) return (byte)(worker.ageYears >= 18 ? 2 : 3);
        return 1;
    }

    /** Push edited gender/age to tracking clients without waiting for periodic sync. */
    public static void notifyChanged(MinecraftServer server, KingdomNpcData.Worker worker) {
        if (server == null || worker == null) return;
        Villager entity = resolve(server, worker);
        if (entity != null) {
            KingdomNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity),
                    new KingdomFemaleOutfitPacket(worker.entityId, appearance(worker)));
        }
    }

    private static Villager resolve(MinecraftServer server, KingdomNpcData.Worker worker) {
        ResourceLocation id = ResourceLocation.tryParse(worker.dimension);
        if (id == null) return null;
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) return null;
        return level.getEntity(worker.entityId) instanceof Villager villager ? villager : null;
    }

    @SubscribeEvent
    public void onTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof Villager villager)) return;
        for (var worker : KingdomNpcData.get(player.getServer()).all()) {
            if (worker.entityId.equals(villager.getUUID())) {
                KingdomNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new KingdomFemaleOutfitPacket(worker.entityId, appearance(worker)));
                return;
            }
        }
        // Explicitly revoke stale state when same UUID is no longer a Kingdom worker.
        KingdomNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new KingdomFemaleOutfitPacket(villager.getUUID(), (byte)0));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % 80 != 0) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        var workers = KingdomNpcData.get(server);
        Set<UUID> known = new HashSet<>();
        for (var worker : workers.all()) {
            known.add(worker.entityId);
            Villager villager = resolve(server, worker);
            if (villager == null) {
                lastBroadcast.remove(worker.entityId);
                continue;
            }
            byte state = appearance(worker);
            if (lastBroadcast.getOrDefault(worker.entityId, (byte)0) == state
                    && lastBroadcast.containsKey(worker.entityId)) continue;
            lastBroadcast.put(worker.entityId, state);
            KingdomNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> villager),
                    new KingdomFemaleOutfitPacket(worker.entityId, state));
        }
        lastBroadcast.keySet().retainAll(known);
    }
}
