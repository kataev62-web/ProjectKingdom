package ru.projectkingdom.cityarchitect.command;

import java.util.Objects;

public record CityArchitectCommand(CityArchitectIntent intent, String argument) {
    public CityArchitectCommand {
        Objects.requireNonNull(intent, "intent");
        argument = Objects.requireNonNull(argument, "argument").trim();
    }
}
