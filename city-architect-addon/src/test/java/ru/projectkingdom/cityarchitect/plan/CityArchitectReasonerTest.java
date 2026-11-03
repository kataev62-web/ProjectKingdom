package ru.projectkingdom.cityarchitect.plan;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CityArchitectReasonerTest {
    private static final List<CityArchitectReasoner.CandidateSummary> CANDIDATES = List.of(
            candidate("road-b", 20, "length", "12"),
            candidate("road-a", 10, "length", "15")
    );

    @Test
    void acceptsOnlyAWhitelistedCandidateId() throws Exception {
        var reasoner = reasoner(client(true,
                CompletableFuture.completedFuture("{\"candidateId\":\"road-b\",\"explanation\":\"меньше воды\"}")));

        var choice = reasoner.choose(CANDIDATES).get(1, TimeUnit.SECONDS);

        assertEquals("road-b", choice.candidateId());
        assertEquals("меньше воды", choice.explanation());
        assertTrue(choice.modelUsed());
    }

    @Test
    void rejectsUnknownIdCoordinatesCommandsAndMalformedOutput() throws Exception {
        assertFallback("{\"candidateId\":\"invented\",\"explanation\":\"так лучше\"}");
        assertFallback("{\"candidateId\":\"road-b\",\"explanation\":\"так лучше\",\"coordinates\":[1,2,3]}");
        assertFallback("{\"candidateId\":\"road-b\",\"explanation\":\"так лучше\",\"command\":\"/fill\"}");
        assertFallback("не json");
    }

    @Test
    void disabledClientIsNeverCalled() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        CityArchitectModelClient disabled = new CityArchitectModelClient() {
            @Override
            public boolean enabled() {
                return false;
            }

            @Override
            public CompletableFuture<String> choose(List<CityArchitectReasoner.CandidateSummary> candidates) {
                called.set(true);
                return CompletableFuture.completedFuture("{}");
            }
        };

        var choice = reasoner(disabled).choose(CANDIDATES).get(1, TimeUnit.SECONDS);

        assertEquals("road-a", choice.candidateId());
        assertFalse(choice.modelUsed());
        assertFalse(called.get());
    }

    @Test
    void exceptionAndTimeoutUseSafeFallback() throws Exception {
        var failed = new CompletableFuture<String>();
        failed.completeExceptionally(new IllegalStateException("offline"));
        assertEquals("road-a", reasoner(client(true, failed))
                .choose(CANDIDATES).get(1, TimeUnit.SECONDS).candidateId());

        var never = new CompletableFuture<String>();
        var timedOut = reasoner(client(true, never)).choose(CANDIDATES).get(1, TimeUnit.SECONDS);
        assertEquals("road-a", timedOut.candidateId());
        assertFalse(timedOut.modelUsed());
    }

    @Test
    void fallbackBreaksEqualScoresByIdAndExplainsMetrics() throws Exception {
        var equal = List.of(
                candidate("road-z", 10, "water", "0"),
                candidate("road-a", 10, "water", "2")
        );

        var choice = reasoner(new DisabledCityArchitectModelClient())
                .choose(equal).get(1, TimeUnit.SECONDS);

        assertEquals("road-a", choice.candidateId());
        assertTrue(choice.explanation().contains("оценка 10"));
        assertTrue(choice.explanation().contains("water=2"));
    }

    private static void assertFallback(String raw) throws Exception {
        var choice = reasoner(client(true, CompletableFuture.completedFuture(raw)))
                .choose(CANDIDATES).get(1, TimeUnit.SECONDS);
        assertEquals("road-a", choice.candidateId());
        assertFalse(choice.modelUsed());
    }

    private static CityArchitectReasoner reasoner(CityArchitectModelClient client) {
        return new CityArchitectReasoner(client, Duration.ofMillis(25));
    }

    private static CityArchitectModelClient client(boolean enabled, CompletableFuture<String> response) {
        return new CityArchitectModelClient() {
            @Override
            public boolean enabled() {
                return enabled;
            }

            @Override
            public CompletableFuture<String> choose(List<CityArchitectReasoner.CandidateSummary> candidates) {
                return response;
            }
        };
    }

    private static CityArchitectReasoner.CandidateSummary candidate(
            String id, long score, String factName, String factValue
    ) {
        return new CityArchitectReasoner.CandidateSummary(id, "road", score, Map.of(factName, factValue));
    }
}
