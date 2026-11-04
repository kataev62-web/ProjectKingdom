package ru.projectkingdom.cityarchitect.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CityArchitectPlanData extends SavedData {
    public static final String DATA_KEY = "kingdom_city_architect_plans_v1";
    public static final int MAX_STAGES = 6;
    public static final int MAX_BUILDINGS = 16;
    public static final int MAX_MARKERS = 8_192;
    private static final int MAX_ROADS = 64;
    private static final int MAX_COMPLETED = 128;

    private final Map<UUID, MutablePlan> plans = new LinkedHashMap<>();
    private int quarantinedRows;

    public static CityArchitectPlanData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                CityArchitectPlanData::load, CityArchitectPlanData::new, DATA_KEY);
    }

    public synchronized PlanView create(
            UUID owner,
            UUID assistant,
            String dimension,
            BlockPos anchor,
            List<StageKind> stageKinds
    ) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(assistant, "assistant");
        String checkedDimension = requireText(dimension, "dimension");
        Objects.requireNonNull(anchor, "anchor");
        List<StageKind> kinds = List.copyOf(Objects.requireNonNull(stageKinds, "stageKinds"));
        if (kinds.isEmpty() || kinds.size() > MAX_STAGES || kinds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("A plan requires between 1 and " + MAX_STAGES + " stages");
        }
        if (ownedBy(owner).isPresent()) {
            throw new IllegalStateException("Owner already has an active city plan");
        }
        MutablePlan plan = new MutablePlan(
                UUID.randomUUID(), owner, assistant, checkedDimension, anchor.immutable(), PlanStatus.DRAFT);
        for (int index = 0; index < kinds.size(); index++) {
            plan.stages.add(new MutableStage(UUID.randomUUID(), index, kinds.get(index)));
        }
        plans.put(plan.id, plan);
        setDirty();
        return plan.view();
    }

    public synchronized Optional<PlanView> ownedBy(UUID owner) {
        return plans.values().stream()
                .filter(plan -> plan.owner.equals(owner) && plan.status.active())
                .map(MutablePlan::view)
                .findFirst();
    }

    public synchronized Optional<PlanView> plan(UUID planId) {
        MutablePlan plan = plans.get(planId);
        return plan == null ? Optional.empty() : Optional.of(plan.view());
    }

    public synchronized List<PlanView> allPlans() {
        return plans.values().stream().map(MutablePlan::view).toList();
    }

    public synchronized void marking(
            UUID planId,
            UUID stageId,
            String candidateId,
            String explanation,
            List<BuildingPlacement> buildings,
            List<RoadPlacement> roads
    ) {
        MutablePlan plan = requirePlan(planId);
        requirePlanStatus(plan, PlanStatus.DRAFT);
        MutableStage stage = requireCurrentStage(plan, stageId, StageStatus.PENDING);
        List<BuildingPlacement> buildingCopy = List.copyOf(Objects.requireNonNull(buildings, "buildings"));
        List<RoadPlacement> roadCopy = List.copyOf(Objects.requireNonNull(roads, "roads"));
        int existingBuildings = plan.stages.stream().mapToInt(value -> value.buildings.size()).sum()
                - stage.buildings.size();
        int existingRoads = plan.stages.stream().mapToInt(value -> value.roads.size()).sum()
                - stage.roads.size();
        if (existingBuildings + buildingCopy.size() > MAX_BUILDINGS) {
            throw new IllegalArgumentException("Plan exceeds the building limit");
        }
        if (existingRoads + roadCopy.size() > MAX_ROADS) {
            throw new IllegalArgumentException("Plan exceeds the road limit");
        }
        stage.candidateId = requireText(candidateId, "candidateId");
        stage.explanation = requireText(explanation, "explanation");
        stage.buildings = buildingCopy;
        stage.roads = roadCopy;
        stage.status = StageStatus.MARKED;
        plan.status = PlanStatus.MARKED;
        setDirty();
    }

    public synchronized void prepareStage(
            UUID planId,
            UUID stageId,
            String candidateId,
            String explanation,
            List<BuildingPlacement> buildings,
            List<RoadPlacement> roads
    ) {
        MutablePlan plan = requirePlan(planId);
        requirePlanStatus(plan, PlanStatus.DRAFT);
        MutableStage stage = requireStage(plan, stageId);
        if (stage.status != StageStatus.PENDING) {
            throw new IllegalStateException("Only a pending stage can be prepared");
        }
        List<BuildingPlacement> buildingCopy = List.copyOf(Objects.requireNonNull(buildings, "buildings"));
        List<RoadPlacement> roadCopy = List.copyOf(Objects.requireNonNull(roads, "roads"));
        int buildingsAfter = plan.stages.stream().mapToInt(value -> value.buildings.size()).sum()
                - stage.buildings.size() + buildingCopy.size();
        int roadsAfter = plan.stages.stream().mapToInt(value -> value.roads.size()).sum()
                - stage.roads.size() + roadCopy.size();
        if (buildingsAfter > MAX_BUILDINGS || roadsAfter > MAX_ROADS) {
            throw new IllegalArgumentException("Prepared stage exceeds plan limits");
        }
        stage.candidateId = requireText(candidateId, "candidateId");
        stage.explanation = requireText(explanation, "explanation");
        stage.buildings = buildingCopy;
        stage.roads = roadCopy;
        setDirty();
    }

    public synchronized void approve(UUID planId, UUID stageId) {
        MutablePlan plan = requirePlan(planId);
        requirePlanStatus(plan, PlanStatus.MARKED);
        MutableStage stage = requireCurrentStage(plan, stageId, StageStatus.MARKED);
        stage.status = StageStatus.APPROVED;
        plan.status = PlanStatus.APPROVED;
        setDirty();
    }

    public synchronized void building(UUID planId, UUID stageId) {
        MutablePlan plan = requirePlan(planId);
        requirePlanStatus(plan, PlanStatus.APPROVED);
        MutableStage stage = requireCurrentStage(plan, stageId, StageStatus.APPROVED);
        stage.status = StageStatus.BUILDING;
        plan.status = PlanStatus.BUILDING;
        setDirty();
    }

    public synchronized void completeStage(UUID planId, UUID stageId) {
        MutablePlan plan = requirePlan(planId);
        requirePlanStatus(plan, PlanStatus.BUILDING);
        MutableStage stage = requireCurrentStage(plan, stageId, StageStatus.BUILDING);
        stage.status = StageStatus.COMPLETE;
        plan.markerChanges.clear();
        if (plan.stages.stream().allMatch(value -> value.status == StageStatus.COMPLETE)) {
            plan.status = PlanStatus.COMPLETE;
        } else {
            plan.status = PlanStatus.DRAFT;
        }
        setDirty();
    }

    public synchronized void failStage(UUID planId, UUID stageId, String failure) {
        MutablePlan plan = requirePlan(planId);
        if (!plan.status.active()) {
            throw new IllegalStateException("Plan is already terminal");
        }
        MutableStage stage = requireStage(plan, stageId);
        if (stage.status.terminal()) {
            throw new IllegalStateException("Stage is already terminal");
        }
        stage.status = StageStatus.FAILED;
        plan.status = PlanStatus.FAILED;
        plan.failure = requireText(failure, "failure");
        setDirty();
    }

    public synchronized void cancel(UUID planId) {
        MutablePlan plan = requirePlan(planId);
        if (!plan.status.active()) {
            throw new IllegalStateException("Plan is already terminal");
        }
        plan.status = PlanStatus.CANCELLED;
        plan.stages.stream()
                .filter(stage -> !stage.status.terminal())
                .forEach(stage -> stage.status = StageStatus.CANCELLED);
        setDirty();
    }

    public synchronized void recordMarkerChange(UUID planId, MarkerChange change) {
        MutablePlan plan = requirePlan(planId);
        if (!plan.status.active()) {
            throw new IllegalStateException("Plan is terminal");
        }
        if (plan.markerChanges.size() >= MAX_MARKERS) {
            throw new IllegalStateException("Plan exceeds the marker limit");
        }
        plan.markerChanges.add(Objects.requireNonNull(change, "change"));
        currentStage(plan).ifPresent(stage -> stage.markerCursor++);
        setDirty();
    }

    public synchronized void updateMarkerChange(UUID planId, int index, MarkerChange change) {
        MutablePlan plan = requirePlan(planId);
        plan.markerChanges.set(index, Objects.requireNonNull(change, "change"));
        setDirty();
    }

    public synchronized void clearMarkerChanges(UUID planId) {
        requirePlan(planId).markerChanges.clear();
        setDirty();
    }

    public synchronized void recordCompletedPlacement(UUID planId, CompletedPlacement placement) {
        MutablePlan plan = requirePlan(planId);
        if (plan.completedPlacements.size() >= MAX_COMPLETED) {
            throw new IllegalStateException("Plan exceeds the completed-placement limit");
        }
        plan.completedPlacements.add(Objects.requireNonNull(placement, "placement"));
        currentStage(plan).ifPresent(stage -> stage.buildCursor++);
        setDirty();
    }

    public synchronized int quarantinedRows() {
        return quarantinedRows;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag root) {
        ListTag rows = new ListTag();
        plans.values().forEach(plan -> rows.add(plan.save()));
        root.put("Plans", rows);
        return root;
    }

    public static CityArchitectPlanData load(CompoundTag root) {
        CityArchitectPlanData data = new CityArchitectPlanData();
        ListTag rows = root.getList("Plans", Tag.TAG_COMPOUND);
        for (Tag row : rows) {
            try {
                MutablePlan plan = MutablePlan.load((CompoundTag) row);
                if (data.plans.putIfAbsent(plan.id, plan) != null) {
                    throw new IllegalArgumentException("Duplicate plan id");
                }
            } catch (RuntimeException corrupt) {
                data.quarantinedRows++;
            }
        }
        return data;
    }

    private MutablePlan requirePlan(UUID planId) {
        MutablePlan plan = plans.get(Objects.requireNonNull(planId, "planId"));
        if (plan == null) {
            throw new IllegalArgumentException("Unknown plan");
        }
        return plan;
    }

    private static void requirePlanStatus(MutablePlan plan, PlanStatus expected) {
        if (plan.status != expected) {
            throw new IllegalStateException("Expected plan status " + expected + " but was " + plan.status);
        }
    }

    private static MutableStage requireCurrentStage(
            MutablePlan plan, UUID stageId, StageStatus expected
    ) {
        MutableStage stage = requireStage(plan, stageId);
        MutableStage current = currentStage(plan).orElseThrow(() -> new IllegalStateException("Plan has no active stage"));
        if (stage != current || stage.status != expected) {
            throw new IllegalStateException("Stage is not the current " + expected + " stage");
        }
        return stage;
    }

    private static MutableStage requireStage(MutablePlan plan, UUID stageId) {
        return plan.stages.stream()
                .filter(stage -> stage.id.equals(stageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown stage"));
    }

    private static Optional<MutableStage> currentStage(MutablePlan plan) {
        return plan.stages.stream().filter(stage -> stage.status != StageStatus.COMPLETE).findFirst();
    }

    private static String requireText(String value, String name) {
        String checked = Objects.requireNonNull(value, name).trim();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return checked;
    }

    public enum PlanStatus {
        DRAFT, MARKED, APPROVED, BUILDING, COMPLETE, CANCELLED, FAILED;

        public boolean active() {
            return this != COMPLETE && this != CANCELLED && this != FAILED;
        }
    }

    public enum StageStatus {
        PENDING, MARKED, APPROVED, BUILDING, COMPLETE, CANCELLED, FAILED;

        public boolean terminal() {
            return this == COMPLETE || this == CANCELLED || this == FAILED;
        }
    }

    public enum StageKind {
        ROADS_SQUARE,
        ADMINISTRATION,
        PRODUCTION,
        HOUSING_HOSPITALITY,
        SECONDARY_INFRASTRUCTURE,
        EXPANSION
    }

    public enum MarkerKind {
        ROAD,
        ADMINISTRATION,
        PRODUCTION,
        COMMERCE_STORAGE,
        HOUSING,
        WATER_PORT
    }

    public record BuildingPlacement(
            UUID id,
            String schematicKey,
            BlockPos origin,
            int rotation,
            StageKind stageKind,
            int width,
            int length
    ) {
        public BuildingPlacement(
                UUID id, String schematicKey, BlockPos origin, int rotation, StageKind stageKind
        ) {
            this(id, schematicKey, origin, rotation, stageKind, 1, 1);
        }

        public BuildingPlacement {
            Objects.requireNonNull(id, "id");
            schematicKey = requireText(schematicKey, "schematicKey");
            origin = Objects.requireNonNull(origin, "origin").immutable();
            Objects.requireNonNull(stageKind, "stageKind");
            if (rotation < 0 || rotation > 3) {
                throw new IllegalArgumentException("rotation must be 0..3");
            }
            if (width < 1 || length < 1 || width > 64 || length > 64) {
                throw new IllegalArgumentException("Invalid building footprint");
            }
        }
    }

    public record RoadPlacement(UUID id, List<BlockPos> centerline, int width, StageKind stageKind) {
        public RoadPlacement {
            Objects.requireNonNull(id, "id");
            centerline = List.copyOf(Objects.requireNonNull(centerline, "centerline"));
            Objects.requireNonNull(stageKind, "stageKind");
            if (centerline.isEmpty() || centerline.size() > MAX_MARKERS || width < 1 || width > 7) {
                throw new IllegalArgumentException("Invalid road placement");
            }
        }
    }

    public record CompletedPlacement(String kind, String key, BlockPos origin, long completedGameTime) {
        public CompletedPlacement {
            kind = requireText(kind, "kind");
            key = requireText(key, "key");
            origin = Objects.requireNonNull(origin, "origin").immutable();
            if (completedGameTime < 0) {
                throw new IllegalArgumentException("completedGameTime must be non-negative");
            }
        }
    }

    public record MarkerCell(BlockPos position, MarkerKind kind) {
        public MarkerCell {
            position = Objects.requireNonNull(position, "position").immutable();
            Objects.requireNonNull(kind, "kind");
        }
    }

    public record MarkerChange(
            BlockPos position,
            MarkerKind kind,
            String beforeState,
            String markerState,
            boolean restored,
            boolean conflict
    ) {
        public MarkerChange {
            position = Objects.requireNonNull(position, "position").immutable();
            Objects.requireNonNull(kind, "kind");
            beforeState = requireText(beforeState, "beforeState");
            markerState = requireText(markerState, "markerState");
        }

        public MarkerChange restored(boolean conflict) {
            return new MarkerChange(position, kind, beforeState, markerState, true, conflict);
        }
    }

    public record StageView(
            UUID id,
            int index,
            StageKind kind,
            StageStatus status,
            String candidateId,
            String explanation,
            List<BuildingPlacement> buildings,
            List<RoadPlacement> roads,
            int markerCursor,
            int buildCursor
    ) {
        public StageView {
            buildings = List.copyOf(buildings);
            roads = List.copyOf(roads);
        }
    }

    public record PlanView(
            UUID id,
            UUID owner,
            UUID assistant,
            String dimension,
            BlockPos anchor,
            PlanStatus status,
            List<StageView> stages,
            List<MarkerChange> markerChanges,
            List<CompletedPlacement> completedPlacements,
            String failure
    ) {
        public PlanView {
            anchor = anchor.immutable();
            stages = List.copyOf(stages);
            markerChanges = List.copyOf(markerChanges);
            completedPlacements = List.copyOf(completedPlacements);
        }
    }

    private static final class MutablePlan {
        private final UUID id;
        private final UUID owner;
        private final UUID assistant;
        private final String dimension;
        private final BlockPos anchor;
        private PlanStatus status;
        private final List<MutableStage> stages = new ArrayList<>();
        private final List<MarkerChange> markerChanges = new ArrayList<>();
        private final List<CompletedPlacement> completedPlacements = new ArrayList<>();
        private String failure = "";

        private MutablePlan(
                UUID id, UUID owner, UUID assistant, String dimension, BlockPos anchor, PlanStatus status
        ) {
            this.id = Objects.requireNonNull(id);
            this.owner = Objects.requireNonNull(owner);
            this.assistant = Objects.requireNonNull(assistant);
            this.dimension = requireText(dimension, "dimension");
            this.anchor = Objects.requireNonNull(anchor).immutable();
            this.status = Objects.requireNonNull(status);
        }

        private PlanView view() {
            return new PlanView(id, owner, assistant, dimension, anchor, status,
                    stages.stream().map(MutableStage::view).toList(),
                    markerChanges, completedPlacements, failure);
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putUUID("Owner", owner);
            tag.putUUID("Assistant", assistant);
            tag.putString("Dimension", dimension);
            tag.putLong("Anchor", anchor.asLong());
            tag.putString("Status", status.name());
            tag.putString("Failure", failure);
            ListTag stageRows = new ListTag();
            stages.forEach(stage -> stageRows.add(stage.save()));
            tag.put("Stages", stageRows);
            ListTag markerRows = new ListTag();
            markerChanges.forEach(change -> markerRows.add(saveMarker(change)));
            tag.put("Markers", markerRows);
            ListTag completedRows = new ListTag();
            completedPlacements.forEach(value -> completedRows.add(saveCompleted(value)));
            tag.put("Completed", completedRows);
            return tag;
        }

        private static MutablePlan load(CompoundTag tag) {
            if (!tag.hasUUID("Id") || !tag.hasUUID("Owner") || !tag.hasUUID("Assistant")
                    || !tag.contains("Dimension", Tag.TAG_STRING)
                    || !tag.contains("Anchor", Tag.TAG_LONG)
                    || !tag.contains("Status", Tag.TAG_STRING)) {
                throw new IllegalArgumentException("Missing plan fields");
            }
            MutablePlan plan = new MutablePlan(tag.getUUID("Id"), tag.getUUID("Owner"),
                    tag.getUUID("Assistant"), tag.getString("Dimension"), BlockPos.of(tag.getLong("Anchor")),
                    PlanStatus.valueOf(tag.getString("Status")));
            plan.failure = tag.getString("Failure");
            ListTag stageRows = tag.getList("Stages", Tag.TAG_COMPOUND);
            if (stageRows.isEmpty() || stageRows.size() > MAX_STAGES) {
                throw new IllegalArgumentException("Invalid stages");
            }
            for (Tag row : stageRows) {
                plan.stages.add(MutableStage.load((CompoundTag) row));
            }
            ListTag markerRows = tag.getList("Markers", Tag.TAG_COMPOUND);
            if (markerRows.size() > MAX_MARKERS) {
                throw new IllegalArgumentException("Too many markers");
            }
            for (Tag row : markerRows) {
                plan.markerChanges.add(loadMarker((CompoundTag) row));
            }
            ListTag completedRows = tag.getList("Completed", Tag.TAG_COMPOUND);
            if (completedRows.size() > MAX_COMPLETED) {
                throw new IllegalArgumentException("Too many completed placements");
            }
            for (Tag row : completedRows) {
                plan.completedPlacements.add(loadCompleted((CompoundTag) row));
            }
            int buildings = plan.stages.stream().mapToInt(value -> value.buildings.size()).sum();
            if (buildings > MAX_BUILDINGS) {
                throw new IllegalArgumentException("Too many buildings");
            }
            return plan;
        }
    }

    private static final class MutableStage {
        private final UUID id;
        private final int index;
        private final StageKind kind;
        private StageStatus status = StageStatus.PENDING;
        private String candidateId = "";
        private String explanation = "";
        private List<BuildingPlacement> buildings = List.of();
        private List<RoadPlacement> roads = List.of();
        private int markerCursor;
        private int buildCursor;

        private MutableStage(UUID id, int index, StageKind kind) {
            this.id = Objects.requireNonNull(id);
            this.index = index;
            this.kind = Objects.requireNonNull(kind);
        }

        private StageView view() {
            return new StageView(id, index, kind, status, candidateId, explanation,
                    buildings, roads, markerCursor, buildCursor);
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putInt("Index", index);
            tag.putString("Kind", kind.name());
            tag.putString("Status", status.name());
            tag.putString("Candidate", candidateId);
            tag.putString("Explanation", explanation);
            tag.putInt("MarkerCursor", markerCursor);
            tag.putInt("BuildCursor", buildCursor);
            ListTag buildingRows = new ListTag();
            buildings.forEach(value -> buildingRows.add(saveBuilding(value)));
            tag.put("Buildings", buildingRows);
            ListTag roadRows = new ListTag();
            roads.forEach(value -> roadRows.add(saveRoad(value)));
            tag.put("Roads", roadRows);
            return tag;
        }

        private static MutableStage load(CompoundTag tag) {
            if (!tag.hasUUID("Id") || !tag.contains("Kind", Tag.TAG_STRING)
                    || !tag.contains("Status", Tag.TAG_STRING)) {
                throw new IllegalArgumentException("Missing stage fields");
            }
            MutableStage stage = new MutableStage(tag.getUUID("Id"), tag.getInt("Index"),
                    StageKind.valueOf(tag.getString("Kind")));
            stage.status = StageStatus.valueOf(tag.getString("Status"));
            stage.candidateId = tag.getString("Candidate");
            stage.explanation = tag.getString("Explanation");
            stage.markerCursor = Math.max(0, tag.getInt("MarkerCursor"));
            stage.buildCursor = Math.max(0, tag.getInt("BuildCursor"));
            ListTag buildingRows = tag.getList("Buildings", Tag.TAG_COMPOUND);
            if (buildingRows.size() > MAX_BUILDINGS) {
                throw new IllegalArgumentException("Too many buildings");
            }
            List<BuildingPlacement> buildings = new ArrayList<>();
            for (Tag row : buildingRows) {
                buildings.add(loadBuilding((CompoundTag) row));
            }
            stage.buildings = List.copyOf(buildings);
            ListTag roadRows = tag.getList("Roads", Tag.TAG_COMPOUND);
            if (roadRows.size() > MAX_ROADS) {
                throw new IllegalArgumentException("Too many roads");
            }
            List<RoadPlacement> roads = new ArrayList<>();
            for (Tag row : roadRows) {
                roads.add(loadRoad((CompoundTag) row));
            }
            stage.roads = List.copyOf(roads);
            return stage;
        }
    }

    private static CompoundTag saveBuilding(BuildingPlacement value) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", value.id());
        tag.putString("Key", value.schematicKey());
        tag.putLong("Origin", value.origin().asLong());
        tag.putInt("Rotation", value.rotation());
        tag.putString("Stage", value.stageKind().name());
        tag.putInt("Width", value.width());
        tag.putInt("Length", value.length());
        return tag;
    }

    private static BuildingPlacement loadBuilding(CompoundTag tag) {
        return new BuildingPlacement(tag.getUUID("Id"), tag.getString("Key"),
                BlockPos.of(tag.getLong("Origin")), tag.getInt("Rotation"),
                StageKind.valueOf(tag.getString("Stage")),
                Math.max(1, tag.getInt("Width")), Math.max(1, tag.getInt("Length")));
    }

    private static CompoundTag saveRoad(RoadPlacement value) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", value.id());
        tag.putLongArray("Points", value.centerline().stream().mapToLong(BlockPos::asLong).toArray());
        tag.putInt("Width", value.width());
        tag.putString("Stage", value.stageKind().name());
        return tag;
    }

    private static RoadPlacement loadRoad(CompoundTag tag) {
        return new RoadPlacement(tag.getUUID("Id"),
                java.util.Arrays.stream(tag.getLongArray("Points")).mapToObj(BlockPos::of).toList(),
                tag.getInt("Width"), StageKind.valueOf(tag.getString("Stage")));
    }

    private static CompoundTag saveMarker(MarkerChange value) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Pos", value.position().asLong());
        tag.putString("Kind", value.kind().name());
        tag.putString("Before", value.beforeState());
        tag.putString("Marker", value.markerState());
        tag.putBoolean("Restored", value.restored());
        tag.putBoolean("Conflict", value.conflict());
        return tag;
    }

    private static MarkerChange loadMarker(CompoundTag tag) {
        return new MarkerChange(BlockPos.of(tag.getLong("Pos")),
                MarkerKind.valueOf(tag.getString("Kind")), tag.getString("Before"),
                tag.getString("Marker"), tag.getBoolean("Restored"), tag.getBoolean("Conflict"));
    }

    private static CompoundTag saveCompleted(CompletedPlacement value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Kind", value.kind());
        tag.putString("Key", value.key());
        tag.putLong("Origin", value.origin().asLong());
        tag.putLong("Time", value.completedGameTime());
        return tag;
    }

    private static CompletedPlacement loadCompleted(CompoundTag tag) {
        return new CompletedPlacement(tag.getString("Kind"), tag.getString("Key"),
                BlockPos.of(tag.getLong("Origin")), tag.getLong("Time"));
    }
}
