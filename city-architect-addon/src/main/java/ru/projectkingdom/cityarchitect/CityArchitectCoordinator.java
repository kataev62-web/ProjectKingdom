package ru.projectkingdom.cityarchitect;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import ru.projectkingdom.assistant.KingdomAssistantData;
import ru.projectkingdom.cityarchitect.data.CityArchitectPlanData;
import ru.projectkingdom.cityarchitect.plan.CityArchitectReasoner;
import ru.projectkingdom.cityarchitect.plan.CityArchitectRoadPlanner;
import ru.projectkingdom.cityarchitect.world.CityArchitectLandmarks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CityArchitectCoordinator {
    private static final Pattern ROAD_ARGUMENT = Pattern.compile("^от\\s+(.+?)\\s+до\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final CityArchitectPlanData data;
    private final CityArchitectLandmarks landmarks;
    private final CityArchitectRoadPlanner roadPlanner;
    private final CityArchitectReasoner reasoner;
    private final SurfaceSnapshotProvider snapshots;
    private final MarkerSink markers;
    private final Executor serverExecutor;

    public CityArchitectCoordinator(
            CityArchitectPlanData data,
            CityArchitectLandmarks landmarks,
            CityArchitectRoadPlanner roadPlanner,
            CityArchitectReasoner reasoner,
            SurfaceSnapshotProvider snapshots,
            MarkerSink markers,
            Executor serverExecutor
    ) {
        this.data = Objects.requireNonNull(data, "data");
        this.landmarks = Objects.requireNonNull(landmarks, "landmarks");
        this.roadPlanner = Objects.requireNonNull(roadPlanner, "roadPlanner");
        this.reasoner = Objects.requireNonNull(reasoner, "reasoner");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.markers = Objects.requireNonNull(markers, "markers");
        this.serverExecutor = Objects.requireNonNull(serverExecutor, "serverExecutor");
    }

    public CompletableFuture<RoadResult> requestRoad(ServerPlayer player, String argument) {
        Objects.requireNonNull(player, "player");
        var assistant = KingdomAssistantData.get(player.getServer()).ownedBy(player.getUUID());
        if (assistant.isEmpty()) {
            return CompletableFuture.completedFuture(RoadResult.failure(
                    ResultCode.ASSISTANT_MISSING, "Сначала призовите личного помощника."));
        }
        String dimension = player.level().dimension().location().toString();
        if (!assistant.orElseThrow().dimension().equals(dimension)) {
            return CompletableFuture.completedFuture(RoadResult.failure(
                    ResultCode.ASSISTANT_MISSING, "Помощник находится в другом измерении."));
        }
        return requestRoad(new RequestContext(player.getUUID(), assistant.orElseThrow().entityId(),
                dimension, player.hasPermissions(4) ? 4 : 0), argument);
    }

    public CompletableFuture<RoadResult> requestRoad(RequestContext context, String argument) {
        Objects.requireNonNull(context, "context");
        if (context.permissionLevel() < 4) {
            return completed(ResultCode.NO_PERMISSION,
                    "План начального города доступен владельцу с уровнем прав 4.");
        }
        if (context.owner() == null || context.assistant() == null) {
            return completed(ResultCode.ASSISTANT_MISSING, "Личный помощник не найден.");
        }
        Matcher parsed = ROAD_ARGUMENT.matcher(Objects.requireNonNullElse(argument, "").trim());
        if (!parsed.matches()) {
            return completed(ResultCode.INVALID_REQUEST,
                    "Укажите две точки в форме «от ратуши до кузницы».");
        }

        CityArchitectLandmarks.Result from = landmarks.resolve(parsed.group(1), context.dimension());
        CityArchitectLandmarks.Result to = landmarks.resolve(parsed.group(2), context.dimension());
        ResultCode landmarkFailure = landmarkFailure(from, to);
        if (landmarkFailure != null) {
            String message = landmarkFailure == ResultCode.LANDMARK_AMBIGUOUS
                    ? "Найдено несколько подходящих объектов; уточните название."
                    : "Один из указанных объектов не найден в текущем измерении.";
            return completed(landmarkFailure, message);
        }

        CityArchitectLandmarks.Landmark start = from.unique().orElseThrow();
        CityArchitectLandmarks.Landmark goal = to.unique().orElseThrow();
        CityArchitectRoadPlanner.RoadRequest snapshot = snapshots.snapshot(start.center(), goal.center());
        List<CityArchitectRoadPlanner.RoadCandidate> candidates = roadPlanner.plan(snapshot);
        if (candidates.isEmpty()) {
            return completed(ResultCode.NO_SAFE_PATH,
                    "Безопасный маршрут в обследованных загруженных чанках не найден.");
        }

        List<CityArchitectReasoner.CandidateSummary> summaries = candidates.stream()
                .map(candidate -> summary(candidate))
                .toList();
        return reasoner.choose(summaries).thenApplyAsync(choice -> {
            CityArchitectRoadPlanner.RoadCandidate selected = candidates.stream()
                    .filter(candidate -> candidate.id().equals(choice.candidateId()))
                    .findFirst()
                    .orElseThrow();
            try {
                CityArchitectPlanData.PlanView plan = data.create(
                        context.owner(), context.assistant(), context.dimension(), start.center(),
                        List.of(CityArchitectPlanData.StageKind.ROADS_SQUARE));
                UUID stageId = plan.stages().get(0).id();
                CityArchitectPlanData.RoadPlacement road = new CityArchitectPlanData.RoadPlacement(
                        UUID.randomUUID(), selected.centerline(), 3,
                        CityArchitectPlanData.StageKind.ROADS_SQUARE);
                data.marking(plan.id(), stageId, selected.id(), choice.explanation(),
                        List.of(), List.of(road));
                markers.queue(plan.id(), roadMarkers(selected.centerline()));
                return new RoadResult(ResultCode.MARKED, plan.id(), choice.explanation());
            } catch (IllegalStateException conflict) {
                return RoadResult.failure(ResultCode.PLAN_CONFLICT,
                        "У владельца уже есть активный план города.");
            }
        }, serverExecutor);
    }

    public static SurfaceSnapshotProvider surfaceProvider(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return (start, goal) -> {
            int distance = Math.abs(start.getX() - goal.getX()) + Math.abs(start.getZ() - goal.getZ());
            if (distance > 128) {
                return new CityArchitectRoadPlanner.RoadRequest(start, goal, Map.of(), 1);
            }
            int margin = 12;
            int minX = Math.min(start.getX(), goal.getX()) - margin;
            int maxX = Math.max(start.getX(), goal.getX()) + margin;
            int minZ = Math.min(start.getZ(), goal.getZ()) - margin;
            int maxZ = Math.max(start.getZ(), goal.getZ()) + margin;
            long area = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
            if (area > 65_536) {
                return new CityArchitectRoadPlanner.RoadRequest(start, goal, Map.of(), 1);
            }
            Map<Long, CityArchitectRoadPlanner.SurfaceCell> cells = new HashMap<>();
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos probe = new BlockPos(x, level.getMinBuildHeight(), z);
                    if (!level.hasChunkAt(probe)) {
                        continue;
                    }
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    BlockPos surface = new BlockPos(x, y, z);
                    var state = level.getBlockState(surface);
                    boolean blocked = level.getBlockEntity(surface) != null
                            || state.getDestroySpeed(level, surface) < 0;
                    boolean water = !level.getFluidState(surface).isEmpty();
                    int clearCost = level.getBlockState(surface.above()).isAir() ? 0 : 1;
                    cells.put(CityArchitectRoadPlanner.cellKey(x, z),
                            new CityArchitectRoadPlanner.SurfaceCell(surface, water, blocked, clearCost));
                }
            }
            return new CityArchitectRoadPlanner.RoadRequest(start, goal, cells, 65_536);
        };
    }

    private static CityArchitectReasoner.CandidateSummary summary(
            CityArchitectRoadPlanner.RoadCandidate candidate
    ) {
        var metrics = candidate.metrics();
        Map<String, String> facts = Map.of(
                "length", Integer.toString(metrics.length()),
                "water", Integer.toString(metrics.waterCost()),
                "elevation", Integer.toString(metrics.elevationCost()),
                "clear", Integer.toString(metrics.clearCost()),
                "turns", Integer.toString(metrics.turns()));
        return new CityArchitectReasoner.CandidateSummary(
                candidate.id(), "road", metrics.totalCost(), facts);
    }

    private static List<CityArchitectPlanData.MarkerCell> roadMarkers(List<BlockPos> centerline) {
        LinkedHashSet<BlockPos> surfaces = new LinkedHashSet<>();
        for (int index = 0; index < centerline.size(); index++) {
            BlockPos current = centerline.get(index);
            BlockPos neighbor = index + 1 < centerline.size()
                    ? centerline.get(index + 1)
                    : index > 0 ? centerline.get(index - 1) : current.east();
            int dx = Integer.signum(neighbor.getX() - current.getX());
            int dz = Integer.signum(neighbor.getZ() - current.getZ());
            surfaces.add(current.immutable());
            surfaces.add(current.offset(-dz, 0, dx).immutable());
            surfaces.add(current.offset(dz, 0, -dx).immutable());
        }
        return surfaces.stream()
                .limit(CityArchitectPlanData.MAX_MARKERS)
                .map(position -> new CityArchitectPlanData.MarkerCell(
                        position, CityArchitectPlanData.MarkerKind.ROAD))
                .toList();
    }

    private static ResultCode landmarkFailure(
            CityArchitectLandmarks.Result first, CityArchitectLandmarks.Result second
    ) {
        if (first.status() == CityArchitectLandmarks.Status.AMBIGUOUS
                || second.status() == CityArchitectLandmarks.Status.AMBIGUOUS) {
            return ResultCode.LANDMARK_AMBIGUOUS;
        }
        if (first.status() != CityArchitectLandmarks.Status.FOUND
                || second.status() != CityArchitectLandmarks.Status.FOUND) {
            return ResultCode.LANDMARK_MISSING;
        }
        return null;
    }

    private static CompletableFuture<RoadResult> completed(ResultCode code, String message) {
        return CompletableFuture.completedFuture(RoadResult.failure(code, message));
    }

    public interface SurfaceSnapshotProvider {
        CityArchitectRoadPlanner.RoadRequest snapshot(BlockPos start, BlockPos goal);
    }

    public interface MarkerSink {
        void queue(UUID planId, List<CityArchitectPlanData.MarkerCell> cells);
    }

    public record RequestContext(UUID owner, UUID assistant, String dimension, int permissionLevel) {
        public RequestContext {
            dimension = Objects.requireNonNull(dimension, "dimension").trim().toLowerCase(Locale.ROOT);
            if (dimension.isEmpty()) {
                throw new IllegalArgumentException("dimension must not be blank");
            }
        }
    }

    public enum ResultCode {
        MARKED,
        NO_PERMISSION,
        ASSISTANT_MISSING,
        INVALID_REQUEST,
        LANDMARK_MISSING,
        LANDMARK_AMBIGUOUS,
        NO_SAFE_PATH,
        PLAN_CONFLICT
    }

    public record RoadResult(ResultCode code, UUID planId, String explanation) {
        public RoadResult {
            Objects.requireNonNull(code, "code");
            explanation = Objects.requireNonNull(explanation, "explanation");
        }

        public static RoadResult failure(ResultCode code, String explanation) {
            return new RoadResult(code, null, explanation);
        }
    }
}
