package ru.projectkingdom.npc;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

/** Home assignment uses MCA's own bed/POI transaction before saving Kingdom coordinates. */
public final class KingdomResidentHome {
    private KingdomResidentHome() {}

    public record Result(boolean assigned, String message) {}

    public static Result assign(ServerPlayer player, KingdomNpcData.Worker worker) {
        if (player == null || worker == null) return new Result(false, "Player and resident required");
        var level = player.serverLevel();
        if (!worker.dimension.equals(level.dimension().location().toString()))
            return new Result(false, "Home and resident must be in the same dimension");
        if (!(level.getEntity(worker.entityId) instanceof Villager resident) || !resident.isAlive())
            return new Result(false, "Load the resident before assigning a home");
        if (!KingdomMcaAdapter.isResident(resident)) {
            saveCoordinates(worker, player.blockPosition());
            return new Result(true, "Home saved for #" + worker.id);
        }
        var before = resident.getBrain().getMemory(MemoryModuleType.HOME);
        if (syncExistingHome(worker, before, level.dimension(), player.blockPosition(),
                KingdomResidentRestController.inspectBed(level,resident)
                    == KingdomResidentRestController.Status.SEEKING_BED))
            return new Result(true, "Existing MCA bed retained for #" + worker.id);
        try {
            // This is the real MCA 7.7.1 Residency transaction. It finds a free
            // nearby bed POI, claims it, releases the previous claim and sets
            // HOME + MCA FORCED_HOME. A rejection leaves the old HOME intact.
            Object residency = resident.getClass().getMethod("getResidency").invoke(resident);
            residency.getClass().getMethod("setHome", ServerPlayer.class).invoke(residency, player);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return new Result(false, "MCA home registration unavailable; existing home kept");
        }
        var after = resident.getBrain().getMemory(MemoryModuleType.HOME);
        boolean validBed = KingdomResidentRestController.inspectBed(level, resident)
                == KingdomResidentRestController.Status.SEEKING_BED;
        if (!commitRegisteredHome(worker, before, after, level.dimension(), player.blockPosition(), validBed))
            return new Result(false, "MCA did not register a new free bed within 8 blocks; existing home kept");
        return new Result(true, "MCA bed registered for #" + worker.id + ": " + after.get().pos().toShortString());
    }

    static boolean syncExistingHome(KingdomNpcData.Worker worker, Optional<GlobalPos> home,
                                    ResourceKey<Level> dimension, BlockPos playerPosition, boolean validBed) {
        if (!validBed || home.isEmpty() || !home.get().dimension().equals(dimension)
                || home.get().pos().distSqr(playerPosition) > 64) return false;
        saveCoordinates(worker, home.get().pos());
        return true;
    }

    static boolean commitRegisteredHome(KingdomNpcData.Worker worker, Optional<GlobalPos> before,
                                        Optional<GlobalPos> after, ResourceKey<Level> dimension,
                                        BlockPos playerPosition, boolean validBed) {
        if (!validBed || after.isEmpty() || after.equals(before)
                || !after.get().dimension().equals(dimension)
                || after.get().pos().distSqr(playerPosition) > 64) return false;
        saveCoordinates(worker, after.get().pos());
        return true;
    }

    private static void saveCoordinates(KingdomNpcData.Worker worker, BlockPos position) {
        worker.homeX = position.getX();
        worker.homeY = position.getY();
        worker.homeZ = position.getZ();
        worker.hasHome = true;
    }
}
