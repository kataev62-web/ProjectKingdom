package ru.projectkingdom.cityarchitect.plan;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class DisabledCityArchitectModelClient implements CityArchitectModelClient {
    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public CompletableFuture<String> choose(List<CityArchitectReasoner.CandidateSummary> candidates) {
        return CompletableFuture.completedFuture("");
    }
}
