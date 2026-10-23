package ru.projectkingdom.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import ru.projectkingdom.administration.AdministrativeKind;
import ru.projectkingdom.administration.KingdomAdministrationData;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomConstructionData;
import ru.projectkingdom.npc.KingdomConstructionPlans;
import ru.projectkingdom.npc.KingdomEnterpriseData;

/**
 * Public built infrastructure projection from existing persistent registries.
 * No client-submitted markers and no world-chunk generation.
 */
public final class KingdomAtlasInfrastructure {
    public static final int MAX_MARKERS = 96;
    public enum Kind { BUILDING, TOWN_HALL, ADMINISTRATION, ENTERPRISE, ROAD, BRIDGE }

    public record Marker(Kind kind, int blockX, int blockZ, String label) {
        public Marker {
            if (kind == null) throw new IllegalArgumentException("Marker kind required");
            if (label == null) label = "";
            if (label.length() > 72) label = label.substring(0, 72);
        }
    }
    public record Source(String dimension, Marker marker) {}

    private KingdomAtlasInfrastructure() {}

    public static List<Marker> forPlayer(ServerPlayer player, int cx, int cz, int scale) {
        var server = player.getServer();
        String dimension = player.serverLevel().dimension().location().toString();
        List<Source> sources = new ArrayList<>();
        for (var project : KingdomConstructionData.get(server).all()) {
            if (project.status() != KingdomConstructionData.Status.COMPLETED) continue;
            String title = KingdomConstructionPlans.title(project.design());
            Kind kind = designKind(project.design(), title);
            sources.add(new Source(project.dimension(), new Marker(kind, project.x(), project.z(), title)));
        }
        var civic = KingdomCivicsData.get(server);
        var admin = KingdomAdministrationData.get(server);
        for (var town : civic.towns()) {
            for (var core : admin.cores(town.id())) {
                BlockPos pos = BlockPos.of(core.packedPos());
                Kind kind = core.kind() == AdministrativeKind.TOWN_HALL
                        ? Kind.TOWN_HALL : Kind.ADMINISTRATION;
                sources.add(new Source(core.dimension(), new Marker(kind,
                        pos.getX(), pos.getZ(), core.kind().russianName() + " — " + town.name())));
            }
        }
        var enterprises = KingdomEnterpriseData.get(server);
        for (var company : enterprises.all()) {
            var core = enterprises.core(company.id());
            if (core == null || !core.active()) continue;
            BlockPos pos = BlockPos.of(core.packedPos());
            sources.add(new Source(core.dimension(), new Marker(Kind.ENTERPRISE,
                    pos.getX(), pos.getZ(), company.name())));
        }
        return select(sources, dimension, cx, cz, scale);
    }

    private static Kind designKind(String design, String title) {
        String name = ((design == null ? "" : design) + " " + title).toLowerCase(java.util.Locale.ROOT);
        if (name.contains("town_hall") || name.contains("ратуш")) return Kind.TOWN_HALL;
        if (name.contains("bridge") || name.contains("мост")) return Kind.BRIDGE;
        if (name.contains("road") || name.contains("дорог")) return Kind.ROAD;
        return Kind.BUILDING;
    }

    /** Pure bounded viewport filter, prioritized by distance to the view center. */
    public static List<Marker> select(Collection<Source> candidates, String dimension,
                                      int centerChunkX, int centerChunkZ, int scale) {
        if (!KingdomGraphicAtlasData.supportedScale(scale) || dimension == null)
            throw new IllegalArgumentException("Invalid atlas viewport");
        long startX = (long)centerChunkX - (KingdomGraphicAtlasData.COLUMNS / 2L) * scale;
        long startZ = (long)centerChunkZ - (KingdomGraphicAtlasData.ROWS / 2L) * scale;
        long maxX = startX + (long)KingdomGraphicAtlasData.COLUMNS * scale;
        long maxZ = startZ + (long)KingdomGraphicAtlasData.ROWS * scale;
        List<Marker> visible = new ArrayList<>();
        for (var item : candidates) {
            if (!dimension.equals(item.dimension())) continue;
            var mark = item.marker();
            long x = Math.floorDiv(mark.blockX(), 16), z = Math.floorDiv(mark.blockZ(), 16);
            if (x >= startX && x < maxX && z >= startZ && z < maxZ) visible.add(mark);
        }
        visible.sort(Comparator.<Marker>comparingLong(m -> {
                long dx = (long)m.blockX() - (long)centerChunkX * 16L;
                long dz = (long)m.blockZ() - (long)centerChunkZ * 16L;
                return dx * dx + dz * dz;
            }).thenComparing(m -> m.kind().ordinal()).thenComparing(Marker::label));
        if (visible.size() > MAX_MARKERS) return List.copyOf(visible.subList(0, MAX_MARKERS));
        return List.copyOf(visible);
    }
}
