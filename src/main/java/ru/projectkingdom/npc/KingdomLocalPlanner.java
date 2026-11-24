package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.List;

import ru.projectkingdom.npc.KingdomPlanner.Intent;
import ru.projectkingdom.npc.KingdomPlanner.Kind;
import ru.projectkingdom.npc.KingdomPlanner.Outcome;
import ru.projectkingdom.npc.KingdomPlanner.Proposal;
import ru.projectkingdom.npc.KingdomPlanner.Request;

/**
 * The local planner: the default, offline and without any dependency.
 *
 * <p>The local planner covers the shifts, the mine, the warehouse and the
 * wages without the JAR of the external planner and without a network
 * request. Its answer is deterministic: the same facts always lead to the same
 * intents.
 */
public final class KingdomLocalPlanner implements KingdomPlanner {
    public static final String NAME = "local";

    @Override public Outcome propose(Request request) {
        if (request == null) return Outcome.rejected("no request");
        List<Intent> intents = new ArrayList<>();
        for (String fact : request.facts()) {
            if (fact == null || fact.isBlank()) continue;
            String[] parts = fact.split(":");
            String kind = parts[0];
            String target = parts.length > 1 ? parts[1] : "";
            long amount = parts.length > 2 ? parse(parts[2]) : 1;
            if (amount <= 0) continue;
            switch (kind) {
                case "buy" -> intents.add(new Intent(Kind.BUY_ORDER, target, amount));
                case "route" -> intents.add(new Intent(Kind.ROUTE_ORDER, target, amount));
                case "build" -> intents.add(new Intent(Kind.BUILD_ORDER, target, amount));
                default -> {
                    // A fact that the local planner does not understand is
                    // ignored, never guessed.
                }
            }
            if (intents.size() >= MAX_INTENTS) break;
        }
        if (intents.isEmpty())
            return Outcome.rejected("the local planner has nothing to propose");
        return Outcome.accepted(new Proposal(SCHEMA, intents, request.validUntil()));
    }

    @Override public String name() { return NAME; }

    private static long parse(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return 1;
        }
    }
}
