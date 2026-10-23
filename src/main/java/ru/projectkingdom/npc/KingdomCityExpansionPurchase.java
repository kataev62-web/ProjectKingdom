package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import ru.projectkingdom.administration.KingdomAdministrationData;

/**
 * Server-thread coordinator for the two persistent ledgers.
 *
 * CivicsData owns the authoritative city balance and territorial plots in ONE
 * SavedData record; AdministrationData owns the physical treasury reserve.
 * An idempotent debit ID + committed civic receipt allow reconciliation when
 * the two files were persisted at different points of a crash.
 */
public final class KingdomCityExpansionPurchase {
    public enum Result { PURCHASED, NOT_FOUNDER, NOT_AT_TOWN_HALL, INVALID_PLAN,
        LAND_CONFLICT, INSUFFICIENT_FUNDS, LEDGER_ERROR, RECONCILIATION_REQUIRED }
    private KingdomCityExpansionPurchase() {}

    private static boolean founder(ServerPlayer player, KingdomCivicsData.Town town) {
        return player.getUUID().equals(
                KingdomAdministrationData.get(player.getServer()).founder(town.id()));
    }

    private static boolean nearOwnTownHall(ServerPlayer player, KingdomCivicsData.Town town) {
        UUID founder = KingdomAdministrationData.get(player.getServer()).founder(town.id());
        if (founder == null) return false;
        String dim = player.serverLevel().dimension().location().toString();
        for (var project : KingdomConstructionData.get(player.getServer()).all()) {
            if (project.status() != KingdomConstructionData.Status.COMPLETED
                    || !"TOWN_HALL".equals(project.design())
                    || !dim.equals(project.dimension())
                    || !founder.equals(project.owner())) continue;
            double dx = player.getX() - (project.x() + 6.5);
            double dy = player.getY() - (project.y() + 1.0);
            double dz = player.getZ() - (project.z() + 7.5);
            if (dx * dx + dy * dy + dz * dz <= 256.0) return true;
        }
        return false;
    }

    /**
     * Reconciles crash-interrupted expansion payments before another purchase.
     * Fails closed on any inconsistency instead of paying from an unbacked reserve.
     */
    public static boolean reconcile(ServerPlayer player, UUID cityId) {
        var civics = KingdomCivicsData.get(player.getServer());
        var admin = KingdomAdministrationData.get(player.getServer());
        for (var debit : admin.expansionDebits(cityId)) {
            var receipt = civics.expansionReceipt(debit.id());
            if (receipt == null) {
                if (!admin.refundExpansion(debit.id())) return false;
            } else if (debit.refunded() || !receipt.townId().equals(cityId)
                    || receipt.copper() != debit.copper()) {
                return false;
            }
        }
        for (var receipt : civics.expansionReceipts(cityId)) {
            var debit = admin.expansionDebit(receipt.id());
            if (debit == null && !admin.debitExpansion(cityId, receipt.id(), receipt.copper()))
                return false;
        }
        return true;
    }

    public static Result purchase(ServerPlayer player) {
        if (player == null || player.getServer() == null
                || !player.getServer().isSameThread()) return Result.LEDGER_ERROR;
        var civics = KingdomCivicsData.get(player.getServer());
        var town = civics.townOf(player.getUUID());
        if (town == null || !founder(player, town)) return Result.NOT_FOUNDER;
        if (!nearOwnTownHall(player, town)) return Result.NOT_AT_TOWN_HALL;
        if (!reconcile(player, town.id())) return Result.RECONCILIATION_REQUIRED;

        String dimension = player.serverLevel().dimension().location().toString();
        var plans = KingdomExpansionPlans.get(player.getServer());
        List<KingdomExpansionPlans.Cell> cells = new ArrayList<>();
        for (var cell : plans.snapshot(town.id())) {
            if (dimension.equals(cell.dimension())) cells.add(cell);
        }
        // Nothing is charged until all checks succeed.
        if (cells.isEmpty() || cells.size() > 256) return Result.INVALID_PLAN;
        var quote = KingdomExpansionQuote.evaluate(town.id(), dimension, cells,
                civics.mapPlots(dimension), civics.townById(town.id()).treasury());
        if (quote.status() == KingdomExpansionQuote.Status.INSUFFICIENT_TREASURY)
            return Result.INSUFFICIENT_FUNDS;
        if (quote.status() != KingdomExpansionQuote.Status.READY) return Result.INVALID_PLAN;

        var legacy = KingdomTerritories.get(player.getServer());
        UUID originalMayor = town.mayor();
        for (var cell : cells) {
            BlockPos start = new BlockPos(cell.x() << 4, 64, cell.z() << 4);
            BlockPos end = new BlockPos((cell.x() << 4) + 15, 64, (cell.z() << 4) + 15);
            if (!player.serverLevel().getWorldBorder().isWithinBounds(start)
                    || !player.serverLevel().getWorldBorder().isWithinBounds(end))
                return Result.LAND_CONFLICT;
            UUID owner = legacy.owner(player.serverLevel(), start);
            if (owner != null && !owner.equals(originalMayor)
                    && !owner.equals(player.getUUID())) return Result.LAND_CONFLICT;
        }
        var admin = KingdomAdministrationData.get(player.getServer());
        if (admin.treasuryReserve(town.id()) < quote.price())
            return Result.INSUFFICIENT_FUNDS;

        UUID id = UUID.randomUUID();
        if (!admin.debitExpansion(town.id(), id, quote.price())) return Result.LEDGER_ERROR;
        if (!civics.commitPaidExpansion(id, town.id(), dimension, cells, quote.price())) {
            if (!admin.refundExpansion(id)) return Result.RECONCILIATION_REQUIRED;
            return Result.LAND_CONFLICT;
        }

        // The Civics record is authoritative. Mirror the legacy NPC-workshop
        // ownership index after commit; never charge again to repair the mirror.
        for (var cell : cells) {
            BlockPos pos = new BlockPos(cell.x() << 4, 64, cell.z() << 4);
            if (!legacy.claim(player.serverLevel(), pos, originalMayor))
                return Result.RECONCILIATION_REQUIRED;
        }

        Set<KingdomExpansionPlans.Cell> remaining = new HashSet<>(plans.snapshot(town.id()));
        remaining.removeAll(cells);
        plans.replace(town.id(), remaining);
        return Result.PURCHASED;
    }
}
