package ru.projectkingdom.cityarchitect;

import org.junit.jupiter.api.Test;
import ru.projectkingdom.assistant.AssistantCommandParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CityArchitectChatRoutingTest {
    @Test
    void ordinaryAssistantCommandsPassThroughToTheBaseMod() {
        assertTrue(AssistantCommandParser.parse("Помощник, статус").isPresent());
        assertEquals(CityArchitectChatRouting.Route.PASS_THROUGH,
                CityArchitectChatRouting.route("Помощник, статус"));
        assertEquals(CityArchitectChatRouting.Route.PASS_THROUGH,
                CityArchitectChatRouting.route("Помощник, построй дом"));
    }

    @Test
    void cityArchitectCommandsAreHandledExactlyOnceByTheAddon() {
        assertEquals(CityArchitectChatRouting.Route.CITY_ARCHITECT,
                CityArchitectChatRouting.route("Помощник, составь план города"));
        assertEquals(CityArchitectChatRouting.Route.CITY_ARCHITECT,
                CityArchitectChatRouting.route("Помощник, проложи дорогу от ратуши до кузницы"));
    }
}
