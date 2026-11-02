package ru.projectkingdom.assistant;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class KingdomAssistantAuthorizationTest {
    @Test void onlySavedPermissionFourOwnerCanConsumeCommands() {
        UUID owner = UUID.fromString("30000000-0000-0000-0000-000000000003");
        UUID stranger = UUID.fromString("40000000-0000-0000-0000-000000000004");
        var data = new KingdomAssistantData();
        data.bind(owner, UUID.fromString("50000000-0000-0000-0000-000000000005"),
                "minecraft:overworld");
        var before = data.ownedBy(owner).orElseThrow();

        assertEquals(KingdomAssistantChat.Authorization.AUTHORIZED,
                KingdomAssistantChat.authorize(data, owner, 4, "Помощник, статус"));
        assertEquals(KingdomAssistantChat.Authorization.NOT_ADMIN,
                KingdomAssistantChat.authorize(data, owner, 3, "Помощник, строй"));
        assertEquals(KingdomAssistantChat.Authorization.NOT_OWNER,
                KingdomAssistantChat.authorize(data, stranger, 4, "Помощник, строй"));
        assertEquals(KingdomAssistantChat.Authorization.NOT_ADDRESSED,
                KingdomAssistantChat.authorize(data, stranger, 4, "Обычный разговор"));

        assertEquals(before, data.ownedBy(owner).orElseThrow());
        assertNull(before.activeTaskId());
    }
}
