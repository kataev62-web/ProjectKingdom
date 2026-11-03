package ru.projectkingdom.cityarchitect.plan;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface CityArchitectModelClient {
    boolean enabled();

    CompletableFuture<String> choose(List<CityArchitectReasoner.CandidateSummary> candidates);
}
