package ru.projectkingdom.assistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class AssistantCommandParserTest {
    @Test void parsesRussianOwnerCommands() {
        assertCommand("Помощник, статус", AssistantIntent.STATUS, "");
        assertCommand("пОмОщНиК, следуй за мной", AssistantIntent.FOLLOW, "");
        assertCommand("#помощник стой здесь", AssistantIntent.STAY, "");
        assertCommand("Помощник, осмотри территорию", AssistantIntent.SURVEY, "");
        assertCommand("Помощник, аудит поселения", AssistantIntent.SETTLEMENT_AUDIT, "");
        assertCommand("Помощник, предпросмотр avalon_smithy", AssistantIntent.PREVIEW, "avalon_smithy");
        assertCommand("Помощник, строй", AssistantIntent.BUILD, "");
        assertCommand("Помощник, отмени", AssistantIntent.CANCEL, "");
        assertCommand("Помощник, откати", AssistantIntent.UNDO, "");
        assertCommand("Помощник, диагностика", AssistantIntent.DIAGNOSTICS, "");
        assertCommand("Помощник, аудит модов", AssistantIntent.MOD_AUDIT, "");
    }

    @Test void ignoresOrdinaryAndNpcAddressedChat() {
        assertTrue(AssistantCommandParser.parse("Кто построил эту дорогу?").isEmpty());
        assertTrue(AssistantCommandParser.parse("Кузнец, открой торговлю").isEmpty());
        assertTrue(AssistantCommandParser.parse("#12 покажи работу").isEmpty());
    }

    @Test void rejectsOversizedRawTextAndArgument() {
        assertTrue(AssistantCommandParser.parse("Помощник, " + "я".repeat(502)).isEmpty());
        assertTrue(AssistantCommandParser.parse("Помощник, предпросмотр " + "a".repeat(257)).isEmpty());
    }

    private static void assertCommand(String raw, AssistantIntent intent, String argument) {
        AssistantCommand parsed = AssistantCommandParser.parse(raw).orElseThrow();
        assertEquals(intent, parsed.intent());
        assertEquals(argument, parsed.argument());
    }
}
