package ru.projectkingdom.npc;

import java.util.Locale;

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

    public record Reply(Intent intent, Action action, String text) {}

    public static Reply reply(String utterance, Snapshot state) {
        if (state == null) throw new IllegalArgumentException("NPC state is required");
        Intent intent = classify(utterance);
        return switch (intent) {
            case GREETING -> answer(intent, "Здравствуй. Я " + safeName(state.name()) + ".");
            case IDENTITY -> answer(intent, "Меня зовут " + safeName(state.name()) + ". Моя профессия — "
                    + profession(state.job()) + ".");
            case NEEDS -> answer(intent, needs(state));
            case WORK -> answer(intent, work(state));
            case TASK -> answer(intent, task(state));
            case HELP -> state.hunger() < 80
                    ? new Reply(intent, Action.GIVE_BREAD, "Если это настоящий хлеб, передай его мне.")
                    : answer(intent, "Спасибо, но сейчас еда мне не нужна.");
            case TRADE -> canTrade(state.job())
                    ? new Reply(intent, Action.OPEN_TRADE, "Торговля идёт только у настоящего прилавка и склада.")
                    : answer(intent, "Я не веду торговлю. Обратись к купцу или кладовщику.");
            case HIRE -> new Reply(intent, Action.OPEN_MANAGEMENT,
                    state.employed() ? "У меня уже есть трудовой договор. Подробности — в управлении."
                            : "Найм оформляется через предприятие с настоящей кассой.");
            case FIRE -> new Reply(intent, Action.OPEN_MANAGEMENT,
                    "Расторжение договора подтверждается в управлении, а не словами.");
            case RELATIONSHIP -> answer(intent, "Наши отношения: " + relationship(state.reputation()) + ".");
            case MANAGEMENT -> new Reply(intent, Action.OPEN_MANAGEMENT,
                    "Открываю проверяемое управление договорами и приказами.");
            case UNKNOWN -> answer(intent,
                    "Я не понял. Спроси о моих нуждах, работе, задании, торговле или отношениях.");
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

    private static String needs(Snapshot s) {
        if (s.safety() < 25) return "Здесь опасно. Мне нужно безопасное место.";
        if (s.hunger() < 30) return "Я голоден. Нужен настоящий хлеб, не обещание.";
        if (s.energy() < 25) return "Я устал и должен отдохнуть дома.";
        if (!s.hasHome()) return "Мне негде жить. Нужен назначенный дом.";
        if (s.morale() < 30) return "Настроение плохое: проверь безопасность, питание и оплату.";
        return "Сейчас всё в порядке: я сыт, могу отдохнуть и чувствую себя безопасно.";
    }

    private static String work(Snapshot s) {
        if (!s.employed()) return "Сейчас я свободен и ищу настоящую работу.";
        if (s.contractPaused()) return "Мой договор приостановлен: накопилась проблема с оплатой.";
        if (!s.hasWorkstation()) return "Договор есть, но рабочая станция ещё не назначена.";
        return "Я работаю как " + profession(s.job()) + ". Текущий приказ: " + order(s.order()) + ".";
    }

    private static String task(Snapshot s) {
        if (s.safety() < 25) return "Сначала помоги сделать это место безопасным.";
        if (s.hunger() < 60) return "Принеси мне один настоящий хлеб и скажи: «Вот хлеб».";
        if (!s.hasHome()) return "Мне нужен дом. Назначить его может управляющий в меню NPC.";
        if (!s.employed()) return "Мне нужна работа в предприятии с реальной кассой.";
        if (s.contractPaused()) return "Помоги работодателю погасить долг по зарплате через кассу предприятия.";
        return "Сейчас срочных просьб нет. Спроси о работе или торговле.";
    }

    private static Reply answer(Intent intent, String text) {
        return new Reply(intent, Action.NONE, text);
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

