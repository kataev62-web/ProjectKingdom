package ru.projectkingdom.cityarchitect.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import ru.projectkingdom.administration.AdministrativeKind;
import ru.projectkingdom.administration.KingdomAdministrationData;
import ru.projectkingdom.cityarchitect.data.CityArchitectPlanData;
import ru.projectkingdom.enterprise.EnterpriseKind;
import ru.projectkingdom.npc.KingdomCivicsData;
import ru.projectkingdom.npc.KingdomConstructionData;
import ru.projectkingdom.npc.KingdomConstructionPlans;
import ru.projectkingdom.npc.KingdomEnterpriseData;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CityArchitectLandmarks {
    private static final int MAX_MATCHES = 8;

    private final Source source;

    public CityArchitectLandmarks(Source source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public Result resolve(String query, String dimension) {
        String normalized = normalize(query);
        String checkedDimension = Objects.requireNonNull(dimension, "dimension").trim();
        Optional<LandmarkType> requestedType = typeFor(normalized);
        List<Landmark> matches = source.all().stream()
                .filter(value -> value.dimension().equals(checkedDimension))
                .filter(value -> requestedType
                        .map(type -> value.type() == type)
                        .orElseGet(() -> nameMatches(normalized, value.name())))
                .limit(MAX_MATCHES)
                .toList();
        Status status = matches.isEmpty()
                ? Status.MISSING
                : matches.size() == 1 ? Status.FOUND : Status.AMBIGUOUS;
        return new Result(status, normalized, matches);
    }

    public static CityArchitectLandmarks fromKingdom(
            MinecraftServer server, CityArchitectPlanData planData
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(planData, "planData");
        return new CityArchitectLandmarks(() -> kingdomSnapshot(server, planData));
    }

    private static List<Landmark> kingdomSnapshot(
            MinecraftServer server, CityArchitectPlanData planData
    ) {
        List<Landmark> result = new ArrayList<>();
        KingdomAdministrationData administration = KingdomAdministrationData.get(server);
        for (KingdomCivicsData.Town town : KingdomCivicsData.get(server).towns()) {
            var core = administration.core(town.id(), AdministrativeKind.TOWN_HALL);
            if (core != null) {
                result.add(new Landmark(town.id(), LandmarkType.TOWN_HALL,
                        "Ратуша " + town.name(), core.dimension(), BlockPos.of(core.packedPos())));
            }
        }

        KingdomEnterpriseData enterprises = KingdomEnterpriseData.get(server);
        for (KingdomEnterpriseData.Enterprise enterprise : enterprises.all()) {
            var core = enterprises.core(enterprise.id());
            if (core == null || !core.active()) {
                continue;
            }
            LandmarkType type = enterpriseType(core.kind());
            result.add(new Landmark(enterprise.id(), type, enterprise.name(),
                    core.dimension(), BlockPos.of(core.packedPos())));
        }

        for (KingdomConstructionData.Project project : KingdomConstructionData.get(server).all()) {
            if (project.status() != KingdomConstructionData.Status.COMPLETED) {
                continue;
            }
            String title = KingdomConstructionPlans.title(project.design());
            typeFor(normalize(title + " " + project.design())).ifPresent(type -> result.add(
                    new Landmark(project.id(), type, title, project.dimension(),
                            new BlockPos(project.x(), project.y(), project.z()))));
        }

        for (CityArchitectPlanData.PlanView plan : planData.allPlans()) {
            for (CityArchitectPlanData.CompletedPlacement placement : plan.completedPlacements()) {
                typeFor(normalize(placement.kind() + " " + placement.key())).ifPresent(type -> {
                    UUID id = UUID.nameUUIDFromBytes((plan.id() + ":" + placement.kind() + ":"
                            + placement.key() + ":" + placement.origin().asLong())
                            .getBytes(StandardCharsets.UTF_8));
                    result.add(new Landmark(id, type, placement.key(), plan.dimension(), placement.origin()));
                });
            }
        }
        return List.copyOf(result);
    }

    private static LandmarkType enterpriseType(EnterpriseKind kind) {
        return switch (kind) {
            case SMITHY -> LandmarkType.SMITHY;
            case TAVERN -> LandmarkType.TAVERN;
            case HOTEL -> LandmarkType.HOUSING;
            case CONSTRUCTION -> LandmarkType.WORKSHOP;
            default -> LandmarkType.ENTERPRISE;
        };
    }

    private static Optional<LandmarkType> typeFor(String normalized) {
        if (normalized.contains("ратуш")) return Optional.of(LandmarkType.TOWN_HALL);
        if (normalized.contains("кузниц") || normalized.contains("smithy")) return Optional.of(LandmarkType.SMITHY);
        if (normalized.contains("банк")) return Optional.of(LandmarkType.BANK);
        if (normalized.contains("склад")) return Optional.of(LandmarkType.WAREHOUSE);
        if (normalized.contains("рынок")) return Optional.of(LandmarkType.MARKET);
        if (normalized.contains("таверн")) return Optional.of(LandmarkType.TAVERN);
        if (normalized.contains("жил") || normalized.contains("гостиниц")) return Optional.of(LandmarkType.HOUSING);
        if (normalized.contains("мастерск")) return Optional.of(LandmarkType.WORKSHOP);
        return Optional.empty();
    }

    private static boolean nameMatches(String normalizedQuery, String name) {
        String normalizedName = normalize(name);
        return !normalizedQuery.isEmpty()
                && (normalizedName.contains(normalizedQuery) || normalizedQuery.contains(normalizedName));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }

    public interface Source {
        List<Landmark> all();
    }

    public enum Status { FOUND, MISSING, AMBIGUOUS }

    public enum LandmarkType {
        TOWN_HALL, SMITHY, BANK, WAREHOUSE, MARKET, TAVERN, HOUSING, WORKSHOP, ENTERPRISE
    }

    public record Landmark(UUID id, LandmarkType type, String name, String dimension, BlockPos center) {
        public Landmark {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(type, "type");
            name = requireText(name, "name");
            dimension = requireText(dimension, "dimension");
            center = Objects.requireNonNull(center, "center").immutable();
        }
    }

    public record Result(Status status, String query, List<Landmark> matches) {
        public Result {
            Objects.requireNonNull(status, "status");
            query = Objects.requireNonNull(query, "query");
            matches = List.copyOf(Objects.requireNonNull(matches, "matches"));
        }

        public Optional<Landmark> unique() {
            return status == Status.FOUND ? Optional.of(matches.get(0)) : Optional.empty();
        }
    }

    private static String requireText(String value, String name) {
        String checked = Objects.requireNonNull(value, name).trim();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return checked;
    }
}
