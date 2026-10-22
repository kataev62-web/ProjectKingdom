package ru.projectkingdom.core;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IdentityRegistryTest {
    @Test void accountOwnsOneStableCharacterAcrossSessions() {
        IdentityRegistry registry = new IdentityRegistry();
        var account = new IdentityIds.AccountId("launcher-42");
        UUID firstSession = UUID.randomUUID();
        var first = registry.bind(account, firstSession);
        var repeat = registry.bind(account, firstSession);
        var rebound = registry.bind(account, UUID.randomUUID());

        assertEquals(IdentityRegistry.BindKind.CREATED, first.kind());
        assertEquals(IdentityRegistry.BindKind.RETURNING_SESSION, repeat.kind());
        assertEquals(IdentityRegistry.BindKind.REBOUND_SESSION, rebound.kind());
        assertEquals(first.identity().characterId(), rebound.identity().characterId());
        assertEquals(first.identity().dynastyId(), rebound.identity().dynastyId());
        assertEquals(1, registry.all().size());
    }

    @Test void worldDataRoundTripPreservesDomainIdentity() {
        IdentitySavedData before = new IdentitySavedData();
        var bound = before.bind("account:alpha", UUID.randomUUID()).identity();
        CompoundTag encoded = before.save(new CompoundTag());
        IdentitySavedData after = IdentitySavedData.load(encoded);
        var restored = after.registry().account(bound.accountId()).orElseThrow();
        assertEquals(bound, restored);
    }

    @Test void invalidAccountIdsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> new IdentityIds.AccountId(" "));
        assertThrows(NullPointerException.class, () -> new IdentityIds.AccountId(null));
    }
}
