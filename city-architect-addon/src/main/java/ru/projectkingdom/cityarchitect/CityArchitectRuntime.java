package ru.projectkingdom.cityarchitect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ru.projectkingdom.cityarchitect.command.CityArchitectCommand;
import ru.projectkingdom.cityarchitect.command.CityArchitectCommandParser;
import ru.projectkingdom.cityarchitect.command.CityArchitectIntent;
import ru.projectkingdom.cityarchitect.data.CityArchitectPlanData;
import ru.projectkingdom.cityarchitect.network.CityArchitectNetwork;
import ru.projectkingdom.cityarchitect.network.CityArchitectPreviewPacket;
import ru.projectkingdom.cityarchitect.plan.CityArchitectPlanner;
import ru.projectkingdom.cityarchitect.plan.CityArchitectReasoner;
import ru.projectkingdom.cityarchitect.plan.CityArchitectRoadPlanner;
import ru.projectkingdom.cityarchitect.plan.DisabledCityArchitectModelClient;
import ru.projectkingdom.cityarchitect.world.CityArchitectBuildService;
import ru.projectkingdom.cityarchitect.world.CityArchitectLandmarks;
import ru.projectkingdom.cityarchitect.world.CityArchitectMarkerService;
import ru.projectkingdom.cityarchitect.world.CityArchitectRoadDesignFactory;
import ru.projectkingdom.npc.KingdomBlockStateCodec;
import ru.projectkingdom.npc.KingdomBuildingTypes;
import ru.projectkingdom.npc.KingdomConstructionData;
import ru.projectkingdom.npc.KingdomConstructionPlans;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class CityArchitectRuntime {
    private final Map<String, CityArchitectMarkerService> markerServices = new HashMap<>();
    private final Map<String, CityArchitectBuildService> buildServices = new HashMap<>();
    private final Set<UUID> resumedMarkerPlans = new LinkedHashSet<>();
    private final CityArchitectRoadDesignFactory roadDesigns = new CityArchitectRoadDesignFactory();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void addressedChat(ServerChatEvent event) {
        if (CityArchitectChatRouting.route(event.getRawText())
                != CityArchitectChatRouting.Route.CITY_ARCHITECT) {
            return;
        }
        event.setCanceled(true);
        ServerPlayer player = event.getPlayer();
        Runnable action = () -> CityArchitectCommandParser.parse(event.getRawText())
                .ifPresent(command -> execute(player, command));
        if (player.getServer().isSameThread()) action.run();
        else player.getServer().execute(action);
    }

    private void execute(ServerPlayer player, CityArchitectCommand command) {
        CityArchitectCoordinator coordinator = coordinator(player.serverLevel());
        CityArchitectCoordinator.RequestContext context = context(player);
        switch (command.intent()) {
            case ROAD -> coordinator.requestRoad(player, command.argument())
                    .thenAccept(result -> player.getServer().execute(() -> deliver(player, result, true)));
            case CITY_PLAN -> requestCityPlan(player, coordinator, context);
            case NEXT_STAGE -> activePlan(player).ifPresentOrElse(plan ->
                            deliver(player, coordinator.nextStage(context, plan.id()), true),
                    () -> message(player, "Активного плана города нет."));
            case CONFIRM -> activePlan(player).ifPresentOrElse(plan -> confirm(player, plan),
                    () -> message(player, "Активного плана города нет."));
            case CANCEL_MARKERS -> activePlan(player).ifPresentOrElse(plan -> cancel(player, plan),
                    () -> message(player, "Активного плана города нет."));
            case STATUS -> deliver(player, coordinator.status(context), false);
            case REPLAN -> replan(player, coordinator, context);
        }
    }

    private void requestCityPlan(
            ServerPlayer player,
            CityArchitectCoordinator coordinator,
            CityArchitectCoordinator.RequestContext context
    ) {
        CityArchitectLandmarks.Result hall = CityArchitectLandmarks
                .fromKingdom(player.getServer(), CityArchitectPlanData.get(player.getServer()))
                .resolve("ратуша", context.dimension());
        if (hall.status() != CityArchitectLandmarks.Status.FOUND) {
            message(player, hall.status() == CityArchitectLandmarks.Status.AMBIGUOUS
                    ? "Найдено несколько ратуш. Уточните город регистрацией единственной активной ратуши."
                    : "Ратуша не найдена. Сначала зарегистрируйте ратушу.");
            return;
        }
        CityArchitectPlanner.CityPlanningRequest request = cityRequest(
                player.serverLevel(), hall.unique().orElseThrow().center(), 64);
        deliver(player, coordinator.requestCityPlan(context, request), true);
    }

    private void replan(
            ServerPlayer player,
            CityArchitectCoordinator coordinator,
            CityArchitectCoordinator.RequestContext context
    ) {
        var active = activePlan(player);
        if (active.isPresent()) {
            marker(player.serverLevel()).restore(active.orElseThrow().id());
            coordinator.cancelPlan(context, active.orElseThrow().id());
        }
        requestCityPlan(player, coordinator, context);
    }

    private void cancel(ServerPlayer player, CityArchitectPlanData.PlanView plan) {
        CityArchitectMarkerService.RestoreResult restored = marker(player.serverLevel()).restore(plan.id());
        CityArchitectCoordinator.RoadResult result = coordinator(player.serverLevel())
                .cancelPlan(context(player), plan.id());
        message(player, result.explanation() + " Восстановлено: " + restored.restored()
                + ", конфликтов: " + restored.conflicts() + ".");
    }

    private void confirm(ServerPlayer player, CityArchitectPlanData.PlanView plan) {
        CityArchitectPlanData.StageView current = currentStage(plan);
        if (current == null || current.status() != CityArchitectPlanData.StageStatus.MARKED) {
            message(player, "Текущий этап ещё не готов к подтверждению.");
            return;
        }
        CityArchitectMarkerService.RestoreResult restored = marker(player.serverLevel()).restore(plan.id());
        if (restored.paused() > 0) {
            message(player, "Не все чанки разметки загружены. Посетите их и подтвердите снова.");
            return;
        }
        CityArchitectCoordinator.RoadResult approved = coordinator(player.serverLevel())
                .confirmCurrent(context(player), plan.id());
        if (approved.code() != CityArchitectCoordinator.ResultCode.APPROVED) {
            deliver(player, approved, false);
            return;
        }
        try {
            List<CityArchitectRoadDesignFactory.RoadCell> cells = buildCells(
                    player.getServer(), current);
            build(player.serverLevel()).queue(plan.id(), current.id(), plan.dimension(), cells);
            CityArchitectPlanData.get(player.getServer()).building(plan.id(), current.id());
            message(player, "Этап подтверждён. Стройка началась по 16 блоков за тик.");
        } catch (RuntimeException failure) {
            CityArchitectPlanData.get(player.getServer()).failStage(
                    plan.id(), current.id(), "Не удалось создать безопасную очередь: " + failure.getMessage());
            message(player, "Этап остановлен: " + failure.getMessage());
        }
    }

    void onDecision(CityArchitectNetwork.AuthorizedDecision decision) {
        var plan = CityArchitectPlanData.get(decision.player().getServer())
                .plan(decision.session().planId());
        if (plan.isEmpty()) return;
        if (decision.confirm()) confirm(decision.player(), plan.orElseThrow());
        else cancel(decision.player(), plan.orElseThrow());
    }

    private void deliver(
            ServerPlayer player, CityArchitectCoordinator.RoadResult result, boolean openPreview
    ) {
        message(player, result.explanation());
        if (openPreview && result.code() == CityArchitectCoordinator.ResultCode.MARKED
                && result.planId() != null) {
            openPreview(player, result.planId());
        }
    }

    private void openPreview(ServerPlayer player, UUID planId) {
        CityArchitectPlanData.PlanView plan = CityArchitectPlanData.get(player.getServer())
                .plan(planId).orElse(null);
        if (plan == null) return;
        CityArchitectPlanData.StageView stage = currentStage(plan);
        if (stage == null) return;
        List<BlockPos> positions = CityArchitectCoordinator.markersForStage(stage).stream()
                .map(CityArchitectPlanData.MarkerCell::position).limit(2_048).toList();
        long expires = player.getServer().overworld().getGameTime() + 2_400L;
        CityArchitectPreviewPacket preview = new CityArchitectPreviewPacket(
                UUID.randomUUID(), plan.id(), stage.id(),
                "Этап " + (stage.index() + 1), stage.explanation(), expires, positions);
        CityArchitectNetwork.open(player, plan.assistant(), plan.id(), stage.id(), expires, preview);
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        CityArchitectNetwork.expire(server.overworld().getGameTime());
        for (ServerLevel level : server.getAllLevels()) {
            resumeMarkedPlans(level);
            marker(level).tick(16);
            build(level).tick(16);
            finishBuilds(level);
        }
    }

    private void resumeMarkedPlans(ServerLevel level) {
        String dimension = level.dimension().location().toString();
        for (CityArchitectPlanData.PlanView plan : CityArchitectPlanData.get(level.getServer()).allPlans()) {
            if (!plan.dimension().equals(dimension) || plan.status() != CityArchitectPlanData.PlanStatus.MARKED
                    || !resumedMarkerPlans.add(plan.id())) continue;
            CityArchitectPlanData.StageView stage = currentStage(plan);
            if (stage != null) marker(level).queue(plan.id(), CityArchitectCoordinator.markersForStage(stage));
        }
    }

    private void finishBuilds(ServerLevel level) {
        CityArchitectPlanData data = CityArchitectPlanData.get(level.getServer());
        CityArchitectBuildService service = build(level);
        String dimension = level.dimension().location().toString();
        for (CityArchitectPlanData.PlanView plan : data.allPlans()) {
            if (!plan.dimension().equals(dimension)
                    || plan.status() != CityArchitectPlanData.PlanStatus.BUILDING
                    || !service.complete(plan.id())) continue;
            CityArchitectPlanData.StageView stage = currentStage(plan);
            if (stage == null) continue;
            if (!service.successful(plan.id())) {
                data.failStage(plan.id(), stage.id(), "Защищённый блок остановил стройку");
                notifyOwner(level.getServer(), plan.owner(), "Стройка остановлена: найден защищённый блок.");
                service.remove(plan.id());
                continue;
            }
            long time = level.getGameTime();
            stage.buildings().forEach(building -> data.recordCompletedPlacement(plan.id(),
                    new CityArchitectPlanData.CompletedPlacement("building", building.schematicKey(),
                            building.origin(), time)));
            stage.roads().forEach(road -> data.recordCompletedPlacement(plan.id(),
                    new CityArchitectPlanData.CompletedPlacement("road", stage.candidateId(),
                            road.centerline().get(0), time)));
            data.completeStage(plan.id(), stage.id());
            service.remove(plan.id());
            resumedMarkerPlans.remove(plan.id());
            notifyOwner(level.getServer(), plan.owner(), data.plan(plan.id()).orElseThrow().status()
                    == CityArchitectPlanData.PlanStatus.COMPLETE
                    ? "Генеральный план города завершён."
                    : "Этап завершён. Команда «Помощник, покажи следующий этап» разметит следующий.");
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        CityArchitectNetwork.disconnect(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void stopped(ServerStoppedEvent event) {
        markerServices.clear();
        buildServices.clear();
        resumedMarkerPlans.clear();
    }

    private CityArchitectCoordinator coordinator(ServerLevel level) {
        MinecraftServer server = level.getServer();
        CityArchitectPlanData data = CityArchitectPlanData.get(server);
        return new CityArchitectCoordinator(
                data,
                CityArchitectLandmarks.fromKingdom(server, data),
                new CityArchitectRoadPlanner(),
                new CityArchitectReasoner(new DisabledCityArchitectModelClient(), Duration.ofSeconds(2)),
                CityArchitectCoordinator.surfaceProvider(level),
                marker(level)::queue,
                server::execute);
    }

    private CityArchitectMarkerService marker(ServerLevel level) {
        String dimension = level.dimension().location().toString();
        return markerServices.computeIfAbsent(dimension, ignored -> new CityArchitectMarkerService(
                CityArchitectPlanData.get(level.getServer()), new MarkerWorld(level)));
    }

    private CityArchitectBuildService build(ServerLevel level) {
        String dimension = level.dimension().location().toString();
        return buildServices.computeIfAbsent(dimension, ignored -> new CityArchitectBuildService(
                CityArchitectBuildService.SavedStore.get(level.getServer()),
                new BuildWorld(level), dimension));
    }

    private static CityArchitectCoordinator.RequestContext context(ServerPlayer player) {
        var assistant = ru.projectkingdom.assistant.KingdomAssistantData.get(player.getServer())
                .ownedBy(player.getUUID()).orElse(null);
        return new CityArchitectCoordinator.RequestContext(
                player.getUUID(), assistant == null ? null : assistant.entityId(),
                player.level().dimension().location().toString(), player.hasPermissions(4) ? 4 : 0);
    }

    private static java.util.Optional<CityArchitectPlanData.PlanView> activePlan(ServerPlayer player) {
        return CityArchitectPlanData.get(player.getServer()).ownedBy(player.getUUID());
    }

    private static CityArchitectPlanData.StageView currentStage(CityArchitectPlanData.PlanView plan) {
        return plan.stages().stream()
                .filter(stage -> stage.status() != CityArchitectPlanData.StageStatus.COMPLETE)
                .findFirst().orElse(null);
    }

    private CityArchitectPlanner.CityPlanningRequest cityRequest(
            ServerLevel level, BlockPos hall, int radius
    ) {
        Map<Long, CityArchitectPlanner.SurfaceCell> surface = new HashMap<>();
        for (int x = hall.getX() - radius; x <= hall.getX() + radius; x++) {
            for (int z = hall.getZ() - radius; z <= hall.getZ() + radius; z++) {
                BlockPos probe = new BlockPos(x, level.getMinBuildHeight(), z);
                if (!level.hasChunkAt(probe)) continue;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockPos position = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(position);
                boolean buildable = level.getBlockEntity(position) == null
                        && state.getDestroySpeed(level, position) >= 0;
                surface.put(CityArchitectPlanner.cellKey(x, z),
                        new CityArchitectPlanner.SurfaceCell(position, buildable,
                                !level.getFluidState(position).isEmpty()));
            }
        }
        List<CityArchitectPlanner.Outline> outlines = existingOutlines(level, hall);
        return new CityArchitectPlanner.CityPlanningRequest(
                hall, surface, outlines, builtInBlueprints(), radius);
    }

    private static List<CityArchitectPlanner.Outline> existingOutlines(
            ServerLevel level, BlockPos hall
    ) {
        List<CityArchitectPlanner.Outline> result = new ArrayList<>();
        result.add(new CityArchitectPlanner.Outline(
                hall.getX() - 6, hall.getZ() - 7, hall.getX() + 6, hall.getZ() + 7));
        String dimension = level.dimension().location().toString();
        for (KingdomConstructionData.Project project : KingdomConstructionData.get(level.getServer()).all()) {
            if (!project.dimension().equals(dimension)
                    || project.status() == KingdomConstructionData.Status.CANCELLED) continue;
            var design = KingdomConstructionPlans.resolve(level.getServer(), project.design());
            if (design != null) result.add(new CityArchitectPlanner.Outline(
                    project.x(), project.z(), project.x() + design.width() - 1,
                    project.z() + design.length() - 1));
        }
        return List.copyOf(result);
    }

    private static List<CityArchitectPlanner.BlueprintFootprint> builtInBlueprints() {
        return List.of(
                blueprint("TOWN_HALL", "bank", KingdomBuildingTypes.Type.TOWN_HALL,
                        CityArchitectPlanData.StageKind.ADMINISTRATION),
                blueprint("WAREHOUSE", "warehouse", KingdomBuildingTypes.Type.WAREHOUSE,
                        CityArchitectPlanData.StageKind.ADMINISTRATION),
                blueprint("WAREHOUSE", "market", KingdomBuildingTypes.Type.WAREHOUSE,
                        CityArchitectPlanData.StageKind.ADMINISTRATION),
                blueprint("SMITHY", "smithy", KingdomBuildingTypes.Type.SMITHY,
                        CityArchitectPlanData.StageKind.PRODUCTION),
                blueprint("TAVERN", "tavern", KingdomBuildingTypes.Type.TAVERN,
                        CityArchitectPlanData.StageKind.HOUSING_HOSPITALITY),
                blueprint("HOUSE", "house", KingdomBuildingTypes.Type.HOUSE,
                        CityArchitectPlanData.StageKind.HOUSING_HOSPITALITY));
    }

    private static CityArchitectPlanner.BlueprintFootprint blueprint(
            String key,
            String role,
            KingdomBuildingTypes.Type type,
            CityArchitectPlanData.StageKind stage
    ) {
        return new CityArchitectPlanner.BlueprintFootprint(
                key, role, type.width, type.length, stage);
    }

    private List<CityArchitectRoadDesignFactory.RoadCell> buildCells(
            MinecraftServer server, CityArchitectPlanData.StageView stage
    ) {
        Map<BlockPos, CityArchitectRoadDesignFactory.RoadCell> cells = new LinkedHashMap<>();
        for (CityArchitectPlanData.RoadPlacement road : stage.roads()) {
            for (var cell : roadDesigns.create(road.centerline()).cells()) {
                cells.put(cell.position(), cell);
            }
        }
        for (CityArchitectPlanData.BuildingPlacement building : stage.buildings()) {
            String key = building.rotation() == 0 ? building.schematicKey()
                    : KingdomConstructionPlans.oriented(building.schematicKey(), building.rotation());
            var design = KingdomConstructionPlans.resolve(server, key);
            if (design == null) throw new IllegalStateException("Схема недоступна: " + key);
            for (var cell : design.blocks()) {
                BlockPos position = building.origin().offset(cell.x(), cell.y(), cell.z());
                cells.put(position, new CityArchitectRoadDesignFactory.RoadCell(position, cell.material()));
                if (cells.size() > CityArchitectRoadDesignFactory.MAX_CELLS)
                    throw new IllegalStateException("Этап превышает лимит блоков");
            }
        }
        if (cells.isEmpty()) throw new IllegalStateException("В этапе нет строительных блоков");
        return List.copyOf(cells.values());
    }

    private static void notifyOwner(MinecraftServer server, UUID owner, String text) {
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) message(player, text);
    }

    private static void message(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal("[Градостроитель] " + text));
    }

    private abstract static class BlockWorldBase {
        private final ServerLevel level;

        private BlockWorldBase(ServerLevel level) {
            this.level = level;
        }

        public boolean loaded(BlockPos position) {
            return level.hasChunkAt(position);
        }

        public boolean setExact(BlockPos position, String expectedState, String replacementState) {
            if (!serialize(level.getBlockState(position)).equals(expectedState)) return false;
            return level.setBlock(position, KingdomBlockStateCodec.parse(replacementState), 3);
        }

        protected BlockState state(BlockPos position) {
            return level.getBlockState(position);
        }

        protected boolean hasBlockEntity(BlockPos position) {
            return level.getBlockEntity(position) != null;
        }

        protected boolean unbreakable(BlockPos position) {
            return state(position).getDestroySpeed(level, position) < 0;
        }

        protected static String serialize(BlockState state) {
            StringBuilder result = new StringBuilder(
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            List<Property<?>> properties = state.getProperties().stream()
                    .sorted(Comparator.comparing(Property::getName)).toList();
            if (!properties.isEmpty()) {
                result.append('[');
                for (int index = 0; index < properties.size(); index++) {
                    if (index > 0) result.append(',');
                    Property<?> property = properties.get(index);
                    result.append(property.getName()).append('=').append(valueName(state, property));
                }
                result.append(']');
            }
            return result.toString();
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static String valueName(BlockState state, Property property) {
            return property.getName(state.getValue(property));
        }
    }

    private static final class MarkerWorld extends BlockWorldBase
            implements CityArchitectMarkerService.WorldAccess {
        private MarkerWorld(ServerLevel level) {
            super(level);
        }

        @Override
        public CityArchitectMarkerService.WorldCell inspect(BlockPos position) {
            return new CityArchitectMarkerService.WorldCell(serialize(state(position)),
                    hasBlockEntity(position), unbreakable(position));
        }

        @Override
        public boolean safeReplaceable(BlockPos position, CityArchitectMarkerService.WorldCell cell) {
            BlockState state = state(position);
            return !cell.hasBlockEntity() && !cell.unbreakable()
                    && (state.isAir() || state.canBeReplaced());
        }
    }

    private static final class BuildWorld extends BlockWorldBase
            implements CityArchitectBuildService.WorldAccess {
        private BuildWorld(ServerLevel level) {
            super(level);
        }

        @Override
        public CityArchitectBuildService.WorldCell inspect(BlockPos position) {
            return new CityArchitectBuildService.WorldCell(serialize(state(position)),
                    hasBlockEntity(position), unbreakable(position));
        }

        @Override
        public boolean safeReplaceable(BlockPos position, CityArchitectBuildService.WorldCell cell) {
            return !cell.hasBlockEntity() && !cell.unbreakable();
        }
    }
}
