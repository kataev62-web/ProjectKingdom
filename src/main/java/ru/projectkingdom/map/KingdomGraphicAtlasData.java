package ru.projectkingdom.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import ru.projectkingdom.administration.KingdomAdministrationData;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomEnterpriseData;
import ru.projectkingdom.npc.KingdomExpansionPlans;
import ru.projectkingdom.npc.KingdomWorldAtlasSnapshot;

/**
 * Immutable bounded 23 x 15 graphical atlas projection.
 * The civic / enterprise ledgers remain authoritative: no client-side claims.
 */
public final class KingdomGraphicAtlasData {
    public static final int COLUMNS = 23;
    public static final int ROWS = 15;
    public static final int MAX_CELLS = COLUMNS * ROWS;
    public static final int[] SCALES = {1, 4, 16, 64};
    public enum Layer { LAND, POLITICAL, ENTERPRISES }
    public record Tile(int claims, boolean own, boolean mixed, boolean planned,
                       int townColor, int nationColor, int enterprises,
                       String town, String nation, String business) {
        public Tile {
            town = safe(town);
            nation = safe(nation);
            business = safe(business);
        }
        public boolean empty() { return claims == 0 && enterprises == 0 && !planned; }
    }
    public record Snapshot(int requestId, int centerX, int centerZ, int scale,
                           Layer layer, List<Tile> tiles) {
        public Snapshot {
            if (!supportedScale(scale) || tiles.size() != MAX_CELLS)
                throw new IllegalArgumentException("Invalid graphic atlas dimensions");
            tiles = List.copyOf(tiles);
        }
        public Tile tile(int column, int row) { return tiles.get(row * COLUMNS + column); }
    }
    public record Business(String dimension, int x, int z, String name) {}

    private static final class MutableTile {
        int claims, enterprises, townColor, nationColor;
        boolean own, mixed, planned;
        String town="", nation="", business="";
    }
    private KingdomGraphicAtlasData() {}

    public static boolean supportedScale(int scale) {
        return scale == 1 || scale == 4 || scale == 16 || scale == 64;
    }
    private static String safe(String s) {
        if (s == null) return "";
        return s.length() > 72 ? s.substring(0, 72) : s;
    }
    private static int index(int x, int z, long minX, long minZ, int scale) {
        long dx = (long)x - minX, dz = (long)z - minZ;
        if (dx < 0 || dz < 0 || dx >= (long)COLUMNS * scale || dz >= (long)ROWS * scale)
            return -1;
        return (int)(dz / scale) * COLUMNS + (int)(dx / scale);
    }

    /** Scans current public plot/firm registries once for a requested viewport. */
    public static Snapshot fromPlayer(ServerPlayer player, int requestId, int centerX,
                                      int centerZ, int scale, Layer layer) {
        KingdomCivicsData civic = KingdomCivicsData.get(player.getServer());
        KingdomEnterpriseData enterprise = KingdomEnterpriseData.get(player.getServer());
        var ownTown = civic.townOf(player.getUUID());
        UUID ownId = ownTown == null ? null : ownTown.id();
        String dimension = player.serverLevel().dimension().location().toString();
        List<Business> businesses = new ArrayList<>();
        for (var firm : enterprise.all()) {
            var core = enterprise.core(firm.id());
            if (core == null || !core.active()) continue;
            BlockPos pos = BlockPos.of(core.packedPos());
            businesses.add(new Business(core.dimension(), pos.getX() >> 4, pos.getZ() >> 4, firm.name()));
        }
        Set<KingdomExpansionPlans.Cell> planned = Set.of();
        if (ownTown != null && player.getUUID().equals(
                KingdomAdministrationData.get(player.getServer()).founder(ownTown.id()))) {
            planned = KingdomExpansionPlans.get(player.getServer()).snapshot(ownTown.id());
        }
        return project(requestId, centerX, centerZ, scale, layer, dimension,
                ownId, civic, businesses, planned);
    }

    /** Pure projection for tests; does not load world chunks or expose ore. */
    public static Snapshot project(int requestId, int centerX, int centerZ, int scale,
                                   Layer layer, String dimension, UUID ownTown,
                                   KingdomCivicsData civic, Collection<Business> businesses,
                                   Collection<KingdomExpansionPlans.Cell> plan) {
        if (!supportedScale(scale) || layer == null || dimension == null)
            throw new IllegalArgumentException("Unsupported atlas request");
        long startX = (long)centerX - (COLUMNS / 2L) * scale;
        long startZ = (long)centerZ - (ROWS / 2L) * scale;
        MutableTile[] cells = new MutableTile[MAX_CELLS];
        Arrays.setAll(cells, unused -> new MutableTile());
        Map<UUID, String> nationNames = new HashMap<>();
        for (var nation : civic.nations()) nationNames.put(nation.id(), nation.name());
        for (var plot : civic.mapPlots(dimension)) {
            int idx = index(plot.x(), plot.z(), startX, startZ, scale);
            if (idx < 0) continue;
            var town = civic.townById(plot.town());
            if (town == null) continue;
            MutableTile cell = cells[idx];
            int color = town.id().hashCode();
            if (cell.claims > 0 && cell.townColor != color) cell.mixed = true;
            cell.claims++;
            cell.townColor = color;
            cell.own |= town.id().equals(ownTown);
            if (cell.town.isEmpty()) cell.town = town.name();
            else if (!cell.town.equals(town.name())) cell.town = "Несколько городов";
            if (town.nation() != null) {
                String nation = nationNames.getOrDefault(town.nation(), "");
                int nationColor = town.nation().hashCode();
                if (!cell.nation.isEmpty() && !cell.nation.equals(nation)) cell.mixed = true;
                cell.nation = cell.nation.isEmpty() ? nation : (cell.nation.equals(nation) ? nation : "Несколько государств");
                cell.nationColor = nationColor;
            }
        }
        for (var business : businesses) {
            if (!dimension.equals(business.dimension())) continue;
            int idx = index(business.x(), business.z(), startX, startZ, scale);
            if (idx < 0) continue;
            MutableTile cell = cells[idx];
            cell.enterprises++;
            if (cell.business.isEmpty()) cell.business = business.name();
            else if (!cell.business.equals(business.name())) cell.business = "Несколько предприятий";
        }
        for (var plotted : plan) {
            if (!dimension.equals(plotted.dimension())) continue;
            int idx = index(plotted.x(), plotted.z(), startX, startZ, scale);
            if (idx >= 0 && cells[idx].claims == 0) cells[idx].planned = true;
        }
        List<Tile> result = new ArrayList<>(MAX_CELLS);
        for (MutableTile cell : cells) {
            result.add(new Tile(cell.claims, cell.own, cell.mixed, cell.planned,
                    cell.townColor, cell.nationColor, cell.enterprises,
                    cell.town, cell.nation, cell.business));
        }
        return new Snapshot(requestId, centerX, centerZ, scale, layer, result);
    }
}
