package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bounded, read-only atlas projection from the existing civic and enterprise
 * ledgers. A tile groups one or more chunks; it never changes ownership.
 * No speculative or undiscovered mineral deposit data is included.
 */
public final class KingdomWorldAtlasSnapshot {
    public static final int COLUMNS = 7;
    public static final int ROWS = 4;

    public record EnterprisePoint(String dimension, int chunkX, int chunkZ,
                                  String name, String type) {}
    public record Tile(int chunkX, int chunkZ, int scale,
                       int occupied, boolean ownTown, boolean foreignTown,
                       String town, String nation, int enterprises,
                       String enterpriseName, String enterpriseType) {}
    public record View(int startX, int startZ, int scale, List<Tile> tiles) {
        public View { tiles = List.copyOf(tiles); }
        public Tile at(int column, int row) {
            if(column < 0 || column >= COLUMNS || row < 0 || row >= ROWS)
                throw new IndexOutOfBoundsException();
            return tiles.get(row * COLUMNS + column);
        }
    }
    private static final class TileState {
        private int claims, businesses;
        private boolean own, foreign;
        private String town="", nation="", enterprise="", type="";
    }

    private KingdomWorldAtlasSnapshot() {}

    public static View forPlayer(ServerPlayer player, int centerX, int centerZ, int scale) {
        Objects.requireNonNull(player);
        var civics = KingdomCivicsData.get(player.getServer());
        var enterpriseData = KingdomEnterpriseData.get(player.getServer());
        List<EnterprisePoint> businesses = new ArrayList<>();
        for (var company : enterpriseData.all()) {
            var core = enterpriseData.core(company.id());
            if (core == null || !core.active()) continue;
            BlockPos pos = BlockPos.of(core.packedPos());
            businesses.add(new EnterprisePoint(core.dimension(), pos.getX() >> 4,
                    pos.getZ() >> 4, company.name(), core.kind().title()));
        }
        var town = civics.townOf(player.getUUID());
        return create(civics, businesses,
                player.serverLevel().dimension().location().toString(),
                town == null ? null : town.id(), centerX, centerZ, scale);
    }

    /** Deterministic testable projection; scan world claims ONCE per view. */
    public static View create(KingdomCivicsData civics, Collection<EnterprisePoint> businesses,
                              String dimension, UUID ownTown, int centerX, int centerZ, int scale) {
        Objects.requireNonNull(civics);
        Objects.requireNonNull(businesses);
        Objects.requireNonNull(dimension);
        if (scale != 1 && scale != 4 && scale != 16 && scale != 64)
            throw new IllegalArgumentException("Unsupported atlas scale");
        int startX = centerX - 3 * scale;
        int startZ = centerZ - scale;
        TileState[] states = new TileState[COLUMNS * ROWS];
        for (int i = 0; i < states.length; i++) states[i] = new TileState();
        for (var plot : civics.mapPlots(dimension)) {
            int index = index(plot.x(), plot.z(), startX, startZ, scale);
            if (index < 0) continue;
            var town = civics.townById(plot.town());
            if (town == null) continue;
            TileState state = states[index];
            state.claims++;
            if (plot.town().equals(ownTown)) state.own = true;
            else state.foreign = true;
            if (state.town.isEmpty()) state.town = town.name();
            else if (!state.town.equals(town.name())) state.town = "Несколько городов";
            if (town.nation() != null) {
                String nation = civics.nations().stream().filter(n -> n.id().equals(town.nation()))
                        .map(KingdomCivicsData.Nation::name).findFirst().orElse("");
                if (!nation.isEmpty()) {
                    if (state.nation.isEmpty()) state.nation = nation;
                    else if (!state.nation.equals(nation)) state.nation = "Несколько государств";
                }
            }
        }
        for (var business : businesses) {
            if (!dimension.equals(business.dimension())) continue;
            int index = index(business.chunkX(), business.chunkZ(), startX, startZ, scale);
            if (index < 0) continue;
            TileState state = states[index];
            state.businesses++;
            if (state.enterprise.isEmpty()) {
                state.enterprise = business.name();
                state.type = business.type();
            }
        }
        List<Tile> tiles = new ArrayList<>(states.length);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                TileState state = states[row * COLUMNS + col];
                tiles.add(new Tile(startX + col * scale, startZ + row * scale, scale,
                        state.claims, state.own, state.foreign, state.town, state.nation,
                        state.businesses, state.enterprise, state.type));
            }
        }
        return new View(startX, startZ, scale, tiles);
    }

    private static int index(int x, int z, int minX, int minZ, int scale) {
        long relativeX = (long)x - minX;
        long relativeZ = (long)z - minZ;
        if (relativeX < 0 || relativeZ < 0
                || relativeX >= (long)COLUMNS * scale || relativeZ >= (long)ROWS * scale) return -1;
        return (int)(relativeZ / scale) * COLUMNS + (int)(relativeX / scale);
    }
}
