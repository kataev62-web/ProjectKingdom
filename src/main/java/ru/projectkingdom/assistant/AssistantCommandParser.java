package ru.projectkingdom.assistant;

import java.util.Locale;
import java.util.Optional;

/** Strict Russian chat parser. It never produces arbitrary server commands. */
public final class AssistantCommandParser {
    public static final int MAX_RAW_LENGTH = 512;
    private static final String NAME_PREFIX = "помощник,";
    private static final String HASH_PREFIX = "#помощник ";

    private AssistantCommandParser() {}

    public static Optional<AssistantCommand> parse(String raw) {
        if (raw == null || raw.length() > MAX_RAW_LENGTH) return Optional.empty();
        String command = addressedBody(raw);
        if (command == null || command.isBlank()) return Optional.empty();
        String normalized = command.toLowerCase(Locale.ROOT).trim();
        try {
            if (normalized.equals("статус")) return command(AssistantIntent.STATUS);
            if (normalized.equals("следуй за мной") || normalized.equals("иди за мной"))
                return command(AssistantIntent.FOLLOW);
            if (normalized.equals("стой здесь") || normalized.equals("останься здесь"))
                return command(AssistantIntent.STAY);
            if (normalized.equals("осмотри территорию") || normalized.equals("обследуй территорию"))
                return command(AssistantIntent.SURVEY);
            if (normalized.equals("аудит поселения") || normalized.equals("проверь поселение"))
                return command(AssistantIntent.SETTLEMENT_AUDIT);
            if (normalized.equals("строй") || normalized.equals("строить")
                    || normalized.equals("построй") || normalized.equals("начать стройку")
                    || normalized.equals("начать строительство"))
                return command(AssistantIntent.BUILD);
            if (normalized.equals("отмена последней стройки") || normalized.equals("откати"))
                return command(AssistantIntent.UNDO);
            if (normalized.equals("отмена") || normalized.equals("отмени"))
                return command(AssistantIntent.CANCEL);
            if (normalized.equals("диагностика") || normalized.equals("проверь системы"))
                return command(AssistantIntent.DIAGNOSTICS);
            if (normalized.equals("аудит модов") || normalized.equals("проверь моды"))
                return command(AssistantIntent.MOD_AUDIT);
            Optional<AssistantCommand> preview = argumentCommand(
                    command, normalized, "предпросмотр ", AssistantIntent.PREVIEW);
            if (preview.isPresent()) return preview;
            preview = argumentCommand(
                    command, normalized, "покажи схематику ", AssistantIntent.PREVIEW);
            if (preview.isPresent()) return preview;
            for (String prefix : new String[]{"построй ", "строй ", "строить "}) {
                preview = argumentCommand(command, normalized, prefix, AssistantIntent.PREVIEW);
                if (preview.isPresent()) return preview;
            }
            return Optional.empty();
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static boolean isAddressed(String raw) {
        return addressedBody(raw) != null;
    }

    private static String addressedBody(String raw) {
        if (raw == null) return null;
        String stripped = raw.strip();
        String lower = stripped.toLowerCase(Locale.ROOT);
        if (lower.startsWith(NAME_PREFIX)) return stripped.substring(NAME_PREFIX.length()).trim();
        if (lower.startsWith(HASH_PREFIX)) return stripped.substring(HASH_PREFIX.length()).trim();
        return null;
    }

    private static Optional<AssistantCommand> command(AssistantIntent intent) {
        return Optional.of(new AssistantCommand(intent, ""));
    }

    private static Optional<AssistantCommand> argumentCommand(
            String original, String normalized, String prefix, AssistantIntent intent) {
        if (!normalized.startsWith(prefix)) return Optional.empty();
        String argument = original.substring(prefix.length()).trim();
        if (argument.isEmpty()) return Optional.empty();
        return Optional.of(new AssistantCommand(intent, argument));
    }
}
