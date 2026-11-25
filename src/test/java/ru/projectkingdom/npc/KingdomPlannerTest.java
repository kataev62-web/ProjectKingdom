package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import ru.projectkingdom.npc.KingdomPlanner.Intent;
import ru.projectkingdom.npc.KingdomPlanner.Kind;
import ru.projectkingdom.npc.KingdomPlanner.Outcome;
import ru.projectkingdom.npc.KingdomPlanner.Proposal;
import ru.projectkingdom.npc.KingdomPlanner.Request;

import org.junit.jupiter.api.Test;

/**
 * The planner of a complex task.
 *
 * <p>The local planner must work without a network and without the JAR of the
 * external service. The answer of the external service is only a proposal:
 * the schema, the number of the intents, the kind and the freshness decide
 * whether it may be used.
 */
final class KingdomPlannerTest {

    private static Request request() {
        return new Request(UUID.randomUUID(), "supply",
                List.of("buy:wheat:10", "route:depot:2", "build:granary:1"), 1000);
    }

    @Test void theLocalPlannerProposesTheIntentsOfTheFacts() {
        var outcome = new KingdomLocalPlanner().propose(request());
        assertTrue(outcome.accepted(), "the local planner must answer");
        assertEquals(3, outcome.proposal().intents().size(), "one intent per understood fact");
        assertEquals(Kind.BUY_ORDER, outcome.proposal().intents().get(0).kind());
        assertEquals("wheat", outcome.proposal().intents().get(0).target());
        assertEquals(10, outcome.proposal().intents().get(0).amount());
        assertEquals(Kind.ROUTE_ORDER, outcome.proposal().intents().get(1).kind());
        assertEquals(Kind.BUILD_ORDER, outcome.proposal().intents().get(2).kind());
    }

    @Test void theLocalPlannerIsDeterministicAndOffline() {
        Request request = request();
        var first = new KingdomLocalPlanner().propose(request);
        var second = new KingdomLocalPlanner().propose(request);
        assertEquals(first.proposal(), second.proposal(), "the same facts lead to the same answer");
        assertEquals("local", new KingdomLocalPlanner().name());
    }

    @Test void theLocalPlannerIgnoresWhatItDoesNotUnderstand() {
        var outcome = new KingdomLocalPlanner().propose(
                new Request(UUID.randomUUID(), "supply", List.of("weather:rain:1"), 1000));
        assertFalse(outcome.accepted(), "an unknown fact leads to no proposal");
        assertNull(outcome.proposal());
    }

    @Test void theExternalPlannerIsOffByDefault() {
        assertFalse(KingdomPlayer2Planner.enabled(), "the external planner must be off by default");
        var outcome = new KingdomPlayer2Planner().propose(request());
        assertFalse(outcome.accepted(), "a disabled planner never answers");
        assertTrue(outcome.reason().contains("switched off"), "the reason must be readable");
    }

    @Test void theAnswerOfTheServiceIsChecked() {
        KingdomPlayer2Planner planner = new KingdomPlayer2Planner();
        Request request = request();
        // A well formed answer of the chat completion.
        String answer = "{\"choices\":[{\"message\":{\"content\":\"{\\\"schema\\\":\\\"kingdom.intent.v1\\\","
                + "\\\"validUntil\\\":1000,\\\"intents\\\":[{\\\"kind\\\":\\\"BUY_ORDER\\\","
                + "\\\"target\\\":\\\"wheat\\\",\\\"amount\\\":10}]}\"}}]}";
        Proposal proposal = planner.parse(answer, request);
        assertNotNull(proposal, "the answer must be readable");
        assertEquals(1, proposal.intents().size());
        assertEquals(Kind.BUY_ORDER, proposal.intents().get(0).kind());
        assertEquals("wheat", proposal.intents().get(0).target());
        assertEquals(10, proposal.intents().get(0).amount());
    }

    @Test void aBrokenAnswerOfTheServiceIsRefused() {
        KingdomPlayer2Planner planner = new KingdomPlayer2Planner();
        Request request = request();
        assertNull(planner.parse("not json at all", request), "a broken answer is not usable");
        assertNull(planner.parse("{\"choices\":[]}", request), "an empty answer is not usable");
    }

    @Test void theValidationRefusesAForeignSchema() {
        KingdomLocalPlanner planner = new KingdomLocalPlanner();
        Request request = request();
        var outcome = planner.validate(new Proposal("other.schema.v9",
                List.of(new Intent(Kind.BUY_ORDER, "wheat", 1)), 1000),
                request, EnumSet.allOf(Kind.class));
        assertFalse(outcome.accepted(), "a foreign schema must be refused");
    }

    @Test void theValidationRefusesAnOutdatedAnswer() {
        KingdomLocalPlanner planner = new KingdomLocalPlanner();
        Request request = request();
        var outcome = planner.validate(new Proposal(KingdomPlanner.SCHEMA,
                List.of(new Intent(Kind.BUY_ORDER, "wheat", 1)), 10),
                request, EnumSet.allOf(Kind.class));
        assertFalse(outcome.accepted(), "an outdated answer must be refused");
    }

    @Test void theValidationRefusesAnIntentThatIsNotAllowed() {
        KingdomLocalPlanner planner = new KingdomLocalPlanner();
        Request request = request();
        var outcome = planner.validate(new Proposal(KingdomPlanner.SCHEMA,
                List.of(new Intent(Kind.BUILD_ORDER, "granary", 1)), 1000),
                request, EnumSet.of(Kind.BUY_ORDER));
        assertFalse(outcome.accepted(), "an intent that is not allowed must be refused");
    }

    @Test void theValidationRefusesTooManyIntents() {
        KingdomLocalPlanner planner = new KingdomLocalPlanner();
        Request request = request();
        List<Intent> intents = new java.util.ArrayList<>();
        for (int i = 0; i < KingdomPlanner.MAX_INTENTS + 1; i++)
            intents.add(new Intent(Kind.BUY_ORDER, "item" + i, 1));
        var outcome = planner.validate(new Proposal(KingdomPlanner.SCHEMA, intents, 1000),
                request, EnumSet.allOf(Kind.class));
        assertFalse(outcome.accepted(), "too many intents must be refused");
    }

    @Test void theValidationAcceptsAGoodAnswer() {
        KingdomLocalPlanner planner = new KingdomLocalPlanner();
        Request request = request();
        var outcome = planner.validate(new Proposal(KingdomPlanner.SCHEMA,
                List.of(new Intent(Kind.BUY_ORDER, "wheat", 10)), 1000),
                request, EnumSet.allOf(Kind.class));
        assertTrue(outcome.accepted(), "a good answer must pass");
        assertEquals("BUY_ORDER wheat x10", KingdomPlanner.describe(outcome.proposal()));
    }
}
