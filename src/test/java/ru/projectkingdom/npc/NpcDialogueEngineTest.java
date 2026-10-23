package ru.projectkingdom.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class NpcDialogueEngineTest {
    private static NpcDialogueEngine.Snapshot state(int hunger, int energy, int safety,
                                                     boolean employed, boolean paused,
                                                     boolean home, boolean station, String job) {
        return new NpcDialogueEngine.Snapshot("Анна", job, hunger, energy, 70, safety,
                employed, paused, home, station, "work", 15);
    }

    @Test void recognizesRussianIntentsAndProtectsManagementActions() {
        var s = state(80, 80, 80, true, false, true, true, "farmer");
        assertEquals(NpcDialogueEngine.Intent.GREETING, NpcDialogueEngine.reply("Привет", s).intent());
        assertEquals(NpcDialogueEngine.Intent.IDENTITY, NpcDialogueEngine.reply("Кто ты?", s).intent());
        assertEquals(NpcDialogueEngine.Intent.NEEDS, NpcDialogueEngine.reply("Как дела?", s).intent());
        assertEquals(NpcDialogueEngine.Intent.WORK, NpcDialogueEngine.reply("Чем занимаешься?", s).intent());
        assertEquals(NpcDialogueEngine.Intent.TASK, NpcDialogueEngine.reply("Чем тебе помочь?", s).intent());
        assertEquals(NpcDialogueEngine.Intent.TRADE, NpcDialogueEngine.reply("Хочу торговать", s).intent());
        assertEquals(NpcDialogueEngine.Action.OPEN_MANAGEMENT,
                NpcDialogueEngine.reply("Хочу тебя нанять", s).action());
        assertEquals(NpcDialogueEngine.Action.OPEN_MANAGEMENT,
                NpcDialogueEngine.reply("Открой управление", s).action());
    }

    @Test void repliesFromRealNeedsAndContractState() {
        assertTrue(NpcDialogueEngine.reply("Как дела?",
                state(20, 80, 80, false, false, false, false, "free")).text().contains("голод"));
        assertTrue(NpcDialogueEngine.reply("Как дела?",
                state(80, 20, 80, false, false, true, false, "free")).text().contains("отдох"));
        assertTrue(NpcDialogueEngine.reply("Как дела?",
                state(80, 80, 20, false, false, true, false, "free")).text().contains("опас"));
        assertTrue(NpcDialogueEngine.reply("Как работа?",
                state(80, 80, 80, true, true, true, true, "smith")).text().contains("приостанов"));
        assertTrue(NpcDialogueEngine.reply("Как работа?",
                state(80, 80, 80, true, false, true, false, "smith")).text().contains("станц"));
    }

    @Test void offersOnlyPhysicalBreadHelpAndSafeFallback() {
        var hungry = state(25, 80, 80, false, false, true, false, "free");
        assertEquals(NpcDialogueEngine.Action.GIVE_BREAD,
                NpcDialogueEngine.reply("Вот хлеб", hungry).action());
        assertEquals(NpcDialogueEngine.Action.NONE,
                NpcDialogueEngine.reply("Вот хлеб", state(95, 80, 80, false, false, true, false, "free")).action());
        var unknown = NpcDialogueEngine.reply("...", hungry);
        assertEquals(NpcDialogueEngine.Intent.UNKNOWN, unknown.intent());
        assertFalse(unknown.text().isBlank());
    }

    @Test void tradeIsRoleLimited() {
        var merchant = state(80, 80, 80, true, false, true, true, "merchant");
        var farmer = state(80, 80, 80, true, false, true, true, "farmer");
        assertEquals(NpcDialogueEngine.Action.OPEN_TRADE,
                NpcDialogueEngine.reply("Покажи товары", merchant).action());
        assertEquals(NpcDialogueEngine.Action.NONE,
                NpcDialogueEngine.reply("Покажи товары", farmer).action());
    }

    @Test void everyRegisteredProfessionUsesCanonicalTitle() {
        for (var profession : KingdomProfessionRegistry.all()) {
            var reply = NpcDialogueEngine.reply("Кто ты?",
                    state(80, 80, 80, true, false, true, true, profession.id()));
            assertTrue(reply.text().contains(profession.title()));
        }
    }
}

