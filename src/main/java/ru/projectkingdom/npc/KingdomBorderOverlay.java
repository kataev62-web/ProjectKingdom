package ru.projectkingdom.npc;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Optional in-world claim outlines; no dependency on client map mods. */
@Mod.EventBusSubscriber(modid = KingdomNpcMod.MOD_ID)
public final class KingdomBorderOverlay {
    private static final Set<UUID> ENABLED = ConcurrentHashMap.newKeySet();
    private KingdomBorderOverlay() {}
    public static boolean toggle(ServerPlayer player) {
        if (!ENABLED.add(player.getUUID())) { ENABLED.remove(player.getUUID()); return false; }
        return true;
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (!ENABLED.contains(player.getUUID()) || player.tickCount % 20 != 0) return;
        var civics = KingdomCivicsData.get(player.getServer());
        var legacy = KingdomTerritories.get(player.getServer());
        Vec3 eye = player.position();
        var town = civics.townOf(player.getUUID());
        int originX = player.blockPosition().getX() >> 4;
        int originZ = player.blockPosition().getZ() >> 4;
        double y = Math.floor(eye.y) + 0.3;
        // Query the live registry, including older claims not yet migrated to city plots.
        for (int cx = originX - 3; cx <= originX + 3; cx++) {
            for (int cz = originZ - 3; cz <= originZ + 3; cz++) {
                var probe = new net.minecraft.core.BlockPos(cx * 16 + 8, player.blockPosition().getY(), cz * 16 + 8);
                var plot = civics.plot(player.serverLevel(), probe);
                var owner = legacy.owner(player.serverLevel(), probe);
                if (plot == null && owner == null) continue;
                boolean own = plot != null ? town != null && town.id().equals(plot.town())
                        : player.getUUID().equals(owner);
                Vector3f color = own ? new Vector3f(0.1f, 0.95f, 0.25f) : new Vector3f(0.95f, 0.15f, 0.15f);
                var particle = new DustParticleOptions(color, 0.8f);
                // Draw exterior edges only: adjacent chunks belonging to the same city are continuous.
                for (int side = 0; side < 4; side++) {
                    int nx = cx + (side == 0 ? -1 : side == 1 ? 1 : 0);
                    int nz = cz + (side == 2 ? -1 : side == 3 ? 1 : 0);
                    var neighborPos = new net.minecraft.core.BlockPos(nx * 16 + 8, probe.getY(), nz * 16 + 8);
                    var neighbor = civics.plot(player.serverLevel(), neighborPos);
                    var neighborOwner = legacy.owner(player.serverLevel(), neighborPos);
                    boolean same = plot != null ? neighbor != null && plot.town().equals(neighbor.town())
                            : neighbor == null && owner != null && owner.equals(neighborOwner);
                    if (same) continue;
                    for (int i = 0; i <= 16; i += 2) {
                        double x = cx * 16 + (side == 0 ? 0 : side == 1 ? 16 : i);
                        double z = cz * 16 + (side == 2 ? 0 : side == 3 ? 16 : i);
                        player.serverLevel().sendParticles(player, particle, false, x, y, z, 1, 0, 0, 0, 0);
                    }
                }
            }
        }
    }
}
