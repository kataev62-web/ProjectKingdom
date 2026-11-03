package ru.projectkingdom.assistant;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ru.projectkingdom.administration.AdministrativeKind;
import ru.projectkingdom.administration.KingdomAdministrationData;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomConstructionData;
import ru.projectkingdom.settlement.KingdomSettlementGrowth;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Read-only settlement advisor backed by the live Project Kingdom growth rules. */
public final class SettlementAuditService {
    private static final int ROAD_STEP = 2;
    private static final int ROAD_ACCESS_DISTANCE_SQUARED = 16;

    public record Target(
            KingdomSettlementGrowth.Rank rank,
            int chunks,
            int buildings,
            int residents,
            boolean tavern
    ) {}

    private SettlementAuditService() {}

    public static List<AssistantFinding> audit(
            MinecraftServer server, ServerPlayer player, WorldSurveySnapshot snapshot) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(snapshot, "snapshot");
        String playerDimension = player.serverLevel().dimension().location().toString();
        if (!playerDimension.equals(snapshot.dimension()))
            throw new IllegalArgumentException("survey belongs to another dimension");

        KingdomCivicsData civics = KingdomCivicsData.get(server);
        var town = civics.townAt(player.serverLevel(), snapshot.center());
        if (town == null) town = civics.townOf(player.getUUID());
        if (town == null) {
            return List.of(finding(AssistantFinding.Category.SETTLEMENT,
                    AssistantFinding.Severity.WARNING,
                    "Поселение не найдено",
                    "Центр обследования не относится к зарегистрированному поселению игрока.",
                    "Выберите участок города или вступите в зарегистрированное поселение.",
                    "Повторите обследование: центр должен находиться на городском участке."));
        }

        KingdomSettlementGrowth.Progress progress = KingdomSettlementGrowth.inspect(
                civics, KingdomConstructionData.get(server), town.id(), snapshot.dimension(), server);
        var warehouse = KingdomAdministrationData.get(server)
                .core(town.id(), AdministrativeKind.CENTRAL_WAREHOUSE);
        boolean hasWarehouse = warehouse != null && snapshot.dimension().equals(warehouse.dimension());
        return assess(progress, hasWarehouse, snapshot);
    }

    /** Pure seam used by tests and future report generation. */
    public static List<AssistantFinding> assess(
            KingdomSettlementGrowth.Progress progress,
            boolean hasCentralWarehouse,
            WorldSurveySnapshot snapshot) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(snapshot, "snapshot");
        List<AssistantFinding> findings = new ArrayList<>();
        Target target = targetAfter(progress.eligible());

        if (target != null) {
            if (target.tavern() && !progress.tavern()) {
                findings.add(finding(AssistantFinding.Category.FOOD_AND_LODGING,
                        AssistantFinding.Severity.CRITICAL,
                        "Нет действующей таверны",
                        "Для уровня «" + target.rank().title() + "» нужна таверна на городской земле, "
                                + "принадлежащая жителю этого поселения.",
                        "Установите и активируйте ядро таверны на участке поселения.",
                        "Проверка роста должна показать: «Таверна: есть»."));
            }
            if (progress.chunks() < target.chunks()) {
                findings.add(finding(AssistantFinding.Category.SETTLEMENT,
                        AssistantFinding.Severity.WARNING,
                        "Недостаточно городской земли",
                        "Зарегистрировано " + progress.chunks() + " из " + target.chunks()
                                + " требуемых чанков для уровня «" + target.rank().title() + "».",
                        "Расширьте территорию через штатную систему городских участков.",
                        "В сводке роста должно быть не меньше " + target.chunks() + " чанков."));
            }
            if (progress.buildings() < target.buildings()) {
                findings.add(finding(AssistantFinding.Category.HOUSING,
                        AssistantFinding.Severity.WARNING,
                        "Не хватает отдельных зданий",
                        "Учтено " + progress.buildings() + " из " + target.buildings()
                                + " отдельных завершённых зданий для уровня «" + target.rank().title() + "».",
                        "Завершите отдельное зарегистрированное здание вне контура стартовой таверны.",
                        "В сводке роста должно быть не меньше " + target.buildings() + " зданий."));
            }
            if (progress.residents() < target.residents()) {
                findings.add(finding(AssistantFinding.Category.WORKFORCE,
                        AssistantFinding.Severity.WARNING,
                        "Недостаточно жителей",
                        "В поселении " + progress.residents() + " из " + target.residents()
                                + " жителей, нужных для уровня «" + target.rank().title() + "».",
                        "Подготовьте жильё, питание и рабочие места для иммиграции.",
                        "Счётчик жителей должен достичь " + target.residents() + "."));
            }
        }

        if (progress.eligible().ordinal() >= KingdomSettlementGrowth.Rank.VILLAGE.ordinal()
                && !hasCentralWarehouse) {
            findings.add(finding(AssistantFinding.Category.LOGISTICS,
                    AssistantFinding.Severity.WARNING,
                    "Нет центрального склада",
                    "У поселения уровня «" + progress.eligible().title()
                            + "» отсутствует зарегистрированное ядро центрального склада.",
                    "Разместите административное ядро центрального склада и зарегистрируйте помещения.",
                    "Откройте ядро склада и проверьте активные привязки помещений."));
        }

        if (!roadsConnectFacilities(snapshot)) {
            findings.add(finding(AssistantFinding.Category.ROADS,
                    AssistantFinding.Severity.WARNING,
                    "Объекты не соединены дорогой",
                    "Не все зарегистрированные объекты обследованной области примыкают к одной дорожной сети.",
                    "Соедините входы объектов непрерывной дорогой без разрывов.",
                    "Повторное обследование должно обнаружить одну дорожную компоненту у всех объектов."));
        }
        return List.copyOf(findings);
    }

    public static Target targetAfter(KingdomSettlementGrowth.Rank rank) {
        Objects.requireNonNull(rank, "rank");
        return switch (rank) {
            case HAMLET -> new Target(KingdomSettlementGrowth.Rank.VILLAGE, 4, 1, 3, true);
            case VILLAGE -> new Target(KingdomSettlementGrowth.Rank.SETTLEMENT, 12, 4, 8, true);
            case SETTLEMENT -> new Target(KingdomSettlementGrowth.Rank.CITY, 32, 11, 20, true);
            case CITY -> null;
        };
    }

    static boolean roadsConnectFacilities(WorldSurveySnapshot snapshot) {
        List<BlockPos> facilities = new ArrayList<>();
        for (var outline : snapshot.buildingOutlines()) {
            facilities.add(new BlockPos(midpoint(outline.minX(), outline.maxX()),
                    snapshot.center().getY(), midpoint(outline.minZ(), outline.maxZ())));
        }
        for (var core : snapshot.enterpriseCores()) facilities.add(core.position());
        if (facilities.size() < 2) return true;

        Set<Long> roads = new HashSet<>();
        for (var column : snapshot.columns()) if (column.road()) roads.add(key(column.x(), column.z()));
        if (roads.isEmpty()) return false;
        Map<Long, Integer> components = roadComponents(roads);
        Integer common = null;
        for (BlockPos facility : facilities) {
            Integer attached = nearestRoadComponent(facility, roads, components);
            if (attached == null) return false;
            if (common == null) common = attached;
            else if (!common.equals(attached)) return false;
        }
        return true;
    }

    private static Map<Long, Integer> roadComponents(Set<Long> roads) {
        Map<Long, Integer> components = new HashMap<>();
        int next = 0;
        for (long start : roads) {
            if (components.containsKey(start)) continue;
            int component = next++;
            ArrayDeque<Long> queue = new ArrayDeque<>();
            queue.add(start);
            components.put(start, component);
            while (!queue.isEmpty()) {
                long point = queue.removeFirst();
                int x = (int) (point >> 32);
                int z = (int) point;
                for (int[] direction : new int[][]{{ROAD_STEP, 0}, {-ROAD_STEP, 0},
                        {0, ROAD_STEP}, {0, -ROAD_STEP}}) {
                    long neighbor = key(x + direction[0], z + direction[1]);
                    if (roads.contains(neighbor) && !components.containsKey(neighbor)) {
                        components.put(neighbor, component);
                        queue.addLast(neighbor);
                    }
                }
            }
        }
        return components;
    }

    private static Integer nearestRoadComponent(
            BlockPos facility, Set<Long> roads, Map<Long, Integer> components) {
        long closestDistance = Long.MAX_VALUE;
        long closestRoad = Long.MAX_VALUE;
        Integer closest = null;
        for (long road : roads) {
            long dx = (long) (int) (road >> 32) - facility.getX();
            long dz = (long) (int) road - facility.getZ();
            long distance = dx * dx + dz * dz;
            if (distance <= ROAD_ACCESS_DISTANCE_SQUARED
                    && (distance < closestDistance
                    || distance == closestDistance && road < closestRoad)) {
                closestDistance = distance;
                closestRoad = road;
                closest = components.get(road);
            }
        }
        return closest;
    }

    private static int midpoint(int min, int max) {
        return (int) (((long) min + max) / 2L);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static AssistantFinding finding(
            AssistantFinding.Category category,
            AssistantFinding.Severity severity,
            String title,
            String reason,
            String recommendation,
            String verification) {
        return new AssistantFinding(category, severity, title, reason, recommendation, verification);
    }
}
