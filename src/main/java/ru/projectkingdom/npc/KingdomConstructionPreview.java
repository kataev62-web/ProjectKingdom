package ru.projectkingdom.npc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Low-bandwidth 3D construction outline for the blueprint item.
 * Intentionally server-side and per-player; no permanent hologram entities,
 * no ghost blocks placed in the real world and no forced chunk loads.
 */
public final class KingdomConstructionPreview {
    private KingdomConstructionPreview() {}

    private static final int MAX_MARKERS = 144;
    private static final long COOLDOWN_TICKS = 20;
    private static final Map<UUID, Long> LAST = new HashMap<>();

    public static void clear(UUID player) {
        LAST.remove(player);
    }

    public static int show(ServerPlayer player, KingdomConstructionData.Project site) {
        var server = player.getServer();
        var level = player.serverLevel();
        if (server == null || site == null
                || !KingdomConstructionData.get(server).mayAssist(site.id(), player.getUUID())
                || !site.dimension().equals(level.dimension().location().toString()))
            return 0;
        var design = KingdomConstructionPlans.resolve(server, site.design());
        if (design == null) return 0;
        long now = server.getTickCount();
        Long old = LAST.get(player.getUUID());
        if (old != null && now >= old && now - old < COOLDOWN_TICKS) return 0;
        LAST.put(player.getUUID(), now);

        int start = KingdomConstructionData.get(server).progress(site.id());
        var blocks = design.blocks();
        if (start >= blocks.size()) return 0;
        int remaining = blocks.size() - start;
        int stride = Math.max(1, (remaining + MAX_MARKERS - 1) / MAX_MARKERS);
        int sent = 0;
        for (int i = start; i < blocks.size() && sent < MAX_MARKERS; i += stride) {
            var cell = blocks.get(i);
            BlockPos target = new BlockPos(site.x() + cell.x(),
                site.y() + cell.y(), site.z() + cell.z());
            if (!level.hasChunkAt(target)
                    || player.distanceToSqr(target.getX() + 0.5,
                        target.getY() + 0.5, target.getZ() + 0.5) > 80.0 * 80.0)
                continue;
            if (level.getBlockState(target).equals(KingdomBlockStateCodec.parse(cell.material())))
                continue;
            level.sendParticles(player, ParticleTypes.END_ROD, false,
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                1, 0.01, 0.01, 0.01, 0.0);
            sent++;
        }
        // Highlight the very next step separately, even for very large structures.
        var next = KingdomConstructionWork.next(server, site);
        if (next.target() != null && level.hasChunkAt(next.target()))
            level.sendParticles(player, ParticleTypes.FLAME, false,
                next.target().getX() + .5, next.target().getY() + .5,
                next.target().getZ() + .5, 8, .28, .28, .28, .004);
        return sent;
    }
}
