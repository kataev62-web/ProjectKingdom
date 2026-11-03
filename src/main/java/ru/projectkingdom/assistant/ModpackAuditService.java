package ru.projectkingdom.assistant;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.ModList;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Metadata-only recommendations. This class cannot install, delete or move mod files. */
public final class ModpackAuditService {
    private record Rule(String modId, String displayName,
                        ModRecommendation.Recommendation recommendation,
                        String reason, String benefit, String risk, int priority,
                        String verification, Set<String> capabilities) {}
    private record Addition(String capability, Rule rule) {}
    private record Rules(Map<String, Rule> installed, List<Addition> additions) {}

    private ModpackAuditService() {}

    public static List<InstalledMod> installed() {
        List<InstalledMod> result = new ArrayList<>();
        for (var info : ModList.get().getMods()) {
            List<String> dependencies = info.getDependencies().stream()
                    .map(dependency -> dependency.getModId()).limit(64).toList();
            result.add(new InstalledMod(info.getModId(), info.getDisplayName(),
                    info.getDescription(), dependencies, Set.of()));
        }
        result.sort(Comparator.comparing(InstalledMod::modId));
        return List.copyOf(result);
    }

    public static List<ModRecommendation> audit(
            List<InstalledMod> installed, FeatureInventory inventory) {
        List<InstalledMod> mods = new ArrayList<>(List.copyOf(installed));
        mods.sort(Comparator.comparing(InstalledMod::modId));
        Rules rules = rules();
        List<ModRecommendation> result = new ArrayList<>();
        Set<String> installedIds = new HashSet<>();
        Set<String> capabilities = new HashSet<>();
        for (InstalledMod mod : mods) {
            installedIds.add(mod.modId());
            capabilities.addAll(mod.capabilities());
            Rule rule = rules.installed().get(mod.modId());
            if (rule == null) {
                result.add(new ModRecommendation(mod.modId(), mod.displayName(),
                        ModRecommendation.Recommendation.REVIEW,
                        "Для мода нет утверждённого правила Project Kingdom.",
                        "Ручная проверка может выявить полезную интеграцию.",
                        "Автоматическое удаление неизвестного мода запрещено.", 20,
                        "Проверьте назначение, зависимости, нагрузку и игровой сценарий вручную."));
            } else {
                capabilities.addAll(rule.capabilities());
                result.add(recommendation(rule, mod.displayName()));
            }
        }
        for (Addition addition : rules.additions()) {
            if (!inventory.requiredCapabilities().contains(addition.capability())
                    || capabilities.contains(addition.capability())
                    || installedIds.contains(addition.rule().modId())) continue;
            result.add(recommendation(addition.rule(), addition.rule().displayName()));
        }
        result.sort(Comparator.comparingInt(ModRecommendation::priority).reversed()
                .thenComparing(ModRecommendation::modId));
        return List.copyOf(result);
    }

    private static ModRecommendation recommendation(Rule rule, String installedName) {
        String name = installedName == null || installedName.isBlank()
                ? rule.displayName() : installedName;
        return new ModRecommendation(rule.modId(), name, rule.recommendation(),
                rule.reason(), rule.benefit(), rule.risk(), rule.priority(), rule.verification());
    }

    private static Rules rules() {
        try (var stream = ModpackAuditService.class.getResourceAsStream(
                "/data/kingdomnpc/assistant/mod_rules.json")) {
            if (stream == null) throw new IllegalStateException("missing mod_rules.json");
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Rule> installed = new HashMap<>();
            for (var element : root.getAsJsonArray("installed")) {
                Rule rule = rule(element.getAsJsonObject());
                installed.put(rule.modId(), rule);
            }
            List<Addition> additions = new ArrayList<>();
            for (var element : root.getAsJsonArray("additions")) {
                JsonObject object = element.getAsJsonObject();
                additions.add(new Addition(object.get("capability").getAsString(), rule(object)));
            }
            return new Rules(Map.copyOf(installed), List.copyOf(additions));
        } catch (Exception invalid) {
            throw new IllegalStateException("invalid assistant mod rules", invalid);
        }
    }

    private static Rule rule(JsonObject object) {
        Set<String> capabilities = new HashSet<>();
        JsonArray array = object.getAsJsonArray("capabilities");
        if (array != null) for (var item : array) capabilities.add(item.getAsString());
        return new Rule(object.get("modId").getAsString(), object.get("displayName").getAsString(),
                ModRecommendation.Recommendation.valueOf(object.get("recommendation").getAsString()),
                object.get("reason").getAsString(), object.get("benefit").getAsString(),
                object.get("risk").getAsString(), object.get("priority").getAsInt(),
                object.get("verification").getAsString(), Set.copyOf(capabilities));
    }
}
