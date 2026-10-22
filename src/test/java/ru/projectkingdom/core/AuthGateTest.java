package ru.projectkingdom.core;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthGateTest {
    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

    @Test void verifiedTokenIsAcceptedOnlyOnce() {
        var account = new IdentityIds.AccountId("account-1");
        AuthGate gate = new AuthGate(token -> Optional.of(new AuthGate.VerifiedToken(account, NOW.plusSeconds(60))),
                Clock.fixed(NOW, ZoneOffset.UTC), false, false);
        assertTrue(gate.authenticate("one-time").allowed());
        assertEquals("replayed_token", gate.authenticate("one-time").reason());
    }

    @Test void expiredAndDisabledVerifierTokensAreRejected() {
        AuthGate expired = new AuthGate(token -> Optional.of(new AuthGate.VerifiedToken(
                new IdentityIds.AccountId("account-1"), NOW)), Clock.fixed(NOW, ZoneOffset.UTC), false, false);
        assertEquals("expired_token", expired.authenticate("old").reason());
        AuthGate disabled = new AuthGate(AuthGate.disabledVerifier(), Clock.fixed(NOW, ZoneOffset.UTC), false, false);
        assertEquals("invalid_token", disabled.authenticate("anything").reason());
    }

    @Test void developmentBypassRequiresBuildAndRuntimeFlags() {
        assertFalse(new AuthGate(AuthGate.disabledVerifier(), Clock.systemUTC(), true, false).developmentBypass("a").allowed());
        assertFalse(new AuthGate(AuthGate.disabledVerifier(), Clock.systemUTC(), false, true).developmentBypass("a").allowed());
        assertTrue(new AuthGate(AuthGate.disabledVerifier(), Clock.systemUTC(), true, true).developmentBypass("a").allowed());
    }
}
