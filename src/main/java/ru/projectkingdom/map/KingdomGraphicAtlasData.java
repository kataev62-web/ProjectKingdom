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
    public enum Layer { LAND, POLITICAL, ENTERPRISES, INFRASTRUCTURE }
    public record Tile(int claims, boolean own, boolean mixed, boolean planned,
                       int townColor, int nationColor, int enterprises,
                       String town, String nation, String business,
                       int terrain, int elevation) {
        /** Keep existing test fixtures and packet callers source compatible. */
        public Tile(int claims, boolean own, boolean mixed, boolean planned,
                    int townColor, int nationColor, int enterprises,
                    String town, String nation, String business) {
            this(claims, own, mixed, planned, townColor, nationColor, enterprises,
                    town, nation, business, KingdomAtlasTerrain.Type.UNKNOWN.ordinal(), 0);
        }
        public Tile {
            town = safe(town);
            nation = safe(nation);
            business = safe(business);
        }
        public boolean empty() { return claims == 0 && enterprises == 0 && !planned
                && terrain == KingdomAtlasTerrain.Type.UNKNOWN.ordinal(); }
    }
    public record Snapshot(int requestId, int centerX, int centerZ, int scale,
                           Layer layer, List<Tile> tiles,
                           List<KingdomAtlasInfrastructure.Marker> markers,
                           List<KingdomAtlasBuildingOutlines.Outline> outlines) {
        public Snapshot(int requestId, int centerX, int centerZ, int scale,
                        Layer layer, List<Tile> tiles,
                        List<KingdomAtlasInfrastructure.Marker> markers) {
            this(requestId,centerX,centerZ,scale,layer,tiles,markers,List.of());
        }
        /** Backward compatible constructor for empty infrastructure viewports. */
        public Snapshot(int requestId, int centerX, int centerZ, int scale,
                        Layer layer, List<Tile> tiles) {
            this(requestId, centerX, centerZ, scale, layer, tiles, List.of());
        }
        public Snapshot {
            if (!supportedScale(scale) || layer == null || tiles == null || tiles.size() != MAX_CELLS
                    || markers == null || markers.size() > KingdomAtlasInfrastructure.MAX_MARKERS
                    || outlines == null || outlines.size() > KingdomAtlasBuildingOutlines.MAX_OUTLINES)
                throw new IllegalArgumentException("Invalid graphic atlas dimensions");
            tiles = List.copyOf(tiles);
            markers = List.copyOf(markers);
            outlines = List.copyOf(outlines);
        }
        public Tile tile(int column, int row) { return tiles.get(row * COLUMNS + column); }
    }
    public record Business(String dimension, int x, int z, String name) {}

    private static final class MutableTile {
        int claims, enterprises, townColor, nationColor, terrain, elevation;
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
        var terrain = KingdomAtlasTerrainCache.get(player.getServer())
                .view(player.serverLevel(), centerX, centerZ, scale);
        var projected = project(requestId, centerX, centerZ, scale, layer, dimension,
                ownId, civic, businesses, planned, terrain);
        // UI names grow naturally without renaming the actual Town key.
        java.util.Map<String,String> labels=new java.util.HashMap<>();
        var buildings=ru.projectkingdom.npc.KingdomConstructionData.get(player.getServer());
        for(var town:civic.towns()){
            var stage=ru.projectkingdom.settlement.KingdomSettlementGrowth.forTown(
                    civic,buildings,town.id(),dimension,player.getServer());
            stage=ru.projectkingdom.settlement.KingdomSettlementRanks.get(player.getServer()).current(town.id(),stage);
            labels.put(town.name(),stage.title()+" "+town.name());
        }
        java.util.List<Tile> labeled=new java.util.ArrayList<>(MAX_CELLS);
        for(var tile:projected.tiles()){
            String name=labels.getOrDefault(tile.town(),tile.town());
            labeled.add(name.equals(tile.town())?tile:new Tile(tile.claims(),tile.own(),
                    tile.mixed(),tile.planned(),tile.townColor(),tile.nationColor(),
                    tile.enterprises(),name,tile.nation(),tile.business(),
                    tile.terrain(),tile.elevation()));
        }
        var markers = KingdomAtlasInfrastructure.forPlayer(player, centerX, centerZ, scale);
        var outlines = KingdomAtlasBuildingOutlines.forPlayer(player, centerX, centerZ, scale);
        return new Snapshot(projected.requestId(), projected.centerX(), projected.centerZ(),
                projected.scale(), projected.layer(), labeled, markers, outlines);
    }

    /** Pure projection for tests; does not load world chunks or expose ore. */
    public static Snapshot project(int requestId, int centerX, int centerZ, int scale,
                                   Layer layer, String dimension, UUID ownTown,
                                   KingdomCivicsData civic, Collection<Business> businesses,
                                   Collection<KingdomExpansionPlans.Cell> plan) {
        return project(requestId, centerX, centerZ, scale, layer, dimension,
                ownTown, civic, businesses, plan, List.of());
    }

    /** Terrain cells are optional and always sampled from already-loaded chunks. */
    public static Snapshot project(int requestId, int centerX, int centerZ, int scale,
                                   Layer layer, String dimension, UUID ownTown,
                                   KingdomCivicsData civic, Collection<Business> businesses,
                                   Collection<KingdomExpansionPlans.Cell> plan,
                                   Collection<KingdomAtlasTerrain.Cell> terrainCells) {
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
        for (var terrain : terrainCells) {
            int idx = index(terrain.chunkX(), terrain.chunkZ(), startX, startZ, scale);
            if (idx < 0) continue;
            cells[idx].terrain = terrain.type().ordinal();
            cells[idx].elevation = Math.max(-512, Math.min(1024, terrain.elevation()));
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
                    cell.town, cell.nation, cell.business, cell.terrain, cell.elevation));
        }
        return new Snapshot(requestId, centerX, centerZ, scale, layer, result);
    }
}
