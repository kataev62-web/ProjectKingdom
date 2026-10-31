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
 * CivicsData owns the city account ledger and territorial plots in ONE
 * SavedData record; AdministrationData owns the municipal BANK-backed reserve.
 * An idempotent debit ID + committed civic receipt allow reconciliation when
 * the two files were persisted at different points of a crash.
 */
public final class KingdomCityExpansionPurchase {
    public enum Result { PURCHASED, NOT_FOUNDER, NOT_AT_TOWN_HALL, INVALID_PLAN,
        LAND_CONFLICT, INSUFFICIENT_FUNDS, LEDGER_ERROR, RECONCILIATION_REQUIRED }
    private KingdomCityExpansionPurchase() {}

    private static UUID adminFounder(ServerPlayer player, KingdomCivicsData.Town town) {
        return KingdomAdministrationData.get(player.getServer()).founder(town.id());
    }
    private static boolean founder(ServerPlayer player, KingdomCivicsData.Town town) {
        return player.getUUID().equals(adminFounder(player,town));
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
        // Repair a legacy NPC-workshop claim mirror after a crash, without
        // touching the authoritative city plot or charging the treasury.
        var town = civics.townById(cityId);
        if (town == null) return false;
        UUID stableFounder = admin.founder(cityId);
        if (stableFounder == null) return false;
        var legacy = KingdomTerritories.get(player.getServer());
        String dimension = player.serverLevel().dimension().location().toString();
        for (var plot : civics.mapPlots(dimension)) {
            if (!cityId.equals(plot.town())) continue;
            var pos = new BlockPos(plot.x() << 4, 64, plot.z() << 4);
            UUID legacyOwner = legacy.owner(player.serverLevel(), pos);
            if (legacyOwner != null && !legacyOwner.equals(town.mayor())
                    && !legacyOwner.equals(stableFounder)) return false;
            if (legacyOwner == null && !legacy.claim(player.serverLevel(), pos, stableFounder))
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
        // Free territory is bought on the city map from the explicit, physically
        // backed municipal BANK account; personal receipts are never auto-debited.
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
                civics.mapPlots(dimension), Math.min(civics.townById(town.id()).treasury(),
                    KingdomAdministrationData.get(player.getServer()).treasuryReserve(town.id())));
        if (quote.status() == KingdomExpansionQuote.Status.INSUFFICIENT_TREASURY)
            return Result.INSUFFICIENT_FUNDS;
        if (quote.status() != KingdomExpansionQuote.Status.READY) return Result.INVALID_PLAN;

        var legacy = KingdomTerritories.get(player.getServer());
        UUID stableFounder = adminFounder(player, town);
        for (var cell : cells) {
            BlockPos start = new BlockPos(cell.x() << 4, 64, cell.z() << 4);
            BlockPos end = new BlockPos((cell.x() << 4) + 15, 64, (cell.z() << 4) + 15);
            if (!player.serverLevel().getWorldBorder().isWithinBounds(start)
                    || !player.serverLevel().getWorldBorder().isWithinBounds(end))
                return Result.LAND_CONFLICT;
            UUID owner = legacy.owner(player.serverLevel(), start);
            if (owner != null && !owner.equals(stableFounder)
                    && !owner.equals(town.mayor())) return Result.LAND_CONFLICT;
        }
        var admin = KingdomAdministrationData.get(player.getServer());
        var treasury=KingdomPhysicalCash.townVault(player,town.id(),
                ru.projectkingdom.administration.AdministrativeBindingRole.BANK_VAULT);
        // The acquisition fee is earmarked as sovereign revenue from the bank-backed town account;
        // the receipt prevents city members from re-spending its ledger balance.
        // Actual inter-vault transport to a separate sovereign capital treasury is
        // not enabled until that vault is configured, so do not fake a destination.
        long bankClaims=admin.bankReserve(town.id()),cityClaims=admin.treasuryReserve(town.id());
        if(treasury==null||bankClaims>Long.MAX_VALUE-cityClaims
                ||KingdomPhysicalCash.value(treasury)<bankClaims+cityClaims
                ||cityClaims<quote.price())
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
            if (!legacy.claim(player.serverLevel(), pos, stableFounder))
                return Result.RECONCILIATION_REQUIRED;
        }

        Set<KingdomExpansionPlans.Cell> remaining = new HashSet<>(plans.snapshot(town.id()));
        remaining.removeAll(cells);
        plans.replace(town.id(), remaining);
        return Result.PURCHASED;
    }
}
