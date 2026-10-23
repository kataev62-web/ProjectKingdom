package ru.projectkingdom.npc;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import ru.projectkingdom.network.KingdomNetwork;
import ru.projectkingdom.network.KingdomNpcAppearancePacket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Sync only existing registered Kingdom NPC UUIDs; never change their AI or ownership. */
public final class KingdomNpcAppearanceSync {
    private final Map<UUID, String> delivered = new HashMap<>();
    private long ticks;
    public KingdomNpcAppearanceSync() { MinecraftForge.EVENT_BUS.register(this); }
    private static String choose(KingdomNpcData.Worker w) {
        return KingdomNpcAppearanceCatalog.select(w.gender, w.ageYears, w.job, w.appearanceId, w.entityId.hashCode());
    }
    @SubscribeEvent
    public void onTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof Villager npc)) return;
        for (var w : KingdomNpcData.get(player.getServer()).all()) {
            if (!w.entityId.equals(npc.getUUID())) continue;
            KingdomNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new KingdomNpcAppearancePacket(npc.getUUID(), choose(w)));
            return;
        }
    }
    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % 100 != 0) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        var workers = KingdomNpcData.get(server);
        for (var w : workers.all()) {
            var appearance = choose(w);
            if (appearance.equals(delivered.get(w.entityId))) continue;
            ResourceLocation dimension = ResourceLocation.tryParse(w.dimension);
            if (dimension == null) continue;
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level == null || !(level.getEntity(w.entityId) instanceof Villager npc)) continue;
            delivered.put(w.entityId, appearance);
            KingdomNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> npc),
                    new KingdomNpcAppearancePacket(w.entityId, appearance));
        }
    }
}
