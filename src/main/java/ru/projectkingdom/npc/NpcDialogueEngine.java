package ru.projectkingdom.npc;

import java.util.Locale;
import ru.projectkingdom.npc.voice.NpcVoiceLine;

/** Deterministic role- and state-aware Russian dialogue policy. It never mutates game state. */
public final class NpcDialogueEngine {
    private NpcDialogueEngine() {}

    public enum Intent {
        GREETING, IDENTITY, NEEDS, WORK, TASK, HELP, TRADE,
        HIRE, FIRE, RELATIONSHIP, MANAGEMENT, UNKNOWN
    }

    public enum Action { NONE, OPEN_MANAGEMENT, OPEN_TRADE, GIVE_BREAD }

    public record Snapshot(String name, String job, int hunger, int energy, int morale, int safety,
                           boolean employed, boolean contractPaused, boolean hasHome,
                           boolean hasWorkstation, String order, int reputation) {}

    public record Reply(Intent intent, Action action, String text, NpcVoiceLine line) {}

    public static Reply reply(String utterance, Snapshot state) {
        if (state == null) throw new IllegalArgumentException("NPC state is required");
        Intent intent = classify(utterance);
        return switch (intent) {
            case GREETING -> answer(intent, "Здравствуй. Я " + safeName(state.name()) + ".", NpcVoiceLine.GREETING);
            case IDENTITY -> answer(intent, "Меня зовут " + safeName(state.name()) + ". Моя профессия — "
                    + profession(state.job()) + ".", NpcVoiceLine.IDENTITY);
            case NEEDS -> needs(state);
            case WORK -> work(state);
            case TASK -> task(state);
            case HELP -> state.hunger() < 80
                    ? new Reply(intent, Action.GIVE_BREAD, "Если это настоящий хлеб, передай его мне.", NpcVoiceLine.FOOD_HELP)
                    : answer(intent, "Спасибо, но сейчас еда мне не нужна.", NpcVoiceLine.FOOD_ENOUGH);
            case TRADE -> canTrade(state.job())
                    ? new Reply(intent, Action.OPEN_TRADE, "Торговля идёт только у настоящего прилавка и склада.", NpcVoiceLine.TRADE)
                    : answer(intent, "Я не веду торговлю. Обратись к купцу или кладовщику.", NpcVoiceLine.NO_TRADE);
            case HIRE -> new Reply(intent, Action.OPEN_MANAGEMENT,
                    state.employed() ? "У меня уже есть трудовой договор. Подробности — в управлении."
                            : "Найм оформляется через предприятие с настоящей кассой.", state.employed() ? NpcVoiceLine.EMPLOYED : NpcVoiceLine.HIRE);
            case FIRE -> new Reply(intent, Action.OPEN_MANAGEMENT,
                    "Расторжение договора подтверждается в управлении, а не словами.", NpcVoiceLine.CONTRACT);
            case RELATIONSHIP -> answer(intent, "Наши отношения: " + relationship(state.reputation()) + ".", NpcVoiceLine.RELATIONSHIP);
            case MANAGEMENT -> new Reply(intent, Action.OPEN_MANAGEMENT,
                    "Открываю проверяемое управление договорами и приказами.", NpcVoiceLine.MANAGEMENT);
            case UNKNOWN -> answer(intent,
                    "Я не понял. Спроси о моих нуждах, работе, задании, торговле или отношениях.", NpcVoiceLine.UNKNOWN);
        };
    }

    public static Intent classify(String utterance) {
        String text = normalize(utterance);
        if (text.isEmpty()) return Intent.UNKNOWN;
        if (has(text, "вот хлеб", "держи хлеб", "принес хлеб", "принесла хлеб", "передаю хлеб")) return Intent.HELP;
        if (has(text, "управлен", "открой меню", "приказ", "договор")) return Intent.MANAGEMENT;
        if (has(text, "увол", "расторг")) return Intent.FIRE;
        if (has(text, "нанять", "найми", "на работу")) return Intent.HIRE;
        if (has(text, "торг", "товар", "купить", "продать", "цена")) return Intent.TRADE;
        if (has(text, "чем помочь", "чем тебе помочь", "помочь тебе", "задани", "работа для меня", "просьб")) return Intent.TASK;
        if (has(text, "как дела", "что нужно", "твои нужд", "голод", "устал", "самочувств")) return Intent.NEEDS;
        if (has(text, "чем занима", "как работа", "работаешь", "професси")) return Intent.WORK;
        if (has(text, "отношен", "довер", "репутац", "что думаешь обо мне")) return Intent.RELATIONSHIP;
        if (has(text, "кто ты", "расскажи о себе", "твое имя", "тебя зовут")) return Intent.IDENTITY;
        if (has(text, "привет", "здравств", "добрый день", "добрый вечер", "доброе утро")) return Intent.GREETING;
        return Intent.UNKNOWN;
    }

    private static Reply needs(Snapshot s) {
        if (s.safety() < 25) return answer(Intent.NEEDS,"Здесь опасно. Мне нужно безопасное место.",NpcVoiceLine.DANGER);
        if (s.hunger() < 30) return answer(Intent.NEEDS,"Испытываю голод. Нужен настоящий хлеб, не обещание.",NpcVoiceLine.HUNGRY);
        if (s.energy() < 25) return answer(Intent.NEEDS,"Пора отдохнуть дома.",NpcVoiceLine.TIRED);
        if (!s.hasHome()) return answer(Intent.NEEDS,"Мне негде жить. Нужен назначенный дом.",NpcVoiceLine.HOME);
        if (s.morale() < 30) return answer(Intent.NEEDS,"Настроение плохое: проверь безопасность, питание и оплату.",NpcVoiceLine.UNHAPPY);
        return answer(Intent.NEEDS,"Сейчас всё в порядке: еды хватает, есть отдых и безопасность.",NpcVoiceLine.WELL);
    }

    private static Reply work(Snapshot s) {
        if (!s.employed()) return answer(Intent.WORK,"Сейчас ищу настоящую работу.",NpcVoiceLine.JOB_SEEKING);
        if (s.contractPaused()) return answer(Intent.WORK,"Мой договор приостановлен: накопилась проблема с оплатой.",NpcVoiceLine.WAGES);
        if (!s.hasWorkstation()) return answer(Intent.WORK,"Договор есть, но рабочая станция ещё не назначена.",NpcVoiceLine.WORKSTATION);
        return answer(Intent.WORK,"Я работаю как " + profession(s.job()) + ". Текущий приказ: " + order(s.order()) + ".",NpcVoiceLine.WORKING);
    }

    private static Reply task(Snapshot s) {
        if (s.safety() < 25) return answer(Intent.TASK,"Сначала помоги сделать это место безопасным.",NpcVoiceLine.DANGER);
        if (s.hunger() < 60) return answer(Intent.TASK,"Принеси мне один настоящий хлеб и скажи: «Вот хлеб».",NpcVoiceLine.FOOD_HELP);
        if (!s.hasHome()) return answer(Intent.TASK,"Мне нужен дом. Назначить его может управляющий в меню NPC.",NpcVoiceLine.HOME);
        if (!s.employed()) return answer(Intent.TASK,"Мне нужна работа в предприятии с реальной кассой.",NpcVoiceLine.JOB_SEEKING);
        if (s.contractPaused()) return answer(Intent.TASK,"Помоги работодателю погасить долг по зарплате через кассу предприятия.",NpcVoiceLine.DEBT_HELP);
        return answer(Intent.TASK,"Сейчас срочных просьб нет. Спроси о работе или торговле.",NpcVoiceLine.NO_TASK);
    }

    private static Reply answer(Intent intent, String text, NpcVoiceLine line) {
        return new Reply(intent, Action.NONE, text, line);
    }

    private static boolean canTrade(String job) {
        return "merchant".equals(job) || "warehouse".equals(job);
    }

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "житель Королевства" : name;
    }

    private static String profession(String job) {
        return KingdomProfessionRegistry.title(job);
    }

    private static String order(String value) {
        if (value == null) return "свободная жизнь";
        return switch (value) {
            case "follow" -> "следовать за работодателем";
            case "stay" -> "оставаться на месте";
            case "work" -> "работать";
            case "home" -> "идти домой";
            case "haul" -> "перевозить грузы";
            default -> "свободная жизнь";
        };
    }

    private static String relationship(int reputation) {
        return new NpcSocialMemory(reputation, 0, 0, "NONE", 0).label();
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replace('ё', 'е').trim();
    }

    private static boolean has(String text, String... fragments) {
        for (String fragment : fragments) if (text.contains(fragment)) return true;
        return false;
    }
}

