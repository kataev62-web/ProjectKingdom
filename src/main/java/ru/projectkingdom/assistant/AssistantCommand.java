package ru.projectkingdom.assistant;

import java.util.Objects;

/** A parsed command after free text has been reduced to a safe server intent. */
public record AssistantCommand(AssistantIntent intent, String argument) {
    public static final int MAX_ARGUMENT_LENGTH = 256;

    public AssistantCommand {
        intent = Objects.requireNonNull(intent, "intent");
        argument = argument == null ? "" : argument.trim();
        if (argument.length() > MAX_ARGUMENT_LENGTH) {
            throw new IllegalArgumentException("Assistant command argument is longer than 256 characters");
        }
    }
}
