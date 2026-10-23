package ru.projectkingdom.npc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class NpcDialogueMenuContractTest {
    @Test void menuStartsWithDialogueTopicsAndKeepsManagementPage() throws Exception {
        String source = Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/KingdomNpcMenu.java"));
        assertTrue(source.contains("enum Page { DIALOGUE, MANAGEMENT }"));
        assertTrue(source.contains("private Page page = Page.DIALOGUE"));
        assertTrue(source.contains("redrawDialogue"));
        assertTrue(source.contains("redrawManagement"));
        assertTrue(source.contains("Поприветствовать"));
        assertTrue(source.contains("Спросить о нуждах"));
        assertTrue(source.contains("Предложить помощь"));
        assertTrue(source.contains("Отношения"));
        assertTrue(source.contains("Управление NPC"));
        assertTrue(source.contains("NpcDialogueService.respond(viewer,w"));
        assertTrue(source.contains("page=Page.MANAGEMENT"));
        assertTrue(source.contains("page=Page.DIALOGUE"));
        assertTrue(source.contains("boolean management"));
        assertTrue(source.contains("management ? Page.MANAGEMENT : Page.DIALOGUE"));

        // Existing validated management actions must remain reachable.
        assertTrue(source.contains("НАНЯТЬ жителя"));
        assertTrue(source.contains("РАБОТАТЬ: реальные культуры"));
        assertTrue(source.contains("СКЛАД СЫРЬЯ"));
    }
}

