package ru.projectkingdom.assistant;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class KingdomAssistantOwnershipTest {
    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID FORGED_OWNER = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test void ownerUuidRoundTrip() {
        var before = new KingdomAssistantOwnership();
        assertTrue(before.assign(OWNER));
        before.setMode(KingdomAssistantData.Mode.STAY);

        var restored = KingdomAssistantOwnership.load(before.save(new CompoundTag()));

        assertEquals(OWNER, restored.ownerId().orElseThrow());
        assertEquals(KingdomAssistantData.Mode.STAY, restored.mode());
    }

    @Test void ownedEntityCannotBeReassigned() {
        var ownership = new KingdomAssistantOwnership();
        assertTrue(ownership.assign(OWNER));
        assertFalse(ownership.assign(FORGED_OWNER));
        assertEquals(OWNER, ownership.ownerId().orElseThrow());
    }

    @Test void permissionBelowFourCannotControlAssistant() {
        var ownership = new KingdomAssistantOwnership();
        ownership.assign(OWNER);

        assertFalse(ownership.mayControl(OWNER, 3));
        assertTrue(ownership.mayControl(OWNER, 4));
    }

    @Test void forgedSecondOwnerCannotControlAssistant() {
        var ownership = new KingdomAssistantOwnership();
        ownership.assign(OWNER);

        assertFalse(ownership.mayControl(FORGED_OWNER, 4));
        assertFalse(ownership.assign(FORGED_OWNER));
    }
}
