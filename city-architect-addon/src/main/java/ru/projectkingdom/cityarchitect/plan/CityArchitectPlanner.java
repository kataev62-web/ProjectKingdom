package ru.projectkingdom.cityarchitect.plan;

import net.minecraft.core.BlockPos;
import ru.projectkingdom.cityarchitect.data.CityArchitectPlanData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CityArchitectPlanner {
    private static final List<String> REQUIRED_ROLES =
            List.of("bank", "warehouse", "market", "smithy", "tavern", "house");

    public PlanningResult plan(CityPlanningRequest request) {
        Objects.requireNonNull(request, "request");
        Map<String, BlueprintFootprint> byRole = new LinkedHashMap<>();
        request.blueprints().stream()
                .sorted(Comparator.comparing(BlueprintFootprint::role))
                .forEach(value -> byRole.putIfAbsent(value.role().toLowerCase(Locale.ROOT), value));
        List<Finding> findings = new ArrayList<>();
        for (String role : REQUIRED_ROLES) {
            if (!byRole.containsKey(role)) {
                findings.add(new Finding(FindingCode.MISSING_BLUEPRINT,
                        "Не найдена серверная схема: " + role));
            }
        }
        if (!findings.isEmpty()) {
            return new PlanningResult(List.of(), findings);
        }

        List<Outline> occupied = new ArrayList<>(request.existingOutlines());
        List<BuildingPlacement> administration = new ArrayList<>();
        List<BuildingPlacement> production = new ArrayList<>();
        List<BuildingPlacement> housing = new ArrayList<>();
        place(request, byRole.get("bank"), -22, -6, occupied).ifPresentOrElse(administration::add,
                () -> findings.add(noSpace("bank")));
        place(request, byRole.get("warehouse"), 14, -12, occupied).ifPresentOrElse(administration::add,
                () -> findings.add(noSpace("warehouse")));
        place(request, byRole.get("market"), -4, 14, occupied).ifPresentOrElse(administration::add,
                () -> findings.add(noSpace("market")));
        place(request, byRole.get("smithy"), 36, -6, occupied).ifPresentOrElse(production::add,
                () -> findings.add(noSpace("smithy")));
        place(request, byRole.get("tavern"), -28, 24, occupied).ifPresentOrElse(housing::add,
                () -> findings.add(noSpace("tavern")));
        place(request, byRole.get("house"), -30, -28, occupied).ifPresentOrElse(housing::add,
                () -> findings.add(noSpace("house")));
        if (!findings.isEmpty()) {
            return new PlanningResult(List.of(), findings);
        }

        List<RoadPlacement> mainRoads = List.of(
                new RoadPlacement(axisX(request.ratHall(), request.radius() / 2), 3),
                new RoadPlacement(axisZ(request.ratHall(), request.radius() / 2), 3));
        List<RoadPlacement> secondary = List.of(
                new RoadPlacement(connect(request.ratHall(), center(administration.get(2))), 3),
                new RoadPlacement(connect(request.ratHall(), center(production.get(0))), 3),
                new RoadPlacement(connect(request.ratHall(), center(housing.get(0))), 3));

        List<CityStage> stages = List.of(
                new CityStage(0, CityArchitectPlanData.StageKind.ROADS_SQUARE,
                        List.of(), mainRoads, "Главная площадь и две оси улиц"),
                new CityStage(1, CityArchitectPlanData.StageKind.ADMINISTRATION,
                        administration, List.of(), "Банк, центральный склад и рынок у площади"),
                new CityStage(2, CityArchitectPlanData.StageKind.PRODUCTION,
                        production, List.of(), "Кузница вынесена от жилья"),
                new CityStage(3, CityArchitectPlanData.StageKind.HOUSING_HOSPITALITY,
                        housing, List.of(), "Таверна и первый жилой квартал"),
                new CityStage(4, CityArchitectPlanData.StageKind.SECONDARY_INFRASTRUCTURE,
                        List.of(), secondary, "Второстепенные дороги связывают кварталы"),
                new CityStage(5, CityArchitectPlanData.StageKind.EXPANSION,
                        List.of(), List.of(), "Резерв расширения по условиям местности"));
        CityMetrics metrics = new CityMetrics(
                stages.stream().mapToInt(stage -> stage.buildings().size()).sum(),
                stages.stream().mapToInt(stage -> stage.roads().stream()
                        .mapToInt(road -> road.centerline().size()).sum()).sum(),
                occupied.size());
        return new PlanningResult(List.of(new CityCandidate(
                "city-1", request.ratHall(), stages, metrics)), List.of());
    }

    private static Optional<BuildingPlacement> place(
            CityPlanningRequest request,
            BlueprintFootprint blueprint,
            int relativeX,
            int relativeZ,
            List<Outline> occupied
    ) {
        for (int radius = 0; radius <= 16; radius += 2) {
            for (int dx = -radius; dx <= radius; dx += Math.max(2, radius * 2)) {
                for (int dz = -radius; dz <= radius; dz += Math.max(2, radius * 2)) {
                    int x = request.ratHall().getX() + relativeX + dx;
                    int z = request.ratHall().getZ() + relativeZ + dz;
                    Outline outline = new Outline(x, z,
                            x + blueprint.width() - 1, z + blueprint.length() - 1);
                    if (!inside(request, outline) || occupied.stream().anyMatch(outline::overlaps)) {
                        continue;
                    }
                    SurfaceCell origin = request.surface().get(cellKey(x, z));
                    if (origin == null || !footprintBuildable(request, outline, origin.position().getY())) {
                        continue;
                    }
                    occupied.add(outline);
                    return Optional.of(new BuildingPlacement(blueprint.key(), blueprint.role(),
                            origin.position(), 0, blueprint.width(), blueprint.length(), blueprint.stageKind()));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean inside(CityPlanningRequest request, Outline outline) {
        int centerX = request.ratHall().getX();
        int centerZ = request.ratHall().getZ();
        return outline.minX() >= centerX - request.radius()
                && outline.maxX() <= centerX + request.radius()
                && outline.minZ() >= centerZ - request.radius()
                && outline.maxZ() <= centerZ + request.radius();
    }

    private static boolean footprintBuildable(CityPlanningRequest request, Outline outline, int baseY) {
        for (int x = outline.minX(); x <= outline.maxX(); x++) {
            for (int z = outline.minZ(); z <= outline.maxZ(); z++) {
                SurfaceCell cell = request.surface().get(cellKey(x, z));
                if (cell == null || !cell.buildable() || cell.water()
                        || Math.abs(cell.position().getY() - baseY) > 1) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Finding noSpace(String role) {
        return new Finding(FindingCode.NO_SAFE_PLACEMENT,
                "Нет безопасного места для схемы: " + role);
    }

    private static BlockPos center(BuildingPlacement building) {
        return building.origin().offset(building.width() / 2, 0, building.length() / 2);
    }

    private static List<BlockPos> axisX(BlockPos center, int radius) {
        List<BlockPos> result = new ArrayList<>();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            result.add(new BlockPos(x, center.getY(), center.getZ()));
        }
        return List.copyOf(result);
    }

    private static List<BlockPos> axisZ(BlockPos center, int radius) {
        List<BlockPos> result = new ArrayList<>();
        for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
            result.add(new BlockPos(center.getX(), center.getY(), z));
        }
        return List.copyOf(result);
    }

    private static List<BlockPos> connect(BlockPos from, BlockPos to) {
        List<BlockPos> result = new ArrayList<>();
        int x = from.getX();
        int z = from.getZ();
        result.add(new BlockPos(x, from.getY(), z));
        while (x != to.getX()) {
            x += Integer.signum(to.getX() - x);
            result.add(new BlockPos(x, from.getY(), z));
        }
        while (z != to.getZ()) {
            z += Integer.signum(to.getZ() - z);
            result.add(new BlockPos(x, from.getY(), z));
        }
        return List.copyOf(result);
    }

    public static long cellKey(int x, int z) {
        return BlockPos.asLong(x, 0, z);
    }

    public record SurfaceCell(BlockPos position, boolean buildable, boolean water) {
        public SurfaceCell {
            position = Objects.requireNonNull(position, "position").immutable();
        }
    }

    public record Outline(int minX, int minZ, int maxX, int maxZ) {
        public Outline {
            if (minX > maxX || minZ > maxZ) throw new IllegalArgumentException("Invalid outline");
        }

        public boolean overlaps(Outline other) {
            return minX <= other.maxX && maxX >= other.minX
                    && minZ <= other.maxZ && maxZ >= other.minZ;
        }
    }

    public record BlueprintFootprint(
            String key, String role, int width, int length, CityArchitectPlanData.StageKind stageKind
    ) {
        public BlueprintFootprint {
            key = text(key, "key");
            role = text(role, "role").toLowerCase(Locale.ROOT);
            Objects.requireNonNull(stageKind, "stageKind");
            if (width < 1 || length < 1 || width > 64 || length > 64)
                throw new IllegalArgumentException("Invalid blueprint footprint");
        }
    }

    public record CityPlanningRequest(
            BlockPos ratHall,
            Map<Long, SurfaceCell> surface,
            List<Outline> existingOutlines,
            List<BlueprintFootprint> blueprints,
            int radius
    ) {
        public CityPlanningRequest {
            ratHall = Objects.requireNonNull(ratHall, "ratHall").immutable();
            surface = Map.copyOf(Objects.requireNonNull(surface, "surface"));
            existingOutlines = List.copyOf(Objects.requireNonNull(existingOutlines, "existingOutlines"));
            blueprints = List.copyOf(Objects.requireNonNull(blueprints, "blueprints"));
            if (radius < 16 || radius > 96) throw new IllegalArgumentException("radius must be 16..96");
        }
    }

    public record BuildingPlacement(
            String schematicKey,
            String role,
            BlockPos origin,
            int rotation,
            int width,
            int length,
            CityArchitectPlanData.StageKind stageKind
    ) {
        public BuildingPlacement {
            schematicKey = text(schematicKey, "schematicKey");
            role = text(role, "role");
            origin = Objects.requireNonNull(origin, "origin").immutable();
            Objects.requireNonNull(stageKind, "stageKind");
        }
    }

    public record RoadPlacement(List<BlockPos> centerline, int width) {
        public RoadPlacement {
            centerline = List.copyOf(centerline);
            if (centerline.isEmpty() || width != 3) throw new IllegalArgumentException("Invalid road");
        }
    }

    public record CityStage(
            int index,
            CityArchitectPlanData.StageKind kind,
            List<BuildingPlacement> buildings,
            List<RoadPlacement> roads,
            String explanation
    ) {
        public CityStage {
            Objects.requireNonNull(kind, "kind");
            buildings = List.copyOf(buildings);
            roads = List.copyOf(roads);
            explanation = text(explanation, "explanation");
        }
    }

    public record CityMetrics(int buildings, int roadCells, int occupiedOutlines) { }

    public record CityCandidate(
            String id, BlockPos ratHall, List<CityStage> stages, CityMetrics metrics
    ) {
        public CityCandidate {
            id = text(id, "id");
            ratHall = ratHall.immutable();
            stages = List.copyOf(stages);
            Objects.requireNonNull(metrics, "metrics");
        }
    }

    public enum FindingCode { MISSING_BLUEPRINT, NO_SAFE_PLACEMENT }

    public record Finding(FindingCode code, String message) {
        public Finding {
            Objects.requireNonNull(code, "code");
            message = text(message, "message");
        }
    }

    public record PlanningResult(List<CityCandidate> candidates, List<Finding> findings) {
        public PlanningResult {
            candidates = List.copyOf(candidates);
            findings = List.copyOf(findings);
        }
    }

    private static String text(String value, String name) {
        String checked = Objects.requireNonNull(value, name).trim();
        if (checked.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return checked;
    }
}
