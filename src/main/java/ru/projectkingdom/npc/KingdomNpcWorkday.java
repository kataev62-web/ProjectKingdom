package ru.projectkingdom.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Shared workday adapter for every Kingdom profession.
 * Scheduling is generic; only existing profession executors may mutate the world.
 * All destinations are in already-loaded chunks and authorized territory.
 */
public final class KingdomNpcWorkday {
    private KingdomNpcWorkday() {}

    /** Validate a live enterprise payroll contract, not merely an employer UUID. */
    public static boolean employed(MinecraftServer server, KingdomNpcData.Worker worker) {
        if (worker.contractPaused || worker.ageYears < 18) return false;
        if (KingdomNpcFarmOwner.ownedFarm(server,worker)!=null) return true;
        if (worker.employer == null) return false;
        var enterprises = KingdomEnterpriseData.get(server);
        for (var contract : KingdomEnterprisePayrollData.get(server).all()) {
            if (!contract.active() || !contract.worker().equals(worker.entityId)) continue;
            if (!worker.workplaceId.isBlank() && !worker.workplaceId.equals(contract.enterprise().toString()))
                continue;
            var firm = enterprises.find(contract.enterprise());
            if (firm != null && worker.employer.equals(firm.owner())) return true;
        }
        return false;
    }

    /** Does an existing site allow this profession to join the common shift? */
    public static boolean hasAssignment(MinecraftServer server, ServerLevel level,
                                        KingdomNpcData.Worker worker) {
        if (!employed(server, worker)) return false;
        var kind = KingdomNpcDuty.kind(worker.job);
        // Couriers use physical pickup/delivery coordinates from assigned paid orders;
        // they do not require a fixed personal raw/finished warehouse.
        if (kind == KingdomNpcDuty.Kind.DELIVERY) return true;
        if (site(server, level, worker) == null) return false;
        return switch (kind) {
            case NONE, DELIVERY -> false;
            case FIELD -> worker.hasSource && worker.hasDestination
                && (!"farmer".equals(worker.job) || KingdomFarmWorker.site(level,worker)!=null)
                && (!"breeder".equals(worker.job) || KingdomLivestockWork.workSite(level,worker)!=null);
            case WORKSHOP -> worker.hasSource && worker.hasDestination && worker.hasWorkstation
                && (KingdomNpcWorkshopData.get(server).plan(worker.entityId) != null
                    || KingdomTfcQuernWork.isQuern(level, worker)
                    || KingdomTfcGrillWork.isGrill(level, worker)
                    || KingdomTfcPotWork.isPot(level, worker)
                    || KingdomTfcLoomWork.isLoom(level, worker));
            case CONSTRUCTION, SERVICE -> true;
        };
    }

    /**
     * Work location for a trade. This never creates a workstation, teleports an
     * entity or loads a chunk. Construction takes its target from a real site.
     */
    public static BlockPos site(MinecraftServer server, ServerLevel level,
                                KingdomNpcData.Worker worker) {
        if (!level.dimension().location().toString().equals(worker.dimension)
                || KingdomNpcFarmOwner.workOwner(level,worker) == null) return null;
        var land = KingdomTerritories.get(server);
        var kind = KingdomNpcDuty.kind(worker.job);
        if ("breeder".equals(worker.job)) {
            BlockPos pen=KingdomLivestockWork.workSite(level,worker);
            if(pen!=null)return pen;
        }
        if ("farmer".equals(worker.job)) {
            BlockPos farmPlot=KingdomFarmWorker.site(level,worker);
            if(farmPlot!=null)return farmPlot;
        }
        if (kind == KingdomNpcDuty.Kind.CONSTRUCTION) {
            BlockPos best = null;
            double distance = Double.MAX_VALUE;
            for (var project : KingdomConstructionData.get(server).all()) {
                if (!project.owner().equals(worker.employer)
                        || !project.dimension().equals(worker.dimension)
                        || (project.status() != KingdomConstructionData.Status.BUILDING
                            && project.status() != KingdomConstructionData.Status.WAITING_MATERIALS))
                    continue;
                var marker = new BlockPos(project.x() - 2, project.y(), project.z() - 2);
                if (KingdomConstructionPlans.resolve(server, project.design()) == null
                        || !KingdomConstructionMarkers.isMarker(level, marker))
                    continue;
                double d = marker.distSqr(new BlockPos(worker.hasHome ? worker.homeX : project.x(),
                    worker.hasHome ? worker.homeY : project.y(),
                    worker.hasHome ? worker.homeZ : project.z()));
                if (d < distance) { distance = d; best = marker; }
            }
            return best;
        }

        if (worker.hasWorkstation) {
            var pos = new BlockPos(worker.workX, worker.workY, worker.workZ);
            if (level.hasChunkAt(pos) && !level.getBlockState(pos).isAir()
                    && land.canUse(level, pos, worker.employer)) return pos;
        }
        // A field worker or courier may use the authorized supply depot as a muster point.
        if ((kind == KingdomNpcDuty.Kind.FIELD || kind == KingdomNpcDuty.Kind.DELIVERY)
                && worker.hasSource) {
            var depot = new BlockPos(worker.sourceX, worker.sourceY, worker.sourceZ);
            if (level.hasChunkAt(depot) && land.usable(level, depot, worker.employer, "raw"))
                return depot;
        }
        return null;
    }
}
