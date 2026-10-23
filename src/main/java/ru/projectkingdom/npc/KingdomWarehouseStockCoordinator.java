package ru.projectkingdom.npc;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import ru.projectkingdom.enterprise.EnterpriseBindingRole;

/**
 * Stage 2: the single read-only inventory projection for workshop planning,
 * procurement and demand listings. Never loads chunks or changes items/coins.
 * Unknown (unloaded/disputed/pending-transfer) storage stays incomplete.
 *
 * A worker's own running cycle is excluded when deciding whether it may
 * finish; other workers' future output is reserved against the same maximum.
 */
public final class KingdomWarehouseStockCoordinator {
    private KingdomWarehouseStockCoordinator() {}

    public static EnterpriseStockPolicy.StockState observe(ServerLevel level, UUID enterprise,
            String itemId, EnterpriseBindingRole role, BlockPos workerChest, UUID currentWorker) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(enterprise, "enterprise");
        Objects.requireNonNull(role, "role");
        if (role != EnterpriseBindingRole.RAW_STORAGE
                && role != EnterpriseBindingRole.FINISHED_STORAGE) {
            throw new IllegalArgumentException("Only physical warehouse roles are supported");
        }
        var physical = KingdomEnterpriseStock.inspect(level, enterprise, itemId, role, workerChest);
        if (!physical.complete()) return physical.state(0);
        int underway = role == EnterpriseBindingRole.FINISHED_STORAGE
                ? KingdomNpcWorkshopData.get(level.getServer())
                    .reservedProduction(enterprise, itemId, currentWorker)
                : 0;
        return physical.state(underway);
    }

    public static EnterpriseStockPolicy.StockState finishedForWorker(ServerLevel level,
            UUID enterprise, String itemId, BlockPos workerChest, UUID workerId) {
        Objects.requireNonNull(workerId, "workerId");
        return observe(level, enterprise, itemId, EnterpriseBindingRole.FINISHED_STORAGE,
                workerChest, workerId);
    }

    public static EnterpriseStockPolicy.StockState raw(ServerLevel level,
            UUID enterprise, String itemId, BlockPos workerChest) {
        return observe(level, enterprise, itemId, EnterpriseBindingRole.RAW_STORAGE,
                workerChest, null);
    }

    /**
     * Planning only. The caller must still validate recipe, physical source,
     * permissions, destination capacity and the actual delivery transaction.
     */
    public static int needed(EnterpriseStockPolicy.Thresholds limits,
            EnterpriseStockPolicy.StockState state) {
        return EnterpriseStockPolicy.reorderQuantity(limits, state);
    }
}
