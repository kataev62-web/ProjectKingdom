package ru.projectkingdom.cityarchitect;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import ru.projectkingdom.assistant.KingdomAssistantData;
import ru.projectkingdom.cityarchitect.data.CityArchitectPlanData;
import ru.projectkingdom.cityarchitect.plan.CityArchitectReasoner;
import ru.projectkingdom.cityarchitect.plan.CityArchitectRoadPlanner;
import ru.projectkingdom.cityarchitect.plan.CityArchitectPlanner;
import ru.projectkingdom.cityarchitect.world.CityArchitectLandmarks;

import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
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
    private final CityArchitectPlanner cityPlanner;

    public CityArchitectCoordinator(
            CityArchitectPlanData data,
            CityArchitectLandmarks landmarks,
            CityArchitectRoadPlanner roadPlanner,
            CityArchitectReasoner reasoner,
            SurfaceSnapshotProvider snapshots,
            MarkerSink markers,
            Executor serverExecutor
    ) {
        this(data, landmarks, roadPlanner, reasoner, snapshots, markers,
                serverExecutor, new CityArchitectPlanner());
    }

    public CityArchitectCoordinator(
            CityArchitectPlanData data,
            CityArchitectLandmarks landmarks,
            CityArchitectRoadPlanner roadPlanner,
            CityArchitectReasoner reasoner,
            SurfaceSnapshotProvider snapshots,
            MarkerSink markers,
            Executor serverExecutor,
            CityArchitectPlanner cityPlanner
    ) {
        this.data = Objects.requireNonNull(data, "data");
        this.landmarks = Objects.requireNonNull(landmarks, "landmarks");
        this.roadPlanner = Objects.requireNonNull(roadPlanner, "roadPlanner");
        this.reasoner = Objects.requireNonNull(reasoner, "reasoner");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.markers = Objects.requireNonNull(markers, "markers");
        this.serverExecutor = Objects.requireNonNull(serverExecutor, "serverExecutor");
        this.cityPlanner = Objects.requireNonNull(cityPlanner, "cityPlanner");
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

    public RoadResult requestCityPlan(
            RequestContext context, CityArchitectPlanner.CityPlanningRequest request
    ) {
        RoadResult denied = authorizeContext(context);
        if (denied != null) {
            return denied;
        }
        CityArchitectPlanner.PlanningResult result = cityPlanner.plan(request);
        if (result.candidates().isEmpty()) {
            String explanation = result.findings().isEmpty()
                    ? "Безопасный генеральный план не найден."
                    : result.findings().get(0).message();
            return RoadResult.failure(ResultCode.NO_SAFE_LAYOUT, explanation);
        }
        CityArchitectPlanner.CityCandidate candidate = result.candidates().get(0);
        try {
            CityArchitectPlanData.PlanView plan = data.create(
                    context.owner(), context.assistant(), context.dimension(), candidate.ratHall(),
                    candidate.stages().stream().map(CityArchitectPlanner.CityStage::kind).toList());
            for (int index = 0; index < candidate.stages().size(); index++) {
                CityArchitectPlanner.CityStage stage = candidate.stages().get(index);
                UUID stageId = plan.stages().get(index).id();
                data.prepareStage(plan.id(), stageId, candidate.id() + "-stage-" + (index + 1),
                        stage.explanation(), buildings(plan.id(), stage), roads(plan.id(), stage));
            }
            CityArchitectPlanData.StageView first = data.plan(plan.id()).orElseThrow().stages().get(0);
            data.marking(plan.id(), first.id(), first.candidateId(), first.explanation(),
                    first.buildings(), first.roads());
            CityArchitectPlanData.StageView marked = data.plan(plan.id()).orElseThrow().stages().get(0);
            markers.queue(plan.id(), markersForStage(marked));
            return new RoadResult(ResultCode.MARKED, plan.id(),
                    "Генеральный план рассчитан. Размечен только первый этап: " + marked.explanation());
        } catch (IllegalStateException conflict) {
            return RoadResult.failure(ResultCode.PLAN_CONFLICT,
                    "У владельца уже есть активный план города.");
        }
    }

    public RoadResult confirmCurrent(RequestContext context, UUID planId) {
        CityArchitectPlanData.PlanView plan = authorizedPlan(context, planId);
        if (plan == null) return RoadResult.failure(ResultCode.NO_PERMISSION, "Нет доступа к плану.");
        CityArchitectPlanData.StageView current = currentStage(plan);
        if (current == null || current.status() != CityArchitectPlanData.StageStatus.MARKED) {
            return RoadResult.failure(ResultCode.INVALID_STATE, "Текущий этап ещё не размечен.");
        }
        data.approve(planId, current.id());
        return new RoadResult(ResultCode.APPROVED, planId,
                "Этап подтверждён и готов к последовательной стройке.");
    }

    public RoadResult finishCurrent(RequestContext context, UUID planId) {
        CityArchitectPlanData.PlanView plan = authorizedPlan(context, planId);
        if (plan == null) return RoadResult.failure(ResultCode.NO_PERMISSION, "Нет доступа к плану.");
        CityArchitectPlanData.StageView current = currentStage(plan);
        if (current == null || current.status() != CityArchitectPlanData.StageStatus.APPROVED) {
            return RoadResult.failure(ResultCode.INVALID_STATE, "Этап не подтверждён.");
        }
        data.building(planId, current.id());
        data.completeStage(planId, current.id());
        return new RoadResult(ResultCode.STAGE_COMPLETE, planId,
                "Этап завершён. Следующий этап ожидает отдельной команды.");
    }

    public RoadResult nextStage(RequestContext context, UUID planId) {
        CityArchitectPlanData.PlanView plan = authorizedPlan(context, planId);
        if (plan == null) return RoadResult.failure(ResultCode.NO_PERMISSION, "Нет доступа к плану.");
        if (plan.status() == CityArchitectPlanData.PlanStatus.COMPLETE) {
            return new RoadResult(ResultCode.PLAN_COMPLETE, planId, "Городской план полностью завершён.");
        }
        CityArchitectPlanData.StageView current = currentStage(plan);
        if (current == null || current.status() != CityArchitectPlanData.StageStatus.PENDING
                || current.candidateId().isBlank()) {
            return RoadResult.failure(ResultCode.INVALID_STATE,
                    "Сначала завершите подтверждённый текущий этап.");
        }
        data.marking(planId, current.id(), current.candidateId(), current.explanation(),
                current.buildings(), current.roads());
        CityArchitectPlanData.StageView marked = data.plan(planId).orElseThrow().stages().stream()
                .filter(stage -> stage.id().equals(current.id())).findFirst().orElseThrow();
        markers.queue(planId, markersForStage(marked));
        return new RoadResult(ResultCode.MARKED, planId,
                "Размечен следующий этап: " + marked.explanation());
    }

    public RoadResult cancelPlan(RequestContext context, UUID planId) {
        CityArchitectPlanData.PlanView plan = authorizedPlan(context, planId);
        if (plan == null) return RoadResult.failure(ResultCode.NO_PERMISSION, "Нет доступа к плану.");
        data.cancel(planId);
        return new RoadResult(ResultCode.CANCELLED, planId, "План отменён; разметку можно восстановить.");
    }

    public RoadResult status(RequestContext context) {
        RoadResult denied = authorizeContext(context);
        if (denied != null) return denied;
        var plan = data.ownedBy(context.owner());
        if (plan.isEmpty()) return RoadResult.failure(ResultCode.NO_ACTIVE_PLAN, "Активного плана города нет.");
        CityArchitectPlanData.PlanView value = plan.orElseThrow();
        CityArchitectPlanData.StageView current = currentStage(value);
        String stage = current == null ? "все этапы завершены" : "этап " + (current.index() + 1)
                + ": " + current.status().name().toLowerCase(Locale.ROOT);
        return new RoadResult(ResultCode.STATUS, value.id(), "План " + value.status() + ", " + stage);
    }

    private RoadResult authorizeContext(RequestContext context) {
        if (context == null || context.permissionLevel() < 4)
            return RoadResult.failure(ResultCode.NO_PERMISSION, "Требуется уровень прав 4.");
        if (context.owner() == null || context.assistant() == null)
            return RoadResult.failure(ResultCode.ASSISTANT_MISSING, "Личный помощник не найден.");
        return null;
    }

    private CityArchitectPlanData.PlanView authorizedPlan(RequestContext context, UUID planId) {
        if (authorizeContext(context) != null) return null;
        return data.plan(planId).filter(plan -> plan.owner().equals(context.owner())
                && plan.assistant().equals(context.assistant())
                && plan.dimension().equals(context.dimension())).orElse(null);
    }

    private static CityArchitectPlanData.StageView currentStage(CityArchitectPlanData.PlanView plan) {
        return plan.stages().stream()
                .filter(stage -> stage.status() != CityArchitectPlanData.StageStatus.COMPLETE)
                .findFirst().orElse(null);
    }

    private static List<CityArchitectPlanData.BuildingPlacement> buildings(
            UUID planId, CityArchitectPlanner.CityStage stage
    ) {
        return stage.buildings().stream().map(value -> new CityArchitectPlanData.BuildingPlacement(
                stableId(planId, "building:" + stage.index() + ":" + value.role()),
                value.schematicKey(), value.origin(), value.rotation(), stage.kind(),
                value.width(), value.length())).toList();
    }

    private static List<CityArchitectPlanData.RoadPlacement> roads(
            UUID planId, CityArchitectPlanner.CityStage stage
    ) {
        List<CityArchitectPlanData.RoadPlacement> result = new ArrayList<>();
        for (int index = 0; index < stage.roads().size(); index++) {
            CityArchitectPlanner.RoadPlacement value = stage.roads().get(index);
            result.add(new CityArchitectPlanData.RoadPlacement(
                    stableId(planId, "road:" + stage.index() + ":" + index),
                    value.centerline(), value.width(), stage.kind()));
        }
        return List.copyOf(result);
    }

    private static UUID stableId(UUID planId, String value) {
        return UUID.nameUUIDFromBytes((planId + ":" + value).getBytes(StandardCharsets.UTF_8));
    }

    public static List<CityArchitectPlanData.MarkerCell> markersForStage(
            CityArchitectPlanData.StageView stage
    ) {
        LinkedHashSet<CityArchitectPlanData.MarkerCell> result = new LinkedHashSet<>();
        stage.roads().forEach(road -> result.addAll(roadMarkers(road.centerline())));
        CityArchitectPlanData.MarkerKind kind = markerKind(stage.kind());
        for (CityArchitectPlanData.BuildingPlacement building : stage.buildings()) {
            int minX = building.origin().getX();
            int minZ = building.origin().getZ();
            int maxX = minX + building.width() - 1;
            int maxZ = minZ + building.length() - 1;
            int y = building.origin().getY();
            for (int x = minX; x <= maxX; x++) {
                result.add(new CityArchitectPlanData.MarkerCell(new BlockPos(x, y, minZ), kind));
                result.add(new CityArchitectPlanData.MarkerCell(new BlockPos(x, y, maxZ), kind));
            }
            for (int z = minZ; z <= maxZ; z++) {
                result.add(new CityArchitectPlanData.MarkerCell(new BlockPos(minX, y, z), kind));
                result.add(new CityArchitectPlanData.MarkerCell(new BlockPos(maxX, y, z), kind));
            }
        }
        return result.stream().limit(CityArchitectPlanData.MAX_MARKERS).toList();
    }

    private static CityArchitectPlanData.MarkerKind markerKind(CityArchitectPlanData.StageKind kind) {
        return switch (kind) {
            case ROADS_SQUARE, SECONDARY_INFRASTRUCTURE -> CityArchitectPlanData.MarkerKind.ROAD;
            case ADMINISTRATION -> CityArchitectPlanData.MarkerKind.ADMINISTRATION;
            case PRODUCTION -> CityArchitectPlanData.MarkerKind.PRODUCTION;
            case HOUSING_HOSPITALITY -> CityArchitectPlanData.MarkerKind.HOUSING;
            case EXPANSION -> CityArchitectPlanData.MarkerKind.WATER_PORT;
        };
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
        APPROVED,
        STAGE_COMPLETE,
        PLAN_COMPLETE,
        CANCELLED,
        STATUS,
        NO_PERMISSION,
        ASSISTANT_MISSING,
        INVALID_REQUEST,
        LANDMARK_MISSING,
        LANDMARK_AMBIGUOUS,
        NO_SAFE_PATH,
        NO_SAFE_LAYOUT,
        NO_ACTIVE_PLAN,
        INVALID_STATE,
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
