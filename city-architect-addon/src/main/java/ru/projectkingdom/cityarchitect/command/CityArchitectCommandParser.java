package ru.projectkingdom.cityarchitect.command;

import java.util.Locale;
import java.util.Optional;

public final class CityArchitectCommandParser {
    public static final int MAX_RAW_LENGTH = 512;

    private static final String NAME_PREFIX = "помощник,";
    private static final String HASH_PREFIX = "#помощник";
    private static final String ROAD_PREFIX = "проложи дорогу ";

    private CityArchitectCommandParser() {
    }

    public static Optional<CityArchitectCommand> parse(String raw) {
        if (raw == null || raw.length() > MAX_RAW_LENGTH) {
            return Optional.empty();
        }
        String addressed = stripAddress(raw.trim());
        if (addressed == null || addressed.isBlank()) {
            return Optional.empty();
        }
        String command = normalizeSpaces(addressed);
        String lower = command.toLowerCase(Locale.ROOT);

        if (lower.startsWith(ROAD_PREFIX)) {
            String argument = command.substring(ROAD_PREFIX.length()).trim();
            if (validRoadArgument(argument)) {
                return Optional.of(new CityArchitectCommand(CityArchitectIntent.ROAD, argument));
            }
            return Optional.empty();
        }
        return switch (lower) {
            case "составь план города" -> command(CityArchitectIntent.CITY_PLAN);
            case "покажи следующий этап" -> command(CityArchitectIntent.NEXT_STAGE);
            case "пересчитай этап" -> command(CityArchitectIntent.REPLAN);
            case "подтверди этап" -> command(CityArchitectIntent.CONFIRM);
            case "отмени разметку" -> command(CityArchitectIntent.CANCEL_MARKERS);
            case "статус плана города" -> command(CityArchitectIntent.STATUS);
            default -> Optional.empty();
        };
    }

    public static boolean isAddressed(String raw) {
        if (raw == null || raw.length() > MAX_RAW_LENGTH) {
            return false;
        }
        return stripAddress(raw.trim()) != null;
    }

    private static Optional<CityArchitectCommand> command(CityArchitectIntent intent) {
        return Optional.of(new CityArchitectCommand(intent, ""));
    }

    private static String stripAddress(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith(NAME_PREFIX)) {
            return raw.substring(NAME_PREFIX.length()).trim();
        }
        if (lower.equals(HASH_PREFIX)) {
            return "";
        }
        if (lower.startsWith(HASH_PREFIX + " ")) {
            return raw.substring(HASH_PREFIX.length()).trim();
        }
        return null;
    }

    private static String normalizeSpaces(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static boolean validRoadArgument(String argument) {
        String lower = argument.toLowerCase(Locale.ROOT);
        int separator = lower.indexOf(" до ");
        return lower.startsWith("от ")
                && separator > "от ".length()
                && separator + " до ".length() < lower.length();
    }
}
