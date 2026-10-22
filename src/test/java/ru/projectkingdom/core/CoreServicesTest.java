package ru.projectkingdom.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CoreServicesTest {
    @Test void permissionsFlagsAuditAndEventsAreIndependentServices() {
        UUID character = UUID.randomUUID();
        CoreServices.Permissions permissions = new CoreServices.Permissions();
        permissions.grant(character, CoreServices.Permission.OPEN_BOOK);
        assertTrue(permissions.allows(character, CoreServices.Permission.OPEN_BOOK));
        assertFalse(permissions.allows(character, CoreServices.Permission.MANAGE_STATE));

        CoreServices.FeatureFlags flags = new CoreServices.FeatureFlags();
        flags.enable("book.bank");
        assertTrue(flags.enabled("book.bank"));

        CoreServices.AuditLog audit = new CoreServices.AuditLog();
        audit.record("account", "BOOK_OPEN", "bank");
        assertEquals(1, audit.snapshot().size());

        AtomicInteger calls = new AtomicInteger();
        CoreServices.Events<String> events = new CoreServices.Events<>();
        events.subscribe(value -> { throw new IllegalStateException("isolated"); });
        events.subscribe(value -> calls.incrementAndGet());
        assertEquals(1, events.publish("event"));
        assertEquals(1, calls.get());
    }

    @Test void paymentProviderIsAnExplicitDisabledStub() {
        var result = PaymentProvider.disabled().requestTopUp(new IdentityIds.AccountId("account"), 500, "RUB");
        assertFalse(result.accepted());
        assertEquals("provider_not_configured", result.reason());
    }
}
