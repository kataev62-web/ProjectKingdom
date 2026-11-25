package ru.projectkingdom.npc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ru.projectkingdom.npc.KingdomPlanner.Intent;
import ru.projectkingdom.npc.KingdomPlanner.Kind;
import ru.projectkingdom.npc.KingdomPlanner.Outcome;
import ru.projectkingdom.npc.KingdomPlanner.Proposal;
import ru.projectkingdom.npc.KingdomPlanner.Request;

/**
 * The optional planner of the external service.
 *
 * <p>The planner is switched off by default and stays switched off until an
 * operator gives it a key. The call runs outside the game tick, the answer is
 * validated on the server and only the intents that pass the check may be
 * applied. A slow, refused or malformed answer never changes the world: the
 * local planner takes over.
 *
 * <p>The service never receives the inventory of a resident, the money or the
 * commands of the server. It receives the domain of the task and answers with
 * the intents that it proposes.
 */
public final class KingdomPlayer2Planner implements KingdomPlanner {
    public static final String NAME = "player2";
    /** The route of the service. */
    public static final String ENDPOINT = "https://api.player2.game/v1/chat/completions";
    /** The property that switches the planner on. */
    public static final String ENABLED_PROPERTY = "kingdom.planner.player2";
    /** The property that holds the key of the service. */
    public static final String KEY_PROPERTY = "kingdom.planner.player2.key";
    /** The time the service may take. */
    public static final Duration TIMEOUT = Duration.ofSeconds(10);

    /** Whether the operator switched the planner on. */
    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "false"));
    }

    /** Whether a key is present. */
    public static boolean configured() {
        String key = System.getProperty(KEY_PROPERTY, "");
        return key != null && !key.isBlank();
    }

    @Override public Outcome propose(Request request) {
        if (!enabled()) return Outcome.rejected("the external planner is switched off");
        if (!configured()) return Outcome.rejected("the external planner has no key");
        if (request == null) return Outcome.rejected("no request");
        try {
            String answer = call(request);
            Proposal proposal = parse(answer, request);
            if (proposal == null) return Outcome.rejected("the answer of the service is not usable");
            // The answer of the service is checked like any other answer. The
            // caller checks it again with the intents it accepts.
            return validate(proposal, request, java.util.EnumSet.allOf(Kind.class));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Outcome.rejected("the call of the service was interrupted");
        } catch (Exception ex) {
            // Every failure of the service is a refusal, never a change of the
            // world.
            return Outcome.rejected("the service is not available: " + ex.getClass().getSimpleName());
        }
    }

    @Override public String name() { return NAME; }

    /** The request of the service: the domain of the task, nothing else. */
    private String body(Request request) {
        StringBuilder facts = new StringBuilder();
        for (String fact : request.facts()) {
            if (fact == null || fact.isBlank()) continue;
            facts.append("- ").append(fact).append('\n');
        }
        String instruction = "You plan one complex task of a medieval town. "
                + "Answer with JSON only: {\"schema\":\"" + SCHEMA + "\",\"validUntil\":"
                + request.validUntil() + ",\"intents\":[{\"kind\":\"BUY_ORDER|ROUTE_ORDER|BUILD_ORDER\","
                + "\"target\":\"...\",\"amount\":1}]}. Task: " + request.taskKind() + "\n" + facts;
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", instruction);
        JsonArray messages = new JsonArray();
        messages.add(message);
        JsonObject body = new JsonObject();
        body.addProperty("model", "gpt-4o-mini");
        body.add("messages", messages);
        return body.toString();
    }

    /** The call of the service. It blocks, and therefore never runs in the game tick. */
    private String call(Request request) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        HttpRequest http = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("player2-game-key", System.getProperty(KEY_PROPERTY, ""))
                .header("Authorization", "Bearer " + System.getProperty(KEY_PROPERTY, ""))
                .POST(HttpRequest.BodyPublishers.ofString(body(request)))
                .build();
        HttpResponse<String> response = client.send(http, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2)
            throw new IllegalStateException("the service answered " + response.statusCode());
        return response.body();
    }

    /** Parses the answer of the service into a proposal. */
    Proposal parse(String answer, Request request) {
        if (answer == null || answer.isBlank()) return null;
        JsonElement root;
        try {
            root = JsonParser.parseString(answer);
        } catch (RuntimeException ex) {
            return null;
        }
        // The service answers with a chat completion: the content holds the JSON.
        String content = content(root);
        if (content == null) return null;
        JsonElement inner;
        try {
            inner = JsonParser.parseString(content);
        } catch (RuntimeException ex) {
            return null;
        }
        if (!inner.isJsonObject()) return null;
        JsonObject object = inner.getAsJsonObject();
        String schema = object.has("schema") ? object.get("schema").getAsString() : "";
        long validUntil = object.has("validUntil") ? object.get("validUntil").getAsLong() : 0;
        List<Intent> intents = new ArrayList<>();
        if (object.has("intents") && object.get("intents").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("intents")) {
                if (!element.isJsonObject()) continue;
                JsonObject intent = element.getAsJsonObject();
                Kind kind = kind(intent.has("kind") ? intent.get("kind").getAsString() : "");
                if (kind == null) continue;
                String target = intent.has("target") ? intent.get("target").getAsString() : "";
                if (target.isBlank() || target.length() > MAX_TEXT) continue;
                long amount = intent.has("amount") ? intent.get("amount").getAsLong() : 0;
                if (amount < 0) continue;
                intents.add(new Intent(kind, target, amount));
                if (intents.size() > MAX_INTENTS) break;
            }
        }
        return new Proposal(schema, intents, validUntil);
    }

    /** The content of the chat completion, or null. */
    private String content(JsonElement root) {
        if (!root.isJsonObject()) return null;
        JsonObject object = root.getAsJsonObject();
        if (object.has("choices") && object.get("choices").isJsonArray()) {
            JsonArray choices = object.getAsJsonArray("choices");
            if (choices.size() > 0 && choices.get(0).isJsonObject()) {
                JsonObject first = choices.get(0).getAsJsonObject();
                if (first.has("message") && first.get("message").isJsonObject()) {
                    JsonObject message = first.getAsJsonObject("message");
                    if (message.has("content")) return message.get("content").getAsString();
                }
            }
        }
        // A direct JSON answer is accepted as well.
        if (object.has("intents")) return root.toString();
        return null;
    }

    private static Kind kind(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "BUY_ORDER" -> Kind.BUY_ORDER;
            case "ROUTE_ORDER" -> Kind.ROUTE_ORDER;
            case "BUILD_ORDER" -> Kind.BUILD_ORDER;
            default -> null;
        };
    }
}
