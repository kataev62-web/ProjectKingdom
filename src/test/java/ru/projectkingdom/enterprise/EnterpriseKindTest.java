package ru.projectkingdom.enterprise;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import ru.projectkingdom.npc.KingdomEnterpriseData;

final class EnterpriseKindTest {
    @Test void catalogHasTwelveUniquePrivateTypes() {
        var placeable = EnterpriseKind.placeableKinds();
        assertEquals(12, placeable.size());
        Set<String> ids = placeable.stream().map(EnterpriseKind::id).collect(Collectors.toSet());
        assertEquals(12, ids.size());
        assertTrue(ids.contains("hotel"));
        assertTrue(ids.contains("tavern"));
        assertFalse(ids.contains("warehouse"));
        assertFalse(ids.contains("market"));
        assertFalse(EnterpriseKind.LEGACY.placeable());
    }

    @Test void lifecycleIsOwnerOnlyAndOneActiveCorePerUuid() {
        KingdomEnterpriseData data = new KingdomEnterpriseData();
        UUID owner = UUID.randomUUID();
        var firm = data.create(owner, "Кузница", EnterpriseKind.SMITHY);
        assertNotNull(firm);
        assertEquals(EnterpriseActivation.FOREIGN_OWNER,
                data.activateCore(UUID.randomUUID(), firm.id(), EnterpriseKind.SMITHY, "overworld", 10));
        assertEquals(EnterpriseActivation.ACTIVATED,
                data.activateCore(owner, firm.id(), EnterpriseKind.SMITHY, "overworld", 10));
        assertEquals(EnterpriseActivation.ALREADY_ACTIVE,
                data.activateCore(owner, firm.id(), EnterpriseKind.SMITHY, "overworld", 11));
        assertFalse(data.suspendCore(owner, firm.id(), "overworld", 11));
        assertTrue(data.suspendCore(owner, firm.id(), "overworld", 10));
        assertEquals(EnterpriseActivation.ACTIVATED,
                data.activateCore(owner, firm.id(), EnterpriseKind.SMITHY, "overworld", 11));
        assertEquals(12, data.core(firm.id()).packedPos());
        assertTrue(data.core(firm.id()).active());
    }

    @Test void legacyAdoptionPreservesIdentityAndName() {
        KingdomEnterpriseData data = new KingdomEnterpriseData();
        UUID owner = UUID.randomUUID();
        var old = data.create(owner, "Старая мастерская");
        assertEquals(EnterpriseKind.LEGACY, data.kind(old.id()));
        assertTrue(data.adoptLegacy(owner, old.id(), EnterpriseKind.FARM));
        assertEquals(old.id(), data.find(old.id()).id());
        assertEquals("Старая мастерская", data.find(old.id()).name());
        assertEquals(EnterpriseKind.FARM, data.kind(old.id()));
        assertFalse(data.adoptLegacy(owner, old.id(), EnterpriseKind.MINE));
    }
}
