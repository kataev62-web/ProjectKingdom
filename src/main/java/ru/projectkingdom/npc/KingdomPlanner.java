package ru.projectkingdom.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The contract of the planner of a complex task.
 *
 * <p>The planner may only propose. It never moves an item, never pays and
 * never writes to the world: the proposal is a small list of intents that the
 * server checks against the rights, the freshness and the schema before
 * anything happens.
 *
 * <p>Two implementations exist: the local planner, which works offline and
 * without any dependency, and the optional planner of the external service,
 * which is switched off by default. A bad or slow answer of the external
 * planner never changes the world: the local planner takes over.
 */
public interface KingdomPlanner {
    /** The version of the schema of a proposal. */
    String SCHEMA = "kingdom.intent.v1";
    /** The maximum number of intents of one proposal. */
    int MAX_INTENTS = 8;
    /** The maximum length of the text of one intent. */
    int MAX_TEXT = 96;

    /** The kinds of intent that the planner may propose. */
    enum Kind {
        /** The order of a purchase: buy the item of the enterprise. */
        BUY_ORDER,
        /** The order of the routes: the order of the assigned deliveries. */
        ROUTE_ORDER,
        /** The order of the infrastructure: the order of the construction. */
        BUILD_ORDER
    }

    /** One intent of a proposal. */
    record Intent(Kind kind, String target, long amount) {
        public Intent {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(target, "target");
            if (target.isBlank() || target.length() > MAX_TEXT)
                throw new IllegalArgumentException("the target of the intent is not usable");
            if (amount < 0) throw new IllegalArgumentException("the amount of the intent is negative");
        }
    }

    /** The request of a plan: who asks, what kind of task and what is known. */
    record Request(UUID resident, String taskKind, List<String> facts, long validUntil) {
        public Request {
            Objects.requireNonNull(resident, "resident");
            Objects.requireNonNull(taskKind, "taskKind");
            facts = facts == null ? List.of() : List.copyOf(facts);
            if (taskKind.isBlank() || taskKind.length() > MAX_TEXT)
                throw new IllegalArgumentException("the kind of the task is not usable");
            if (facts.size() > 16) throw new IllegalArgumentException("too many facts");
        }
    }

    /** The proposal of the planner. */
    record Proposal(String schema, List<Intent> intents, long validUntil) {
        public Proposal {
            intents = intents == null ? List.of() : List.copyOf(intents);
        }
    }

    /** The result of a planning attempt. */
    record Outcome(boolean accepted, Proposal proposal, String reason) {
        public static Outcome rejected(String reason) { return new Outcome(false, null, reason); }
        public static Outcome accepted(Proposal proposal) { return new Outcome(true, proposal, ""); }
    }

    /**
     * Checks a proposal of any planner.
     *
     * <p>The check is the same for the local and for the external planner: the
     * schema, the number of intents, the kind of the intents, the amount and
     * the freshness of the answer.
     *
     * @param allowed the kinds of intent that the caller accepts.
     */
    default Outcome validate(Proposal proposal, Request request, java.util.Set<Kind> allowed) {
        if (proposal == null) return Outcome.rejected("the planner answered nothing");
        if (!SCHEMA.equals(proposal.schema()))
            return Outcome.rejected("the schema of the answer is unknown: " + proposal.schema());
        if (proposal.intents().isEmpty()) return Outcome.rejected("the answer holds no intent");
        if (proposal.intents().size() > MAX_INTENTS)
            return Outcome.rejected("the answer holds too many intents");
        if (proposal.validUntil() < request.validUntil())
            return Outcome.rejected("the answer is outdated");
        for (Intent intent : proposal.intents()) {
            if (!allowed.contains(intent.kind()))
                return Outcome.rejected("the answer proposes an intent that is not allowed: " + intent.kind());
            if (intent.amount() > 2304)
                return Outcome.rejected("the amount of the intent is too large");
        }
        return Outcome.accepted(proposal);
    }

    /**
     * Plans a complex task.
     *
     * @return the outcome; a refusal never changes the world.
     */
    Outcome propose(Request request);

    /** The name of the planner for the operator. */
    String name();

    /** The intents of a proposal as a readable list. */
    static String describe(Proposal proposal) {
        if (proposal == null) return "нет предложения";
        List<String> parts = new ArrayList<>();
        for (Intent intent : proposal.intents())
            parts.add(intent.kind() + " " + intent.target() + " x" + intent.amount());
        return String.join("; ", parts);
    }
}
