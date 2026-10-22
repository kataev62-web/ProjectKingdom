package ru.projectkingdom.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Server-side, one-time launcher token boundary. The real platform verifier is plugged in later. */
public final class AuthGate {
    public interface TokenVerifier {
        Optional<VerifiedToken> verify(String token);
    }
    public record VerifiedToken(IdentityIds.AccountId accountId, Instant expiresAt) {}
    public record Decision(boolean allowed, IdentityIds.AccountId accountId, String reason) {
        static Decision deny(String reason) { return new Decision(false, null, reason); }
        static Decision allow(IdentityIds.AccountId id) { return new Decision(true, id, "ok"); }
    }

    private final TokenVerifier verifier;
    private final Clock clock;
    private final boolean developmentBuild;
    private final boolean developmentRuntimeEnabled;
    private final Map<String, Instant> consumed = new HashMap<>();

    public AuthGate(TokenVerifier verifier, Clock clock, boolean developmentBuild, boolean developmentRuntimeEnabled) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.developmentBuild = developmentBuild;
        this.developmentRuntimeEnabled = developmentRuntimeEnabled;
    }

    public synchronized Decision authenticate(String token) {
        if (token == null || token.isBlank()) return Decision.deny("missing_token");
        Instant now = clock.instant();
        consumed.entrySet().removeIf(entry -> entry.getValue().isBefore(now.minus(Duration.ofMinutes(1))));
        if (consumed.containsKey(token)) return Decision.deny("replayed_token");
        Optional<VerifiedToken> verified = verifier.verify(token);
        if (verified.isEmpty()) return Decision.deny("invalid_token");
        if (!verified.get().expiresAt().isAfter(now)) return Decision.deny("expired_token");
        consumed.put(token, verified.get().expiresAt());
        return Decision.allow(verified.get().accountId());
    }

    public boolean developmentBypassAvailable() { return developmentBuild && developmentRuntimeEnabled; }

    public Decision developmentBypass(String accountId) {
        return developmentBypassAvailable()
                ? Decision.allow(new IdentityIds.AccountId("dev:" + accountId))
                : Decision.deny("development_bypass_disabled");
    }

    /** Production-safe placeholder: no token is accepted until the platform adapter is installed. */
    public static TokenVerifier disabledVerifier() { return token -> Optional.empty(); }
}
