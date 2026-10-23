package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * Ancient-Warfare-inspired builder: find a valid worksite, walk to its material
 * depot and place at most one physical block per tick cycle. Uses precisely the
 * same survival placement engine as the player's crafted builder's hammer.
 */
public final class KingdomNpcBuilder {
    private KingdomNpcBuilder() {}

    public static void tick(MinecraftServer server) {
        var sites = KingdomConstructionData.get(server);
        var workers = KingdomNpcData.get(server);
        for (var site : sites.all()) {
            if (site.status() != KingdomConstructionData.Status.WAITING_MATERIALS
                    && site.status() != KingdomConstructionData.Status.BUILDING) continue;
            if (KingdomBuildingTypes.type(site.design()) == null) continue;

            ServerLevel level = null;
            for (ServerLevel candidate : server.getAllLevels()) {
                if (candidate.dimension().location().toString().equals(site.dimension())) {
                    level = candidate;
                    break;
                }
            }
            if (level == null) continue;
            BlockPos marker = new BlockPos(site.x() - 2, site.y(), site.z() - 2);
            if (!KingdomConstructionMarkers.isMarker(level, marker)) continue;

            Villager builder = null;
            for (var worker : workers.all()) {
                if (!site.owner().equals(worker.employer)
                        || !site.dimension().equals(worker.dimension)
                        || !"builder".equals(worker.job)
                        || !KingdomNpcDuty.mayExecute(worker.job, worker.order,
                            worker.autonomousIntent, KingdomNpcWorkday.employed(server, worker)))
                    continue;
                var entity = level.getEntity(worker.entityId);
                if (entity instanceof Villager villager && villager.isAlive()
                        && villager.blockPosition().distSqr(marker) <= 48 * 48) {
                    builder = villager;
                    break;
                }
            }
            if (builder == null) continue;
            if (builder.isNoAi()) builder.setNoAi(false);
            if (builder.distanceToSqr(marker.getX() + 0.5, marker.getY(),
                    marker.getZ() + 0.5) > 16.0) {
                builder.getNavigation().moveTo(marker.getX() + 0.5,
                    marker.getY(), marker.getZ() + 0.5, 0.8);
                continue;
            }
            builder.getNavigation().stop();
            var result = KingdomConstructionWork.placeNext(server, level, site);
            if (result.result() == KingdomConstructionWork.Result.NO_MATERIALS
                    || result.result() == KingdomConstructionWork.Result.NO_DEPOT)
                sites.waiting(site.id());
        }
    }
}
