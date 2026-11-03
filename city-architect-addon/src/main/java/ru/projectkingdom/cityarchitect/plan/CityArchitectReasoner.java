package ru.projectkingdom.cityarchitect.plan;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class CityArchitectReasoner {
    private static final int MAX_EXPLANATION_LENGTH = 512;

    private final CityArchitectModelClient client;
    private final Duration timeout;

    public CityArchitectReasoner(CityArchitectModelClient client, Duration timeout) {
        this.client = Objects.requireNonNull(client, "client");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public CompletableFuture<Choice> choose(List<CandidateSummary> input) {
        List<CandidateSummary> candidates = List.copyOf(Objects.requireNonNull(input, "candidates"));
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("At least one candidate is required");
        }
        ensureUniqueIds(candidates);
        Choice fallback = fallback(candidates);
        if (!client.enabled()) {
            return CompletableFuture.completedFuture(fallback);
        }

        final CompletableFuture<String> response;
        try {
            response = client.choose(candidates);
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(fallback);
        }
        if (response == null) {
            return CompletableFuture.completedFuture(fallback);
        }
        return response.completeOnTimeout(null, timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .handle((raw, failure) -> {
                    if (failure != null) {
                        return fallback;
                    }
                    return validate(raw, candidates).orElse(fallback);
                });
    }

    private static Optional<Choice> validate(String raw, List<CandidateSummary> candidates) {
        if (raw == null || raw.isBlank() || raw.length() > 2_048) {
            return Optional.empty();
        }
        try {
            JsonElement parsed = JsonParser.parseString(raw);
            if (!parsed.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject object = parsed.getAsJsonObject();
            if (object.size() != 2
                    || !stringProperty(object, "candidateId")
                    || !stringProperty(object, "explanation")) {
                return Optional.empty();
            }
            String candidateId = object.get("candidateId").getAsString();
            String explanation = object.get("explanation").getAsString().trim();
            if (explanation.isEmpty() || explanation.length() > MAX_EXPLANATION_LENGTH) {
                return Optional.empty();
            }
            boolean allowed = candidates.stream().anyMatch(candidate -> candidate.id().equals(candidateId));
            return allowed
                    ? Optional.of(new Choice(candidateId, explanation, true))
                    : Optional.empty();
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    private static boolean stringProperty(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    private static Choice fallback(List<CandidateSummary> candidates) {
        CandidateSummary selected = candidates.stream()
                .min(Comparator.comparingLong(CandidateSummary::score).thenComparing(CandidateSummary::id))
                .orElseThrow();
        String facts = selected.facts().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + ", " + right)
                .orElse("без дополнительных метрик");
        return new Choice(selected.id(),
                "Выбран безопасный вариант без модели: оценка " + selected.score() + ", " + facts,
                false);
    }

    private static void ensureUniqueIds(List<CandidateSummary> candidates) {
        long unique = candidates.stream().map(CandidateSummary::id).distinct().count();
        if (unique != candidates.size()) {
            throw new IllegalArgumentException("Candidate ids must be unique");
        }
    }

    public record CandidateSummary(String id, String kind, long score, Map<String, String> facts) {
        public CandidateSummary {
            id = requireText(id, "id");
            kind = requireText(kind, "kind");
            Objects.requireNonNull(facts, "facts");
            LinkedHashMap<String, String> copy = new LinkedHashMap<>();
            facts.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> copy.put(
                            requireText(entry.getKey(), "fact key"),
                            requireText(entry.getValue(), "fact value")));
            facts = Map.copyOf(copy);
        }
    }

    public record Choice(String candidateId, String explanation, boolean modelUsed) {
        public Choice {
            candidateId = requireText(candidateId, "candidateId");
            explanation = requireText(explanation, "explanation");
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
