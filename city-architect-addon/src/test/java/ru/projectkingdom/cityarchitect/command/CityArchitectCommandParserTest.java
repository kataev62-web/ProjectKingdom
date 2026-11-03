package ru.projectkingdom.cityarchitect.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CityArchitectCommandParserTest {
    @Test
    void parsesEveryApprovedAddressedPhrase() {
        assertCommand("Помощник, проложи дорогу от ратуши до кузницы",
                CityArchitectIntent.ROAD, "от ратуши до кузницы");
        assertCommand("#помощник составь план города",
                CityArchitectIntent.CITY_PLAN, "");
        assertCommand("Помощник, покажи следующий этап",
                CityArchitectIntent.NEXT_STAGE, "");
        assertCommand("Помощник, пересчитай этап",
                CityArchitectIntent.REPLAN, "");
        assertCommand("Помощник, подтверди этап",
                CityArchitectIntent.CONFIRM, "");
        assertCommand("Помощник, отмени разметку",
                CityArchitectIntent.CANCEL_MARKERS, "");
        assertCommand("Помощник, статус плана города",
                CityArchitectIntent.STATUS, "");
    }

    @Test
    void acceptsCaseAndOuterWhitespaceWithoutChangingRoadArgument() {
        assertCommand("  ПОМОЩНИК,   Проложи дорогу   от Ратуши до Кузницы  ",
                CityArchitectIntent.ROAD, "от Ратуши до Кузницы");
    }

    @Test
    void rejectsOrdinaryChatIncompleteCommandsAndOversizedInput() {
        assertTrue(CityArchitectCommandParser.parse("Нужна дорога от ратуши до кузницы").isEmpty());
        assertTrue(CityArchitectCommandParser.parse("Помощник, проложи дорогу").isEmpty());
        assertTrue(CityArchitectCommandParser.parse("Помощник, составь красивый план города").isEmpty());
        assertTrue(CityArchitectCommandParser.parse("Помощник, " + "я".repeat(600)).isEmpty());
    }

    private static void assertCommand(String raw, CityArchitectIntent intent, String argument) {
        CityArchitectCommand command = CityArchitectCommandParser.parse(raw).orElseThrow();
        assertEquals(intent, command.intent());
        assertEquals(argument, command.argument());
    }
}
