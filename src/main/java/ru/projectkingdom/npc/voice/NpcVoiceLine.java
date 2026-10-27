package ru.projectkingdom.npc.voice;

import java.util.Locale;

public enum NpcVoiceLine {
    GREETING("Здравствуй."),
    IDENTITY("Расскажу о себе."),
    DANGER("Здесь опасно."),
    HUNGRY("Хочется есть."),
    TIRED("Пора отдохнуть."),
    HOME("Нужен дом."),
    UNHAPPY("Дела идут плохо."),
    WELL("Всё хорошо."),
    JOB_SEEKING("Ищу работу."),
    WAGES("Жду зарплату."),
    WORKSTATION("Нужно рабочее место."),
    WORKING("Работа идёт."),
    FOOD_HELP("Помоги мне с едой."),
    DEBT_HELP("Помоги вернуть долг."),
    NO_TASK("Пока просьб нет."),
    BREAD_THANKS("Спасибо за хлеб."),
    BREAD_MISSING("У тебя нет хлеба."),
    FOOD_ENOUGH("Спасибо, еды хватает."),
    TRADE("Покажу товары."),
    NO_TRADE("Обратись к торговцу."),
    EMPLOYED("Договор уже есть."),
    HIRE("Обсудим найм."),
    CONTRACT("Обсудим договор."),
    RELATIONSHIP("Давай поговорим."),
    MANAGEMENT("Открою управление."),
    UNKNOWN("Не понимаю. Повтори.");

    private final String text;

    private NpcVoiceLine(String text) {
        this.text = text;
    }

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public String text() {
        return this.text;
    }

    public static NpcVoiceLine fromId(String id) {
        for (NpcVoiceLine line : NpcVoiceLine.values()) {
            if (!line.id().equals(id)) continue;
            return line;
        }
        throw new IllegalArgumentException("Unknown recorded NPC line");
    }
}

