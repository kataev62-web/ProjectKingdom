package ru.projectkingdom.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Converts untrusted model DTOs into a small allow-listed advisory plan. */
public final class AssistantPlanValidator {
    private static final Pattern WINDOWS_PATH = Pattern.compile("(?i)(^|\\s)[a-z]:\\\\");
    private static final Pattern CONSOLE_COMMAND = Pattern.compile(
            "(?i)(^|[\\r\\n;])\\s*/?(execute|op|deop|give|kill|summon|setblock|fill|tp|teleport|gamemode|difficulty|stop|reload|ban)\\b");

    public record PlanningLimits(UUID expectedRequestId, int maxActions, int maxTextChars) {
        public PlanningLimits {
            expectedRequestId = Objects.requireNonNull(expectedRequestId, "expectedRequestId");
            if (maxActions < 1 || maxActions > 20) throw new IllegalArgumentException("maxActions");
            if (maxTextChars < 1 || maxTextChars > 64_000)
                throw new IllegalArgumentException("maxTextChars");
        }
    }

    public record ValidationResult(
            boolean accepted,
            String reason,
            List<AssistantPlanner.PlannedAction> actions,
            String text
    ) {
        public ValidationResult {
            reason = Objects.requireNonNull(reason, "reason");
            actions = List.copyOf(actions);
            text = Objects.requireNonNull(text, "text");
        }
    }

    public ValidationResult validate(
            AssistantModelClient.ModelResponse response, PlanningLimits limits) {
        Objects.requireNonNull(limits, "limits");
        if (response == null) return rejected("missing response");
        if (!limits.expectedRequestId().equals(response.requestId()))
            return rejected("request id mismatch");
        if (response.actions() == null || response.text() == null)
            return rejected("malformed response");
        if (response.actions().size() > limits.maxActions())
            return rejected("too many actions");

        String text = response.text().trim();
        if (text.isEmpty()) return rejected("blank text");
        long characters = text.length();
        List<AssistantPlanner.PlannedAction> safe = new ArrayList<>();
        for (AssistantModelClient.ModelAction proposed : response.actions()) {
            if (proposed == null || proposed.type() == null || proposed.argument() == null
                    || proposed.rawBlocks() == null)
                return rejected("malformed action");
            if (!proposed.rawBlocks().isEmpty()) return rejected("raw block list forbidden");

            AssistantPlanner.ActionType type;
            try {
                type = AssistantPlanner.ActionType.valueOf(
                        proposed.type().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return rejected("unknown or forbidden action");
            }
            String argument = proposed.argument().trim();
            if (argument.isEmpty() || unsafeArgument(argument))
                return rejected("command or path forbidden");
            characters += argument.length();
            if (characters > limits.maxTextChars()) return rejected("text limit exceeded");
            try {
                safe.add(new AssistantPlanner.PlannedAction(type, argument));
            } catch (RuntimeException ignored) {
                return rejected("invalid action");
            }
        }
        if (characters > limits.maxTextChars()) return rejected("text limit exceeded");
        return new ValidationResult(true, "accepted", safe, text);
    }

    private static boolean unsafeArgument(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return value.startsWith("/")
                || lower.contains("../")
                || lower.contains("..\\")
                || lower.startsWith("file:")
                || lower.startsWith("/tmp/")
                || lower.startsWith("/home/")
                || lower.startsWith("/var/")
                || value.indexOf('\\') >= 0
                || WINDOWS_PATH.matcher(value).find()
                || CONSOLE_COMMAND.matcher(value).find();
    }

    private static ValidationResult rejected(String reason) {
        return new ValidationResult(false, reason, List.of(), "");
    }
}
